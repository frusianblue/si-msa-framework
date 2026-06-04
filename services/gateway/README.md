# gateway (엣지 게이트웨이 · API Gateway)

WebFlux 기반 Spring Cloud Gateway. 라우팅·레이트리밋·엣지 인증(JWT 1차 검증 + 신뢰 헤더 주입)·이중 발급기(자체 JWT + AS RS256/JWKS) 수용을 담당하는 엣지다.

- **포트**: `8000`
- **스택**: Spring Boot 4.0.6 / Java 21 / Spring Cloud Gateway(WebFlux) / Reactive Redis / Resilience4j / jjwt
- **DB 없음 · Flyway 없음** (상태는 Redis 에만)

---

## 1. 빌드 (Build)

```bash
./gradlew :services:gateway:build       # 컴파일 + 테스트 + assemble
./gradlew :services:gateway:bootJar      # 실행 가능 jar (CI→Dockerfile JAR_FILE)
./gradlew :services:gateway:compileJava  # 컴파일만
```
> 커밋 전 루트 `./gradlew spotlessApply`.

---

## 2. 테스트 (Test)

```bash
./gradlew :services:gateway:test                              # 전체
./gradlew :services:gateway:test --tests "*GatewayDualIssuerTest"   # 이중 발급기(자체 JWT + AS RS256/JWKS)
./gradlew :services:gateway:test --tests "*GatewayAuthGlobalFilterTest" --tests "*GatewayTokenVerifierTest"
```
주요 테스트:
- `auth/GatewayDualIssuerTest` — 실 RSA 키 + JWKS 로 AS 토큰 검증, `iss` 분기, AS 토큰 jti 블랙리스트 미적용(§4 경계).
- `auth/GatewayAuthGlobalFilterTest` — 스푸핑 헤더 제거·화이트리스트·401·신뢰 헤더 주입.
- `config/PrincipalKeyResolverTest` — 레이트리밋 키 산출 우선순위(검증 userId → Principal → XFF 첫 홉 → remote IP → unknown)·선행 콤마 기형 XFF 폴백·deny-empty-key 안전.
- `web/FallbackControllerTest` — 서킷브레이커 폴백 503(ApiResponse.fail 형식)·서비스명 보간/정화.
- `GatewayCorsPreflightTest` — 게이트웨이 기동 후 CORS preflight(허용 origin echo / 미허용 차단 / 비라우트 경로 처리).

---

## 3. 환경 설정 (Configuration)

엣지 인증/이중 발급기/블랙리스트는 **모두 기본 off**(옵트인). 켜면 게이트웨이가 토큰을 1차 검증하고 `X-User-Id`/`X-User-Roles` 신뢰 헤더를 주입한다(클라이언트가 보낸 동일 헤더는 항상 먼저 제거 → 스푸핑 차단).

| 변수 | 기본값 | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | (없음) | 운영 프로파일 |
| `GATEWAY_AUTH_ENABLED` | `false` | 엣지 JWT 1차 검증 + 신뢰 헤더 주입 |
| `JWT_SECRET` | (빈값) | 자체 JWT(HMAC) 검증 시크릿 — **user-service 와 동일 값** |
| `GATEWAY_BLACKLIST_CHECK_ENABLED` | `false` | 중앙 로그아웃(SSO): 로그아웃 토큰 엣지 401 차단(reactive Redis 필요) |
| `GATEWAY_AS_ENABLED` | `false` | 이중 발급기 — AS(OP) 발급 RS256/JWKS 토큰도 수용 |
| `AUTH_SERVER_ISSUER` | (빈값) | AS issuer(`iss` 분기 기준). `GATEWAY_AS_ENABLED=true` 면 필수 |
| `AUTH_SERVER_JWKS_URI` | (빈값) | 생략 시 `{issuer}/oauth2/jwks` 자동 |
| `REDIS_HOST`/`REDIS_PORT` | localhost/6379 | Redis 연결(레이트리밋·블랙리스트) |

토큰 `iss` 로 분기: AS issuer 면 JWKS/RS256 검증, 내부 issuer 면 자체 JWT(HMAC). AS 토큰은 jti 블랙리스트 미적용(폐기는 AS `/oauth2/revoke`). 상세: [`docs/modules/GATEWAY_EDGE_AUTH.md`](../../docs/modules/GATEWAY_EDGE_AUTH.md).

---

## 4. 기동 (Run)

게이트웨이는 **Redis 가 떠 있어야** 정상 동작한다(레이트리밋 `RequestRateLimiter` + 선택적 로그아웃 블랙리스트가 reactive Redis 사용).

```bash
# Redis 준비 (예: 도커)
docker run -d --name redis -p 6379:6379 redis:7

# 게이트웨이 기동 — 엣지 인증 off(기본), 단순 라우팅/레이트리밋만
./gradlew :services:gateway:bootRun
#   → http://localhost:8000  (헬스: /actuator/health)
```

### ⚠️ 라우트의 `lb://` 와 로컬 실행
기본 라우트는 `uri: lb://user-service` 로 **서비스 디스커버리/로드밸런서 전제**다(K8s 등). 로컬 단독에서는 해석되지 않으므로 둘 중 하나:
- K8s 에 배포(서비스 DNS 로 라우팅) — 정석.
- 로컬 검증용 정적 주소 override:
  ```bash
  ./gradlew :services:gateway:bootRun \
    --args='--spring.cloud.gateway.server.webflux.routes[0].uri=http://localhost:8080'
  ```

