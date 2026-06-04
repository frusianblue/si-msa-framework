# user-service (사용자/인증 API)

회원·인증·비밀번호·파일 업로드 등 사용자 도메인 REST API. framework-security(JWT/RBAC/비번정책)·openapi·file·commoncode 를 조합한 표준 업무 서비스다. 서블릿 + MyBatis.

- **포트**: `8080`
- **스택**: Spring Boot 4.0.6 / Java 21 / MyBatis / Flyway / framework-security·openapi·file·commoncode
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`

---

## 1. 빌드 (Build)

```bash
./gradlew :services:user-service:build      # 컴파일 + 테스트 + assemble
./gradlew :services:user-service:bootJar     # 실행 가능 jar (CI→Dockerfile JAR_FILE)
./gradlew :services:user-service:compileJava # 컴파일만
```
> 커밋 전 루트 `./gradlew spotlessApply`.

---

## 2. 테스트 (Test)

```bash
./gradlew :services:user-service:test                       # 전체
./gradlew :services:user-service:test --tests "*XxxTest"    # 특정
```
> 다운스트림 zero-trust/이중 발급기 검증기 자체의 단위 테스트는 프레임워크 레벨: `./gradlew :framework:framework-security:test --tests "*DownstreamDualIssuerTest"`.
> 아키텍처 규칙: `./gradlew :framework:framework-archtest:test`.

---

## 3. 환경 설정 (Configuration)

환경 구분은 `local | dev | prod`. 특수 조합은 `local` 위에 겹치는 `local-xx` 오버레이.

| 프로파일 | DB / 저장소 | 용도 |
|---|---|---|
| `local` (기본) | H2 인메모리 + 시드, token-store=memory | 단독 기동·기능 검증 |
| `local-postgres` | localhost:5432/sidb | 로컬 PostgreSQL |
| `local-redis` | + Redis | 토큰/블랙리스트/캐시 공유(다중 인스턴스 모사) |
| `local-noauth` | local 위 겹침 | 로그인/권한 우회(개발) |
| `dev` | env 주입(DB/Redis) | 개발 서버. token-store/audit=redis/jdbc 기본 |
| `prod` | env 주입(전부) | 운영. 모든 시크릿 env |

Flyway: `V1`(init) · `V2`(common_code) · `V3`(file_metadata) · `V4`(audit_log).

### 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | 활성 프로파일 |
| `DB_URL` | (프로파일별) | 데이터소스 URL |
| `JWT_SECRET` | (local placeholder) | 자체 JWT 서명 시크릿(**게이트웨이와 동일 값 공유**) |
| `TOKEN_STORE_TYPE` | `memory` | `memory`\|`jdbc`\|`redis`. **다중 인스턴스는 redis 필수** |
| `EDGE_TRUST_MODE` | `zero-trust` | `zero-trust`(Bearer 재검증·안전 기본) \| `gateway-headers`(헤더 신뢰·격리 환경 한정) |
| `RESOURCE_SERVER_ENABLED` | `false` | AS(OP) RS256/JWKS 토큰 재검증 활성(이중 발급기 다운스트림) |
| `AUTH_SERVER_ISSUER` | (빈값) | AS issuer(이중 발급기 재검증 시 필수) |
| `AUTH_SERVER_JWKS_URI` | (빈값) | 생략 시 `{issuer}/oauth2/jwks` |
| `AES_SECRET` | (placeholder) | 마스터키(파일 at-rest 암호화 · `ENC(...)` 설정 복호화) |
| `AUDIT_STORE_TYPE` | `logging` | 감사 로그 적재(`logging`\|`jdbc`) |
| `FILE_STORAGE_TYPE` | `local` | `local`\|`nas`\|`s3`(s3 는 framework-file-s3 필요) |
| `REDIS_HOST`/`REDIS_PORT` | localhost/6379 | Redis 연결 |

> **다중 인스턴스 주의**: k8s `replicas: 2` 기준 — `TOKEN_STORE_TYPE=redis` + `framework.security.login-attempt.type=redis` 를 켜야 토큰/잠금이 공유된다(기본 `memory` 는 인스턴스별 → 잠금 우회 가능).

---

## 4. 기동 (Run)

```bash
# 1) 기본 로컬 — H2 메모리 + 시드 계정, 인증 on
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

