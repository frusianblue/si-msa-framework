# gateway (엣지 게이트웨이 · API Gateway)

WebFlux 기반 Spring Cloud Gateway. 라우팅·레이트리밋·엣지 인증(JWT 1차 검증 + 신뢰 헤더 주입)·이중 발급기(자체 JWT + AS RS256/JWKS) 수용을 담당하는 엣지다.

- **포트**: `8000`
- **스택**: Spring Boot 4.0.6 / Java 21 / Spring Cloud Gateway(WebFlux) / Reactive Redis / Resilience4j / jjwt
- **DB 없음 · Flyway 없음** (상태는 Redis 에만)

---

## 기동 (How to run)

게이트웨이는 **Redis 가 떠 있어야** 정상 동작한다(레이트리밋 `RequestRateLimiter` + 선택적 로그아웃 블랙리스트가 reactive Redis 사용).

```bash
# Redis 준비 (예: 도커)
docker run -d --name redis -p 6379:6379 redis:7

# 게이트웨이 기동 — 엣지 인증 off(기본), 단순 라우팅/레이트리밋만
./gradlew :services:gateway:bootRun
#   → http://localhost:8000  (헬스: /actuator/health)
```

### ⚠️ 라우트의 `lb://` 와 로컬 실행

기본 라우트는 `uri: lb://user-service` 로 **서비스 디스커버리/로드밸런서 전제**다. 이 서비스엔 discovery client 의존성이 없으므로(= K8s/외부 디스커버리 환경 전제), **로컬 단독에서는 `lb://user-service` 가 해석되지 않는다.** 로컬에서 게이트웨이→user-service 라우팅을 끝까지 보려면 둘 중 하나:

- K8s 에 배포(서비스 DNS `http://user-service:8080` 로 라우팅) — 정석.
- 로컬 검증용으로 라우트 uri 를 정적 주소로 override:
  ```bash
  ./gradlew :services:gateway:bootRun \
    --args='--spring.cloud.gateway.server.webflux.routes[0].uri=http://localhost:8080'
  ```
  (또는 application.yml 의 `uri` 를 임시로 `http://localhost:8080` 으로 두고 user-service 를 :8080 에 함께 기동.)

> **운영(K8s)**: `SPRING_PROFILES_ACTIVE` 와 env 로 제어, `REDIS_HOST` 는 클러스터 Redis 를 가리킨다.

---

## 엣지 인증 / 이중 발급기 (옵트인)

모두 기본 **off** — 켜면 게이트웨이가 토큰을 1차 검증하고 `X-User-Id`/`X-User-Roles` 신뢰 헤더를 주입한다(클라이언트가 보낸 동일 헤더는 항상 먼저 제거 → 스푸핑 차단). 다운스트림은 무변경으로 수용.

| 변수 | 기본값 | 설명 |
|---|---|---|
| `GATEWAY_AUTH_ENABLED` | `false` | 엣지 JWT 1차 검증 + 신뢰 헤더 주입 |
| `JWT_SECRET` | (빈값) | 자체 JWT(HMAC) 검증 시크릿 — **user-service 와 동일 값** |
| `GATEWAY_BLACKLIST_CHECK_ENABLED` | `false` | 중앙 로그아웃(SSO): 로그아웃 토큰 엣지 401 차단(reactive Redis 필요) |
| `GATEWAY_AS_ENABLED` | `false` | 이중 발급기 — AS(OP) 발급 RS256/JWKS 토큰도 수용 |
| `AUTH_SERVER_ISSUER` | (빈값) | AS issuer(`iss` 분기 기준). `GATEWAY_AS_ENABLED=true` 면 필수 |
| `AUTH_SERVER_JWKS_URI` | (빈값) | 생략 시 `{issuer}/oauth2/jwks` 자동 |
| `REDIS_HOST`/`REDIS_PORT` | localhost/6379 | Redis 연결 |

토큰 `iss` 로 분기: AS issuer 면 JWKS/RS256 검증, 내부 issuer 면 자체 JWT(HMAC). AS 토큰은 jti 블랙리스트 미적용(폐기는 AS `/oauth2/revoke`).

상세: [`../../docs/modules/GATEWAY_EDGE_AUTH.md`](../../docs/modules/GATEWAY_EDGE_AUTH.md)

---

## 레이트리밋 · 관측성

- **레이트리밋**: 토큰버킷(`replenishRate=10/s`, `burstCapacity=20`), 키 = 인증된 userId(`principalKeyResolver`). 초과 시 `429`. (Redis 필요)
- **서킷브레이커**: Resilience4j — user-service 라우트에 `fallbackUri: forward:/fallback/user`.
- **헬스/프로브**: `/actuator/health`(K8s liveness/readiness), 트레이싱 OTLP.

---

## 컨테이너 / 배포

```bash
./gradlew :services:gateway:bootJar          # 실행 가능 jar (CI 빌드 → Dockerfile JAR_FILE 주입)
```

게이트웨이는 K8s 배포가 정석이다(`lb://` 라우팅·클러스터 Redis·NetworkPolicy 로 백엔드 인그레스 제한). `JWT_SECRET` 등은 k8s Secret 으로 주입한다.

---

## 참고 문서
- 엣지 인증/이중 발급기: [`docs/modules/GATEWAY_EDGE_AUTH.md`](../../docs/modules/GATEWAY_EDGE_AUTH.md)
- 중앙 로그아웃(SSO): [`docs/modules/SSO_CENTRAL_LOGOUT.md`](../../docs/modules/SSO_CENTRAL_LOGOUT.md)
- AS 연동 경계: [`docs/modules/AUTH_SERVER.md`](../../docs/modules/AUTH_SERVER.md)
