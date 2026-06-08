# auth-server (OAuth2 / OIDC Authorization Server · OP)

우리가 외부/그룹사에 표준 OAuth2/OIDC 토큰을 발급하는 **독립 배포 부트 서비스**다. 내부 1차 인증/세션은 기존 자체 JWT(framework-security)를 그대로 쓰고, **외부/그룹사 위임 발급만** 이 서버를 거친다(이중 발급기 경계). Spring Authorization Server(SAS, Spring Security 7 흡수) 기반, 서블릿.

- **포트**: `9000` (독립 포트 — 서비스 경계)
- **discovery**: `http://localhost:9000/.well-known/openid-configuration`
- **스택**: Spring Boot 4.0.6 / Java 21 / Spring Authorization Server / MyBatis / Flyway / **Jackson 3**
- **저장소**: 클라이언트·인가·동의·서명키 **전부 JDBC**(다중 파드 공유)
- **그랜트**: `authorization_code`+PKCE · `client_credentials` (implicit/password 미채택)

---

## 1. 빌드 (Build)

루트에서 Gradle 래퍼로 빌드한다(별도 gradle 설치 불필요).

```bash
./gradlew :services:auth-server:build      # 컴파일 + 테스트 + 검증 + assemble
./gradlew :services:auth-server:bootJar     # 실행 가능 jar (build/libs/*.jar) — CI→Dockerfile JAR_FILE
./gradlew :services:auth-server:compileJava # 컴파일만 (빠른 확인)
```

> 코드 스타일: 루트 `./gradlew spotlessApply` (커밋 전).

---

## 2. 테스트 (Test)

```bash
# 이 서비스 단위/통합 테스트 전체
./gradlew :services:auth-server:test

# 토큰 발급 라운드트립 e2e만 (실 발급→JWKS→다운스트림 zero-trust 재검증)
./gradlew :services:auth-server:test --tests "*TokenIssuanceRoundTripTest"

# 서명키 회전/암호화 단위 테스트
./gradlew :services:auth-server:test --tests "*SigningKeyRotationServiceTest" --tests "*AesSigningKeyCipherTest"
```

주요 테스트:
- `e2e/TokenIssuanceRoundTripTest` — 실제 기동(@SpringBootTest RANDOM_PORT, profile `local`)한 AS 가 두 그랜트로 발급한 진짜 RS256 access token 을, 실 `/oauth2/jwks` 공개키로 `ResourceServerJwtVerifier`/`DownstreamTokenAuthenticator`(다운스트림 zero-trust)가 재검증 + 음성(issuer 핀 불일치·서명 변조 거부). **H2 인메모리라 외부 설치 0.**
- `jose/SigningKeyRotationServiceTest` · `jose/AesSigningKeyCipherTest` — 회전(RETIRE→INSERT→grace)·멱등·개인키 `enc:` 마커 라운드트립.

> 아키텍처 규칙(모듈 순환/Jackson3/네이밍 등)은 프레임워크 레벨: `./gradlew :framework:framework-archtest:test`.

---

## 3. 환경 설정 (Configuration)

환경 구분은 `local | dev | prod`(+ `local-postgres` 오버레이). 기본 `local`.

| 프로파일 | DB | 용도 |
|---|---|---|
| `local` (기본) | H2 인메모리 `authdb` | 단독 기동·기능 검증. demo 클라이언트/계정 자동 시드 |
| `local-postgres` | localhost:5432/authdb | 로컬 PostgreSQL 검증 |
| (운영) `dev`/`prod` | `DB_URL` 주입 | `SPRING_PROFILES_ACTIVE` 로 지정, 모든 시크릿 env 주입 |

Flyway 마이그레이션: `V1`(SAS 스키마) · `V2`(서명키) · `V3`(빈 RBAC 테이블) · `V4`(framework_lock) · `V5`(서명키 retired_at) · **`V6`(SS7 정합 — `oauth2_authorization` device_code/user_code 컬럼)**.