---

## 5. 실행 확인 (Verify)

```bash
# 헬스
curl -s http://localhost:8080/actuator/health

# 시드 계정 로그인: admin/admin123 (ADMIN), hong/hong123 (USER)
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"loginId":"admin","password":"admin123"}'
# 같은 loginId 로 5회 실패 → 6번째부터 429(LOGIN_LOCKED)
```

---

## 6. 사용 (Usage)

- Swagger UI: `http://localhost:8080/swagger-ui.html` (전체 API 탐색)
- 대표 흐름:
  - `POST /api/v1/auth/login` → access/refresh 토큰
  - `PATCH /api/v1/users/me/password` (현재 비번 필요) / `PATCH /api/v1/users/{id}/password/reset` (ADMIN 토큰, 비ADMIN 은 403)
- **신뢰 자세**: 게이트웨이 뒤(K8s+NetworkPolicy)면 `EDGE_TRUST_MODE=gateway-headers`(저렴), VM 등 격리 약하면 기본 `zero-trust`(Bearer 로컬 재검증). AS 위임 토큰도 받으면 `RESOURCE_SERVER_ENABLED=true`+`AUTH_SERVER_ISSUER`.

---

## 7. 암호화 값 다루기 (Encrypted values)

> 상세/공통은 **[`docs/ENCRYPTION_GUIDE.md`](../../docs/ENCRYPTION_GUIDE.md)**.

이 서비스가 쓰는 암호화:
- **설정값(`ENC(...)`)** — `JWT_SECRET`·DB 비번 등 민감 설정을 yaml 에 평문으로 두지 않으려면 `ENC(...)` 로 넣는다(기동 시 자동 복호).
  ```bash
  AES_SECRET="$AES_SECRET" ./gradlew --no-daemon -q \
    :framework:framework-core:encryptSecret -Pplain='실제비밀값'
  # 출력 ENC(...) → application-prod.yml 등에 붙여넣기
  ```
  ```yaml
  framework:
    security:
      jwt:
        secret: ENC(Qk9k...)   # 기동 시 자동 복호
  ```
- **파일 at-rest** — `FILE_STORAGE_TYPE` 저장소에서 파일 본문 암호화가 필요하면 `framework.file.*` 설정과 `AES_SECRET` 사용(AES-CBC 스트림).
- **마스터키 규칙**: `AES_SECRET` = `openssl rand -base64 32`, k8s Secret, **교체 금지**, prod 약한키면 부팅 차단.

> ⚠️ 마스터키 자신(`AES_SECRET`)은 `ENC(...)` 로 둘 수 없다(평문/시크릿 주입). `ENC(...)`(설정) 와 `enc:`(컬럼) 마커는 별개.

---

## 8. 컨테이너 / 배포

```bash
./gradlew :services:user-service:bootJar
```
운영 다중 인스턴스는 `TOKEN_STORE_TYPE=redis` 필수. 시크릿(`JWT_SECRET`/`AES_SECRET`/`DB_URL`)은 k8s Secret 으로 주입.

**Kustomize 멀티서비스 배포** (4개 서비스 + 인-클러스터 Redis 일괄):
```bash
kubectl apply -k deploy/k8s/overlays/dev     # 개발(약한 시크릿 동봉, 1 레플리카)
kubectl apply -k deploy/k8s/overlays/prod    # 운영(HPA·외부 DB/시크릿 전제 — ESO/SealedSecrets)
```
레이아웃·서비스별 env 계약·시크릿 주입·ServiceMonitor 는 `docs/modules/K8S_CICD_MULTISERVICE.md` 참고.

---

## 참고 문서
- 암호화 가이드: [`docs/ENCRYPTION_GUIDE.md`](../../docs/ENCRYPTION_GUIDE.md)
- 토큰 검증/배치 환경(K8s vs VM): [`docs/TOKEN_VERIFICATION_GUIDE.md`](../../docs/TOKEN_VERIFICATION_GUIDE.md)
- 로컬 설치: [`docs/LOCAL_SETUP.md`](../../docs/LOCAL_SETUP.md)
- 모듈 카탈로그: [`docs/FRAMEWORK_MODULES.md`](../../docs/FRAMEWORK_MODULES.md)