> **운영(K8s)**: `SPRING_PROFILES_ACTIVE` 와 env 로 제어, `REDIS_HOST` 는 클러스터 Redis.

---

## 5. 실행 확인 (Verify)

```bash
curl -s http://localhost:8000/actuator/health

# 엣지 인증 on 시: 토큰 없이 보호 경로 호출 → 401(ApiResponse.fail JSON)
curl -i http://localhost:8000/api/v1/users/me
# 유효 Bearer 면 통과 + 다운스트림에 X-User-Id/X-User-Roles 주입
```
- 레이트리밋: 토큰버킷(`replenishRate=10/s`, `burstCapacity=20`), 키=인증 userId(없으면 XFF 첫 홉 → remote IP). 초과 시 `429`.
- 서킷브레이커: user-service 라우트에 `fallbackUri: forward:/fallback/user` → `FallbackController` 가 **503**(`{"success":false,"code":"E0503",...}`)로 graceful 응답(핸들러가 없으면 404 로 샜었음 — 점검에서 보강).

### CORS preflight 확인
```bash
# 허용 origin → 200 + Access-Control-Allow-Origin echo
curl -i -X OPTIONS http://localhost:8000/api/v1/users/me \
  -H "Origin: https://app.yourdomain.com" \
  -H "Access-Control-Request-Method: GET"
# 미허용 origin(https://evil.com) → Access-Control-Allow-Origin 미부여
```

### 레이트리밋 429 확인 (Redis 필요)
```bash
# burstCapacity(20) 를 넘겨 같은 키로 연타 → 일부 429
for i in $(seq 1 30); do \
  curl -s -o /dev/null -w "%{http_code} " http://localhost:8000/api/v1/users/me \
    -H "X-Forwarded-For: 203.0.113.9"; done; echo
# 200 ... 200 429 429 ... (replenishRate=10/s 로 회복)
```
> 429 의 실제 토큰버킷 동작은 reactive Redis 가 떠 있어야 한다. 키 산출 로직 자체는 `PrincipalKeyResolverTest`(Redis 불요)가 커버한다.

---

## 6. 사용 (Usage)

- **엣지 인증 켜기**: `GATEWAY_AUTH_ENABLED=true` + `JWT_SECRET`(user-service 와 동일). 다운스트림은 무변경으로 신뢰 헤더 수용.
- **이중 발급기 켜기**(AS 토큰 수용): `GATEWAY_AS_ENABLED=true` + `AUTH_SERVER_ISSUER`(= auth-server issuer).
- **중앙 로그아웃**: `GATEWAY_BLACKLIST_CHECK_ENABLED=true`(reactive Redis) → 로그아웃 토큰 엣지 401. 자세히 [`docs/modules/SSO_CENTRAL_LOGOUT.md`](../../docs/modules/SSO_CENTRAL_LOGOUT.md).

---

## 7. 암호화 값 다루기 (Encrypted values)

> 상세/공통은 **[`docs/ENCRYPTION_GUIDE.md`](../../docs/ENCRYPTION_GUIDE.md)**.

게이트웨이는 DB/컬럼 암호화는 없다. 다만 **민감 설정(`JWT_SECRET` 등)을 `ENC(...)` 로 둘 수 있다**(기동 시 자동 복호 — `framework-core` EPP 가 게이트웨이에도 동일 적용).

```bash
# JWT_SECRET 을 ENC 토큰으로 만들어 yaml/secret 에 보관
AES_SECRET="$AES_SECRET" ./gradlew --no-daemon -q \
  :framework:framework-core:encryptSecret -Pplain='게이트웨이-JWT-시크릿'
```
```yaml
gateway:
  auth:
    jwt-secret: ENC(Qk9k...)   # 기동 시 자동 복호
```
- `ENC(...)` 를 쓰면 마스터키 `AES_SECRET`(평문, k8s Secret) 주입이 필요하다. 마스터키 자신은 `ENC(...)` 불가.

---

## 8. 컨테이너 / 배포

```bash
./gradlew :services:gateway:bootJar
```
K8s 배포가 정석(`lb://` 라우팅·클러스터 Redis·NetworkPolicy 로 백엔드 인그레스 제한). `JWT_SECRET`/`AES_SECRET` 등은 k8s Secret 으로 주입.

**Kustomize 멀티서비스 배포** (4개 서비스 + 인-클러스터 Redis 일괄):
```bash
kubectl apply -k deploy/k8s/overlays/dev     # 개발(약한 시크릿 동봉, 1 레플리카)
kubectl apply -k deploy/k8s/overlays/prod    # 운영(HPA·외부 DB/시크릿 전제 — ESO/SealedSecrets)
```
레이아웃·서비스별 env 계약·시크릿 주입·ServiceMonitor 는 `docs/modules/K8S_CICD_MULTISERVICE.md` 참고.

---

## 참고 문서
- 암호화 가이드: [`docs/ENCRYPTION_GUIDE.md`](../../docs/ENCRYPTION_GUIDE.md)
- 엣지 인증/이중 발급기: [`docs/modules/GATEWAY_EDGE_AUTH.md`](../../docs/modules/GATEWAY_EDGE_AUTH.md)
- 중앙 로그아웃(SSO): [`docs/modules/SSO_CENTRAL_LOGOUT.md`](../../docs/modules/SSO_CENTRAL_LOGOUT.md)
- AS 연동 경계: [`docs/modules/AUTH_SERVER.md`](../../docs/modules/AUTH_SERVER.md)