### 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | 활성 프로파일 |
| `AUTH_SERVER_ISSUER` | `http://localhost:9000` | OP issuer(토큰 `iss`·discovery 기준). 외부 접근 가능한 안정 URL |
| `AES_SECRET` | (local placeholder) | 서명키 개인키 컬럼 암호화 마스터키. **운영 필수·강한키**(prod 약한키/기본값이면 부팅 차단). **한 번 정하면 변경 금지**(기존 암호문 복호 불가) |
| `SIGNING_KEY_ROTATION_ENABLED` | `false` | 서명키 회전 스케줄러 on/off |
| `SIGNING_KEY_ROTATION_CRON` | `0 0 4 1 * *` | 회전 주기(매월 1일 04:00) |
| `SIGNING_KEY_RETIRE_GRACE` | `14d` | RETIRED 보존(폐기 후) — ≥ access수명+JWKS캐시TTL+여유 |
| `SIGNING_KEY_ENCRYPTION_ENABLED` | `true` | 개인키 컬럼 암호화(쓰기). 읽기는 토글 무관 항상 `enc:` 마커 인지 |
| `LOCK_TYPE` | `jdbc` | 회전 단일 실행 락 백엔드(`jdbc`\|`redis`). 운영 다중 파드는 `jdbc`/`redis` 필수(`memory`는 파드 간 상호배제 불가) |
| `FRAMEWORK_JWT_SECRET` | (local placeholder) | framework-security 사용자 소스 재사용용 |

---

## 4. 기동 (Run)

```bash
# 1) 가장 단순한 로컬 기동 — H2 메모리, 회전 off, 개인키 암호화 on(placeholder 키)
./gradlew :services:auth-server:bootRun
#   → http://localhost:9000/.well-known/openid-configuration (200 이면 정상)

# 2) 서명키 회전 + 개인키 암호화까지 켜서 기동 (핵심 기능 검증)
export AES_SECRET="$(openssl rand -base64 32)"     # 개인키 컬럼 암호화 마스터키(임의 32B+). 한 번 정하면 바꾸지 말 것
export SIGNING_KEY_ROTATION_ENABLED=true           # 회전 스케줄러 활성(기본 off)
export LOCK_TYPE=jdbc                               # 다중 파드 단일 회전(@SchedulerLock) 백엔드
./gradlew :services:auth-server:bootRun

# 3) 로컬 PostgreSQL 로 전환(전제: localhost:5432/authdb, auth_app/authpass)
./gradlew :services:auth-server:bootRun --args='--spring.profiles.active=local,local-postgres'
```

> **컨테이너/운영**은 인자 없이 `SPRING_PROFILES_ACTIVE` 와 env 로 제어한다(`prod`, `DB_URL`, `AUTH_SERVER_ISSUER`, `AES_SECRET`(필수·강한키), `FRAMEWORK_JWT_SECRET` 등 → k8s Secret).

---

## 5. 실행 확인 (Verify)

```bash
# discovery (200 + issuer/authorize/token/jwks/userinfo/revoke/introspect 노출)
curl -s http://localhost:9000/.well-known/openid-configuration | head

# JWKS — 서명키 공개키(회전 시 새 kid 등장, 직전 kid 는 grace 동안 잔존=오버랩)
curl -s http://localhost:9000/oauth2/jwks

# 헬스
curl -s http://localhost:9000/actuator/health
```

### 서명키 회전 동작 확인 (`SIGNING_KEY_ROTATION_ENABLED=true`)
1. 기동 직후 `auth_signing_key` 에 ACTIVE 1건 부트스트랩(개인키 `jwk_json` 은 `enc:` 마커로 암호화 저장).
2. 회전(cron 또는 수동 트리거) 후 — `/oauth2/jwks` 에 **새 kid** 등장 + 직전 kid 가 grace(기본 14d) 동안 **함께 잔존**(검증 오버랩).
3. 다중 파드면 `@SchedulerLock`(리더 선출)로 **한 파드만** 회전(로그 "회전 완료" 1회).

---

## 6. 사용 (Usage)

### 데모 자산 (local 프로파일 한정)
- 로그인 계정: `demo` / `demo`
- 클라이언트: `demo-web`(authorization_code + PKCE, public) · `demo-service`(client_credentials, secret=`demo-secret`)

```bash
# client_credentials 토큰 발급 (demo-service) → RS256 access_token
curl -s -u demo-service:demo-secret \
  -d 'grant_type=client_credentials&scope=api.read' \
  http://localhost:9000/oauth2/token

# authorization_code + PKCE 는 브라우저 흐름(로그인→authorize→코드→교환). 자동 검증은 e2e 테스트(§2) 참고.
```

