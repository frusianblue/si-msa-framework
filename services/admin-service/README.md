# admin-service (관리/RBAC 메타데이터 API)

메뉴·리소스·역할 매핑 등 RBAC 메타데이터 관리 REST API. framework-security·openapi·file·commoncode 조합. 서블릿 + MyBatis.

- **포트**: `8081`
- **스택**: Spring Boot 4.0.6 / Java 21 / MyBatis / Flyway / framework-security·openapi·file·commoncode
- **Swagger UI**: `http://localhost:8081/swagger-ui.html`
- **H2 콘솔(local)**: `http://localhost:8081/h2-console` (JDBC `jdbc:h2:mem:admindb`, user `sa`)

---

## 1. 빌드 (Build)

```bash
./gradlew :services:admin-service:build       # 컴파일 + 테스트 + assemble
./gradlew :services:admin-service:bootJar      # 실행 가능 jar (CI→Dockerfile JAR_FILE)
./gradlew :services:admin-service:compileJava  # 컴파일만
```
> 커밋 전 루트 `./gradlew spotlessApply`.

---

## 2. 테스트 (Test)

```bash
./gradlew :services:admin-service:test                     # 전체
./gradlew :services:admin-service:test --tests "*XxxTest"  # 특정
```
> 아키텍처 규칙: `./gradlew :framework:framework-archtest:test`.

---

## 3. 환경 설정 (Configuration)

환경 구분은 `local | dev | prod`(+ `local-xx` 오버레이). user-service(:8080)와 다른 포트라 동시 기동 가능.

| 프로파일 | DB / 저장소 | 용도 |
|---|---|---|
| `local` (기본) | H2 인메모리 `admindb` + /h2-console, audit=jdbc | 단독 기동·검증 |
| `local-postgres` | localhost:5432/sidb | 로컬 PostgreSQL |
| `local-redis` | + Redis | 토큰 store=redis |
| `local-noauth` | local 위 겹침 | 로그인/권한 우회(개발) |
| `dev` | env 주입(DB/Redis) | 개발 서버. token-store=redis, audit=jdbc |
| `prod` | env 주입(전부) | 운영. 모든 시크릿 env |

Flyway: `V1`(init) · `V2`(audit_log).

### 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | 활성 프로파일 |
| `DB_URL` | (프로파일별) | 데이터소스 URL |
| `JWT_SECRET` | (local placeholder) | 자체 JWT 서명 시크릿 |
| `TOKEN_STORE_TYPE` | `memory` | `memory`\|`jdbc`\|`redis`(다중 인스턴스는 redis) |
| `AES_SECRET` | (placeholder) | 마스터키(`ENC(...)` 설정 복호화 · 컬럼/파일 암호화) |
| `AUDIT_STORE_TYPE` | `jdbc`(local) / `logging`(prod) | 감사 로그 적재 |
| `REDIS_HOST`/`REDIS_PORT` | localhost/6379 | Redis 연결 |

---

## 4. 기동 (Run)

```bash
# 1) 기본 로컬 — H2 메모리, 감사로그 jdbc(H2 적재 → /h2-console 에서 검증)
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

---

## 5. 실행 확인 (Verify)

```bash
curl -s http://localhost:8081/actuator/health
# Swagger UI: http://localhost:8081/swagger-ui.html
# H2 콘솔: http://localhost:8081/h2-console (감사로그 jdbc 적재 확인)
```

---

## 6. 사용 (Usage)

RBAC 메타데이터 관리 — ADMIN 권한 토큰 필요(개발 중엔 `local-noauth` 로 우회).

```
GET/POST/PUT/DELETE  /api/v1/admin/menus            메뉴
GET/POST/PUT/DELETE  /api/v1/admin/resources        보호 리소스
POST/DELETE          /api/v1/admin/.../role-map     역할 매핑
```
전체 API 는 Swagger UI 에서 탐색.

---

## 7. 암호화 값 다루기 (Encrypted values)

> 상세/공통은 **[`docs/ENCRYPTION_GUIDE.md`](../../docs/reference/ENCRYPTION_GUIDE.md)**.

- **설정값(`ENC(...)`)** — DB 비번·`JWT_SECRET` 등 민감 설정을 yaml 평문 대신 `ENC(...)` 로(기동 시 자동 복호).
  ```bash
  AES_SECRET="$AES_SECRET" ./gradlew --no-daemon -q \
    :framework:framework-core:encryptSecret -Pplain='실제비밀값'
  # 출력 ENC(...) → application-*.yml 에 붙여넣기
  ```
- **마스터키 규칙**: `AES_SECRET` = `openssl rand -base64 32`, k8s Secret, **교체 금지**, prod 약한키면 부팅 차단(`AesMasterKeySafetyGuard`). 마스터키 자신은 `ENC(...)` 불가.

---

## 8. 컨테이너 / 배포

```bash
./gradlew :services:admin-service:bootJar
```
운영 다중 인스턴스는 `TOKEN_STORE_TYPE=redis` 권장. 시크릿은 k8s Secret 주입.

**Kustomize 멀티서비스 배포** (4개 서비스 + 인-클러스터 Redis 일괄):
```bash
kubectl apply -k deploy/k8s/overlays/dev     # 개발(약한 시크릿 동봉, 1 레플리카)
kubectl apply -k deploy/k8s/overlays/prod    # 운영(HPA·외부 DB/시크릿 전제 — ESO/SealedSecrets)
```
레이아웃·서비스별 env 계약·시크릿 주입·ServiceMonitor 는 `docs/modules/K8S_CICD_MULTISERVICE.md` 참고.

---

## 참고 문서
- 암호화 가이드: [`docs/ENCRYPTION_GUIDE.md`](../../docs/reference/ENCRYPTION_GUIDE.md)
- 로컬 설치: [`docs/LOCAL_SETUP.md`](../../docs/ops/LOCAL_SETUP.md)
- 모듈 카탈로그: [`docs/FRAMEWORK_MODULES.md`](../../docs/FRAMEWORK_MODULES.md)
