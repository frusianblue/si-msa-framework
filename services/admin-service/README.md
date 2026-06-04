# admin-service (관리/RBAC 메타데이터 API)

메뉴·리소스·역할 매핑 등 RBAC 메타데이터 관리 REST API. framework-security·openapi·file·commoncode 조합. 서블릿 + MyBatis.

- **포트**: `8081`
- **스택**: Spring Boot 4.0.6 / Java 21 / MyBatis / Flyway / framework-security·openapi·file·commoncode
- **Swagger UI**: `http://localhost:8081/swagger-ui.html`
- **H2 콘솔(local)**: `http://localhost:8081/h2-console` (JDBC `jdbc:h2:mem:admindb`, user `sa`)

---

## 기동 (How to run)

기본 활성 프로파일 = `local`(H2 인메모리). user-service(:8080)와 다른 포트라 같이 띄워도 충돌 없다.

```bash
# 1) 기본 로컬 기동 — H2 메모리, 감사로그 jdbc(H2 적재 → /h2-console 에서 검증)
./gradlew :services:admin-service:bootRun
#   → http://localhost:8081  (Swagger: /swagger-ui.html, H2: /h2-console)

# 2) 로컬 PostgreSQL (전제: localhost:5432/sidb)
./gradlew :services:admin-service:bootRun --args='--spring.profiles.active=local,local-postgres'

# 3) + Redis (토큰 store=redis)
./gradlew :services:admin-service:bootRun --args='--spring.profiles.active=local,local-redis'

# 4) 로그인/권한 우회 (개발 편의)
./gradlew :services:admin-service:bootRun --args='--spring.profiles.active=local,local-noauth'
```

> **컨테이너/운영**은 인자 없이 `SPRING_PROFILES_ACTIVE=dev|prod` + env 주입.

### 대표 엔드포인트 (RBAC 관리)

```
GET/POST/PUT/DELETE  /api/v1/admin/menus            메뉴
GET/POST/PUT/DELETE  /api/v1/admin/resources        보호 리소스
POST/DELETE          /api/v1/admin/.../role-map     역할 매핑
```

> 관리 API 라 ADMIN 권한 토큰이 필요하다. 개발 중엔 `local-noauth` 로 우회 가능.

---

## 프로파일

| 프로파일 | DB / 저장소 | 용도 |
|---|---|---|
| `local` (기본) | H2 인메모리 `admindb` + /h2-console, audit=jdbc | 단독 기동·검증 |
| `local-postgres` | localhost:5432/sidb | 로컬 PostgreSQL |
| `local-redis` | + Redis | 토큰 store=redis |
| `local-noauth` | local 위 겹침 | 로그인/권한 우회(개발) |
| `dev` | env 주입(DB/Redis) | 개발 서버. token-store=redis, audit=jdbc |
| `prod` | env 주입(전부) | 운영. 모든 시크릿 env |

Flyway: `V1`(init) · `V2`(audit_log).

---

## 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | 활성 프로파일 |
| `DB_URL` | (프로파일별) | 데이터소스 URL |
| `JWT_SECRET` | (local placeholder) | 자체 JWT 서명 시크릿 |
| `TOKEN_STORE_TYPE` | `memory` | `memory`\|`jdbc`\|`redis`(다중 인스턴스는 redis) |
| `AES_SECRET` | (placeholder) | 마스터키(컬럼/파일 암호화 등) |
| `AUDIT_STORE_TYPE` | `logging`(prod) / `jdbc`(local) | 감사 로그 적재 |
| `REDIS_HOST`/`REDIS_PORT` | localhost/6379 | Redis 연결 |

---

## 컨테이너 / 배포

```bash
./gradlew :services:admin-service:bootJar    # 실행 가능 jar (CI 빌드 → Dockerfile JAR_FILE 주입)
```

운영 다중 인스턴스는 `TOKEN_STORE_TYPE=redis` 권장(기본 `memory` 는 인스턴스별).

---

## 참고 문서
- 로컬 설치: [`docs/LOCAL_SETUP.md`](../../docs/LOCAL_SETUP.md)
- 모듈 카탈로그: [`docs/FRAMEWORK_MODULES.md`](../../docs/FRAMEWORK_MODULES.md)