엔드포인트: `/oauth2/authorize` · `/oauth2/token` · `/oauth2/jwks` · `/.well-known/openid-configuration` · `/userinfo` · `/oauth2/revoke` · `/oauth2/introspect`

### 다운스트림 연동(이중 발급기)
게이트웨이/리소스 서버는 토큰 `iss` 로 분기해 AS 토큰(RS256/JWKS)을 수용한다. 게이트웨이 `GATEWAY_AS_ENABLED=true`, user-service `RESOURCE_SERVER_ENABLED=true` + `AUTH_SERVER_ISSUER` 동일. AS 토큰 폐기는 `/oauth2/revoke`(자체 jti 블랙리스트와 혼용 금지).

---

## 7. 암호화 값 다루기 (Encrypted values)

> 상세/공통은 **[`docs/ENCRYPTION_GUIDE.md`](../../docs/reference/ENCRYPTION_GUIDE.md)**.

이 서비스가 쓰는 암호화:
- **서명키 개인키(컬럼, `enc:` 마커)** — `auth_signing_key.jwk_json` 을 `AesSigningKeyCipher`(AES-256-GCM)가 **자동 암호화 저장**한다. 운영자는 `AES_SECRET` 만 주입하면 된다. 토글 `SIGNING_KEY_ENCRYPTION_ENABLED`(쓰기, 기본 on), 읽기는 토글 무관 항상 마커 인지(평문↔암호문 혼재·롤백 안전).
- **설정값(`ENC(...)`)** — DB 비밀번호 등 민감 설정을 yaml 에 평문으로 두지 않으려면 `ENC(...)` 로 넣는다. 기동 시 자동 복호.
  ```bash
  # 마스터키로 ENC 토큰 생성 → application-*.yml 에 붙여넣기
  AES_SECRET="$AES_SECRET" ./gradlew --no-daemon -q \
    :framework:framework-core:encryptSecret -Pplain='실제DB비번'
  # 출력: ENC(...)
  ```
- **마스터키 규칙**: `AES_SECRET` = `openssl rand -base64 32`, k8s Secret 주입, **교체 금지**(기존 암호문 복호 불가). prod 에서 약한 키/placeholder 면 부팅 차단(`AesMasterKeySafetyGuard`).

---

## 8. 컨테이너 / 배포

```bash
./gradlew :services:auth-server:bootJar
```
운영은 `AES_SECRET`/`FRAMEWORK_JWT_SECRET`/`DB_URL` 등을 **k8s Secret** 으로 주입(셸 export 금지). `AES_SECRET` 은 절대 교체하지 않는다.

**Kustomize 멀티서비스 배포** (4개 서비스 + 인-클러스터 Redis 일괄):
```bash
kubectl apply -k deploy/k8s/overlays/dev     # 개발(약한 시크릿 동봉, 1 레플리카)
kubectl apply -k deploy/k8s/overlays/prod    # 운영(HPA·외부 DB/시크릿 전제 — ESO/SealedSecrets)
```
레이아웃·서비스별 env 계약·시크릿 주입·ServiceMonitor 는 `docs/modules/K8S_CICD_MULTISERVICE.md` 참고.

---

## 참고 문서
- 서비스 가이드: [`docs/modules/AUTH_SERVER.md`](../../docs/modules/AUTH_SERVER.md)
- 암호화 가이드: [`docs/ENCRYPTION_GUIDE.md`](../../docs/reference/ENCRYPTION_GUIDE.md)
- 회전 설계: [`docs/NEXT_SIGNING_KEY_ROTATION.md`](../../docs/_internal/planning/NEXT_SIGNING_KEY_ROTATION.md)
- OIDC id_token 발급(다음 작업): [`docs/NEXT_OIDC_ID_TOKEN.md`](../../docs/_internal/planning/NEXT_OIDC_ID_TOKEN.md)
- 게이트웨이 이중 발급기 연동: [`docs/modules/GATEWAY_EDGE_AUTH.md`](../../docs/modules/GATEWAY_EDGE_AUTH.md)
- 토큰 검증(다운스트림): [`docs/TOKEN_VERIFICATION_GUIDE.md`](../../docs/reference/TOKEN_VERIFICATION_GUIDE.md)
