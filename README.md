# SI MSA Common Framework

Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 (Oakwood) / Jackson 3 · MyBatis · PostgreSQL(로컬 H2)
한국 SI(금융 우선·공공) MSA 프로젝트의 드롭인 공통 기반. K8s/CI-CD/DevOps 배포 전제.

> **문서는 [`docs/00_INDEX.md`](docs/00_INDEX.md) 에서 시작하세요.** 역할별(개발자·오너·운영) 진입점이 모두 거기에 있습니다.

---

## 한눈에

| 무엇을 | 어디로 |
|---|---|
| 5분 안에 띄우기 | 아래 [처음 실행하기](#처음-실행하기) |
| 어떤 모듈을 켤지 | [`docs/guide/MODULE_COMPOSITION.md`](docs/guide/MODULE_COMPOSITION.md) |
| 전 모듈 카탈로그·의존·구축순서 | [`docs/FRAMEWORK_MODULES.md`](docs/FRAMEWORK_MODULES.md) |
| 스택/버전 핀 | [`docs/reference/STACK.md`](docs/reference/STACK.md) |
| 배포/운영(k8s·CI/CD) | [`docs/ops/`](docs/ops/) |

## 모듈 구조 (요약)

```
framework/   36개 공통 모듈 (core·mybatis·security 는 [코어] 항상 탑재, 나머지는 선택)
services/    gateway(8000) · auth-server(9000, OP) · user-service(8080) · admin-service(8081)
deploy/      docker/ · k8s/(Kustomize base+overlay) · cicd/
docs/        문서 (00_INDEX.md 진입점)
```
모듈별 기능·전제·연결·토글은 [`docs/guide/MODULE_COMPOSITION.md`](docs/guide/MODULE_COMPOSITION.md) 한 장에 정리되어 있다.
각 서비스 기동법은 서비스별 README 참조: [gateway](services/gateway/README.md) · [auth-server](services/auth-server/README.md) · [user-service](services/user-service/README.md) · [admin-service](services/admin-service/README.md).

## 핵심 설계

- 각 서비스는 `framework-*` 의존성만 추가하면 표준 응답/예외/보안/MyBatis 가 **자동 적용**된다(Boot auto-configuration `.imports`).
- 모든 응답은 `ApiResponse<T>` 표준 포맷, 예외는 `GlobalExceptionHandler` 가 통일 변환.
- 요청마다 `traceId`(MDC) 부여 → 로그 패턴/응답 헤더 노출(MSA 추적).
- 무상태 인증 `Authorization: Bearer <JWT>` · DB 기반 RBAC 동적 인가.
- 모듈 토글 3단: 의존성 추가 → `framework.<name>.enabled` → 구현 선택. 상세 [`docs/guide/MODULE_COMPOSITION.md`](docs/guide/MODULE_COMPOSITION.md).

---

## 처음 실행하기

**사전 준비**: JDK 21 만 있으면 된다. 로컬은 H2 인메모리 + Flyway 자동 마이그레이션/시드라 외부 인프라(DB·Redis) 불필요. Docker 는 통합테스트(Testcontainers)·이미지 빌드 때만. (Windows 개발 툴체인은 [`docs/ops/DEV_ENV_WINDOWS.md`](docs/ops/DEV_ENV_WINDOWS.md).)

```bash
# 빌드 (gradlew 동봉 — 별도 gradle 설치 불필요)
./gradlew spotlessApply        # 포맷 정렬(최초/포맷 변경 시)
./gradlew clean build          # 컴파일 + 테스트 + 커버리지   (테스트 생략: build -x test)
```

```bash
# 로컬 기동: 기본 프로파일 = local (H2 인메모리+시드). 컨테이너는 SPRING_PROFILES_ACTIVE 로 override.
./gradlew :services:user-service:bootRun     # → :8080
./gradlew :services:admin-service:bootRun    # → :8081 (다른 터미널)

# local-xx 오버레이 (local 위에 겹쳐 사용)
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local,local-postgres'        # 로컬 PostgreSQL
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local,local-postgres,local-redis'  # +Redis
./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local,local-noauth'           # 로그인/권한 우회(개발)
# 환경 구분은 local / dev / prod. 로컬 설치·검증 상세는 docs/ops/LOCAL_SETUP.md.
```

> **설정값 암호화**: yaml 의 시크릿을 `ENC(...)` 로 두면 기동 시 자동 복호화. 마스터키 = `framework.crypto.aes-secret`(운영 `AES_SECRET`, 항상 평문 주입). 상세 [`docs/reference/ENCRYPTION_GUIDE.md`](docs/reference/ENCRYPTION_GUIDE.md).

```bash
# 동작 확인 — 시드 계정: admin/admin123(ADMIN), hong/hong123(USER)
curl -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
     -d '{"loginId":"admin","password":"admin123"}'
# 같은 loginId 로 5회 실패 시 6번째부터 429(LOGIN_LOCKED)

# 회원가입: password 강도 정책(min-length 9, 3종 이상) 충족해야 201
curl -X POST localhost:8080/api/v1/users -H 'Content-Type: application/json' \
     -d '{"loginId":"kim","password":"Passw0rd!","name":"김","email":"kim@test.com","phone":"010-1234-5678"}'
```

> **게이트웨이(:8000) 로컬 주의**: 라우트가 `lb://user-service` 인데 디스커버리 의존성이 없어 로컬에선 `lb://` 미해석. 로컬은 8080/8081 직접 호출, k8s 에선 `http://user-service:8080` 직접 URI 로 전환.

## 컨테이너 / 배포

```bash
# 런타임 전용 Dockerfile: jar 는 CI 가 먼저 빌드 → JAR_FILE 로 주입
./gradlew :services:user-service:bootJar
docker build -f deploy/docker/Dockerfile \
  --build-arg JAR_FILE=services/user-service/build/libs/user-service-1.0.0.jar -t user-service .

# k8s 배포는 Kustomize 오버레이로 (4개 서비스 일괄)
kubectl apply -k deploy/k8s/overlays/dev      # 개발(약한 시크릿 동봉, 1 레플리카)
kubectl apply -k deploy/k8s/overlays/prod     # 운영(HPA·외부 DB/시크릿 — ESO/SealedSecrets)
kubectl apply -k deploy/k8s/overlays/local    # 로컬 자기완결(kind: 인-클러스터 PG)
```

> 빌드·테스트·게이트·이미지(4서비스 matrix)·롤아웃은 `deploy/cicd/{ci-cd.yml,Jenkinsfile}`.
> **다중 인스턴스 주의**: `replicas≥2` 면 로그인 잠금/토큰 공유 필요 → 운영 프로파일에서 `framework.security.login-attempt.type=redis` · `token-store.type=redis`.

**환경 구성·배포 문서** — 모두 [`docs/ops/`](docs/ops/): DEV_ENV_WINDOWS · LOCAL_SETUP · LOCAL_K8S_ENV_SETUP · LOCAL_K8S_TEST · K8S_ADDONS · K8S_CICD_MULTISERVICE.

---

## 빌드 / 품질

Spotless(Palantir) · JaCoCo · ArchUnit(`framework-archtest`: 모듈 순환·Jackson3 규약·레이어·네이밍·생성자주입 강제) · WireMock · OWASP Dependency-Check · SonarQube. 상세 [`docs/reference/STACK.md`](docs/reference/STACK.md) · [`docs/FRAMEWORK_MODULES.md`](docs/FRAMEWORK_MODULES.md).

> 작업/세션 인수인계 기록(HANDOFF 등)은 [`docs/_internal/`](docs/_internal/) — 프레임워크 사용과 무관.
