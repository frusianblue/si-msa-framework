# auth-server (OAuth2 / OIDC Authorization Server · OP)

우리가 외부/그룹사에 표준 OAuth2/OIDC 토큰을 발급하는 **독립 배포 부트 서비스**다. 내부 1차 인증/세션은 기존 자체 JWT(framework-security)를 그대로 쓰고, **외부/그룹사 위임 발급만** 이 서버를 거친다(이중 발급기 경계). Spring Authorization Server(SAS, Spring Security 7 흡수) 기반, 서블릿.

- **포트**: `9000` (독립 포트 — 서비스 경계)
- **discovery**: `http://localhost:9000/.well-known/openid-configuration`
- **스택**: Spring Boot 4.0.6 / Java 21 / Spring Authorization Server / MyBatis / Flyway / **Jackson 3**
- **저장소**: 클라이언트·인가·동의·서명키 **전부 JDBC**(다중 파드 공유)

---

## 기동 (How to run)

루트에서 Gradle 래퍼로 띄운다(별도 gradle 설치 불필요). 기본 활성 프로파일 = `local`(H2 인메모리 + 서명키 부트스트랩 + demo 클라이언트 자동 등록).

```bash
# 1) 가장 단순한 로컬 기동 — H2 메모리, 회전 off, 개인키 암호화 on(placeholder 키)
./gradlew :services:auth-server:bootRun
#   → http://localhost:9000/.well-known/openid-configuration (200 이면 정상)

# 2) 서명키 회전 + 개인키 암호화까지 켜서 기동 (이 서비스의 핵심 기능 검증)
export AES_SECRET="$(openssl rand -base64 32)"     # 개인키 컬럼 암호화 마스터키(임의 32B+). 한 번 정하면 바꾸지 말 것
export SIGNING_KEY_ROTATION_ENABLED=true           # 회전 스케줄러 활성(기본 off)
export LOCK_TYPE=jdbc                               # 다중 파드 단일 회전(@SchedulerLock) 백엔드
./gradlew :services:auth-server:bootRun

# 3) 로컬 PostgreSQL 로 전환(전제: localhost:5432/authdb, authuser/authpass)
./gradlew :services:auth-server:bootRun --args='--spring.profiles.active=local,local-postgres'
```

> **컨테이너/운영**은 인자 없이 `SPRING_PROFILES_ACTIVE` 와 env 로 제어한다.
> `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `AUTH_SERVER_ISSUER`, `AES_SECRET`(필수·강한키), `FRAMEWORK_JWT_SECRET` 등.

---

## 프로파일

| 프로파일 | DB | 용도 |
|---|---|---|
| `local` (기본) | H2 인메모리 `authdb` | 단독 기동·기능 검증. demo 클라이언트/계정 자동 시드 |
| `local-postgres` | localhost:5432/authdb | 로컬 PostgreSQL 검증 |
| (운영) | `DB_URL` 주입 | `SPRING_PROFILES_ACTIVE` 로 지정, 모든 시크릿 env 주입 |

Flyway 마이그레이션: `V1`(SAS 스키마) · `V2`(서명키) · `V3`(빈 RBAC 테이블) · `V4`(framework_lock) · `V5`(서명키 retired_at).

---

## 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | 활성 프로파일 |
| `AUTH_SERVER_ISSUER` | `http://localhost:9000` | OP issuer(토큰 `iss`·discovery 기준) |
| `AES_SECRET` | (local placeholder) | 서명키 개인키 컬럼 암호화 마스터키. **운영 필수·강한키**(prod 약한키/기본값이면 부팅 차단). 한 번 정하면 변경 금지(기존 암호문 복호 불가) |
| `SIGNING_KEY_ROTATION_ENABLED` | `false` | 서명키 회전 스케줄러 on/off |
| `SIGNING_KEY_ENCRYPTION_ENABLED` | `true` | 개인키 컬럼 암호화(쓰기). 읽기는 토글 무관 항상 `enc:` 마커 인지 |
| `LOCK_TYPE` | `jdbc` | 회전 단일 실행 락 백엔드(`jdbc`\|`redis`). 운영 다중 파드는 `jdbc`/`redis` 필수(`memory`는 파드 간 상호배제 불가) |
| `FRAMEWORK_JWT_SECRET` | (local placeholder) | framework-security 사용자 소스 재사용용 |

---

## 데모 (local 프로파일 한정)

- 로그인 계정: `demo` / `demo`
- 클라이언트: `demo-web`(authorization_code + PKCE) · `demo-service`(client_credentials, secret=`demo-secret`)

```bash
# discovery
curl http://localhost:9000/.well-known/openid-configuration

# client_credentials 토큰 발급 (demo-service)
curl -u demo-service:demo-secret \
  -d 'grant_type=client_credentials' \
  http://localhost:9000/oauth2/token

# JWKS (서명키 공개키 — 회전 시 새 kid 등장, 직전 kid 는 grace 동안 잔존=오버랩)
curl http://localhost:9000/oauth2/jwks
```

엔드포인트: `/oauth2/authorize` · `/oauth2/token` · `/oauth2/jwks` · `/.well-known/openid-configuration` · `/userinfo` · `/oauth2/revoke` · `/oauth2/introspect`

---

## 서명키 회전 동작 확인

회전이 켜진 상태(`SIGNING_KEY_ROTATION_ENABLED=true`)에서:

1. 기동 직후 `auth_signing_key` 에 ACTIVE 1건 부트스트랩(개인키 `jwk_json` 은 `enc:` 마커로 암호화 저장).
2. 회전(cron 기본 `0 0 4 1 * *` 또는 수동 트리거) 후 — `/oauth2/jwks` 에 **새 kid** 등장 + 직전 kid 가 grace(기본 14d) 동안 **함께 잔존**(검증 오버랩).
3. 다중 파드면 `@SchedulerLock`(리더 선출)로 **한 파드만** 회전(로그에 "회전 완료"가 1회).

상세 설계/함정: [`../../docs/NEXT_SIGNING_KEY_ROTATION.md`](../../docs/NEXT_SIGNING_KEY_ROTATION.md) · [`../../docs/modules/AUTH_SERVER.md`](../../docs/modules/AUTH_SERVER.md)

---

## 컨테이너 / 배포

```bash
./gradlew :services:auth-server:bootJar      # 실행 가능 jar (CI 가 빌드 → Dockerfile 이 JAR_FILE 로 주입)
```

운영은 `AES_SECRET`/`FRAMEWORK_JWT_SECRET`/`DB_URL` 등을 **k8s Secret** 으로 주입한다(셸 export 금지). `AES_SECRET` 은 절대 교체하지 않는다.

---

## 참고 문서
- 서비스 가이드: [`docs/modules/AUTH_SERVER.md`](../../docs/modules/AUTH_SERVER.md)
- 회전 설계: [`docs/NEXT_SIGNING_KEY_ROTATION.md`](../../docs/NEXT_SIGNING_KEY_ROTATION.md)
- 게이트웨이 이중 발급기 연동: [`docs/modules/GATEWAY_EDGE_AUTH.md`](../../docs/modules/GATEWAY_EDGE_AUTH.md)
- 토큰 검증(다운스트림): [`docs/TOKEN_VERIFICATION_GUIDE.md`](../../docs/TOKEN_VERIFICATION_GUIDE.md)
