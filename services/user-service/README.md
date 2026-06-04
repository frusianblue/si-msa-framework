# user-service (사용자/인증 API)

회원·인증·비밀번호·파일 업로드 등 사용자 도메인 REST API. framework-security(JWT/RBAC/비번정책)·openapi·file·commoncode 를 조합한 표준 업무 서비스다. 서블릿 + MyBatis.

- **포트**: `8080`
- **스택**: Spring Boot 4.0.6 / Java 21 / MyBatis / Flyway / framework-security·openapi·file·commoncode
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`

---

## 기동 (How to run)

기본 활성 프로파일 = `local`(H2 인메모리 + 시드 계정). 루트에서 Gradle 래퍼로 띄운다.

```bash
# 1) 기본 로컬 기동 — H2 메모리 + 시드 계정, 인증 on
./gradlew :services:user-service:bootRun
#   → http://localhost:8080  (Swagger: /swagger-ui.html, H2 콘솔: /h2-console)

# 2) 로컬 PostgreSQL (전제: localhost:5432/sidb)
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local,local-postgres'

# 3) + Redis (리프레시 토큰/블랙리스트/분산 캐시 공유)
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local,local-postgres,local-redis'

# 4) 로그인/권한 우회 (개발 편의 — 토큰 없이 호출)
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local,local-noauth'
```

> **컨테이너/운영**은 인자 없이 `SPRING_PROFILES_ACTIVE=dev|prod` + env 주입.

### 동작 확인 (시드 계정)

```bash
# 시드: admin/admin123 (ADMIN), hong/hong123 (USER)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"loginId":"admin","password":"admin123"}'
# 같은 loginId 로 5회 실패 → 6번째부터 429(LOGIN_LOCKED)

# 본인 비밀번호 변경(현재 비번 필요) / 관리자 강제 초기화(ADMIN 토큰 필요)
# PATCH /api/v1/users/me/password         body: {"currentPassword","newPassword"}
# PATCH /api/v1/users/{id}/password/reset body: {"newPassword"}   ← 비ADMIN 은 403
```

---

## 프로파일

| 프로파일 | DB / 저장소 | 용도 |
|---|---|---|
| `local` (기본) | H2 인메모리 + 시드, token-store=memory | 단독 기동·기능 검증 |
| `local-postgres` | localhost:5432/sidb | 로컬 PostgreSQL |
| `local-redis` | + Redis | 토큰/블랙리스트/캐시 공유(다중 인스턴스 모사) |
| `local-noauth` | local 위 겹침 | 로그인/권한 우회(개발) |
| `dev` | env 주입(DB/Redis) | 개발 서버. token-store/audit=redis/jdbc 기본 |
| `prod` | env 주입(전부) | 운영. 모든 시크릿 env |

> ⚠️ 환경 구분은 `local` / `dev` / `prod`. 과거 `local,dev`(로그인 우회)는 이제 `local,local-noauth`. `dev`는 개발 서버(env 주입)를 뜻한다. 로컬 설치 상세는 [`docs/LOCAL_SETUP.md`](../../docs/LOCAL_SETUP.md).

Flyway: `V1`(init) · `V2`(common_code) · `V3`(file_metadata) · `V4`(audit_log).

---

## 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | 활성 프로파일 |
| `DB_URL` | (프로파일별) | 데이터소스 URL |
| `JWT_SECRET` | (local placeholder) | 자체 JWT 서명 시크릿(게이트웨이와 동일 값 공유) |
| `TOKEN_STORE_TYPE` | `memory` | `memory`\|`jdbc`\|`redis`. **다중 인스턴스는 redis 필수** |
| `EDGE_TRUST_MODE` | `zero-trust` | `zero-trust`(Bearer 재검증·안전 기본) \| `gateway-headers`(헤더 신뢰·격리 환경 한정) |
| `RESOURCE_SERVER_ENABLED` | `false` | AS(OP) RS256/JWKS 토큰 재검증 활성 |
| `AUTH_SERVER_ISSUER` | (빈값) | AS issuer(이중 발급기 재검증 시) |
| `AES_SECRET` | (placeholder) | 파일 at-rest 암호화 등 마스터키 |
| `AUDIT_STORE_TYPE` | `logging` | 감사 로그 적재(`logging`\|`jdbc`) |
| `FILE_STORAGE_TYPE` | `local` | `local`\|`nas`\|`s3`(s3 는 framework-file-s3 필요) |
| `REDIS_HOST`/`REDIS_PORT` | localhost/6379 | Redis 연결 |

> **다중 인스턴스 주의**: k8s `replicas: 2` 기준 — `TOKEN_STORE_TYPE=redis` + `framework.security.login-attempt.type=redis` 를 켜야 토큰/잠금이 공유된다(기본 `memory` 는 인스턴스별 → 잠금 우회 가능).

---

## 컨테이너 / 배포

```bash
./gradlew :services:user-service:bootJar     # 실행 가능 jar (CI 빌드 → Dockerfile JAR_FILE 주입)
```

---

## 참고 문서
- 토큰 검증/배치 환경(K8s vs VM): [`docs/TOKEN_VERIFICATION_GUIDE.md`](../../docs/TOKEN_VERIFICATION_GUIDE.md)
- 로컬 설치: [`docs/LOCAL_SETUP.md`](../../docs/LOCAL_SETUP.md)
- 모듈 카탈로그: [`docs/FRAMEWORK_MODULES.md`](../../docs/FRAMEWORK_MODULES.md)
