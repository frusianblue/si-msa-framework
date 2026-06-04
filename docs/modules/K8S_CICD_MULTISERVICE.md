# k8s / CI-CD 멀티서비스 (Kustomize)

> 작성 2026-06-04. 배포 자산을 user-service 단일 → **4개 서비스 일괄**로 확장하고,
> 중복 제거를 위해 **Kustomize base+overlay** 로 재구성한 기록. 전제는
> **Redis 인-클러스터 + DB 외부 Secret**. Dockerfile 의미는 `deploy/docker/Dockerfile`,
> 서비스별 설정은 각 `services/*/README.md` 참고.

---

## 1. 레이아웃

```
deploy/k8s/
  base/
    namespace.yaml                         # si-msa 네임스페이스
    common/
      deployment-hardening.yaml            # 전 서비스 Deployment 공통 패치(보안/프로브/리소스/tmp)
      servicemonitor.yaml                  # 단일 ServiceMonitor(앱 4종만 스크레이프)
    redis/{redis.yaml,kustomization.yaml}  # 인-클러스터 Redis(비영속)
    gateway/        {deployment,service,configmap,kustomization}.yaml
    auth-server/    {deployment,service,configmap,kustomization}.yaml
    user-service/   {deployment,service,configmap,kustomization}.yaml
    admin-service/  {deployment,service,configmap,kustomization}.yaml
    kustomization.yaml                     # 위를 묶고 hardened 패치를 label 타깃으로 적용
  overlays/
    local/ {kustomization.yaml, postgres.yaml, secrets-local.yaml}  # kind/minikube 자기완결: 인-클러스터 PG, SM 제외, :local
    dev/   {kustomization.yaml, secrets-dev.yaml}              # 1 레플리카, :dev, 약한 시크릿 동봉
    prod/  {kustomization.yaml, hpa.yaml, secrets-prod.example.yaml}  # HPA, 외부 DB·issuer, 시크릿 미포함
```

> **로컬 자기완결 테스트**는 `overlays/local`(인-클러스터 Postgres 동봉·ServiceMonitor 제거·로컬 빌드 이미지)로 `kubectl apply -k deploy/k8s/overlays/local` 한 줄. kind 기준 단계별 절차는 `docs/modules/LOCAL_K8S_TEST.md`.

**중복 제거의 핵심** — 서비스별 `deployment.yaml` 은 *차이값만*(이미지/포트번호/envFrom) 선언하고,
보안 컨텍스트·프로브·리소스·`/tmp` 볼륨은 `common/deployment-hardening.yaml` 한 곳에서 정의해
`siframework.io/hardened: "true"` 라벨을 가진 Deployment 전부에 일괄 패치한다.

### 공통 규약 (패치가 성립하는 전제)
- **메인 컨테이너 이름 = `app`** (전 서비스 동일). 공통 패치가 컨테이너 `app` 에 프로브/리소스/보안을 머지하고, CI 의 `kubectl set image deployment/<svc> app=<image>` 도 이 이름으로 동작한다.
- **HTTP 포트 이름 = `http`** (번호는 서비스마다 다름: gateway 8000 / user 8080 / admin 8081 / auth 9000). 프로브·Service·ServiceMonitor 가 전부 포트 *이름* `http` 로 참조 → 번호 차이 무관.

---

## 2. 서비스별 env 계약

ConfigMap(`<svc>-config`, 비밀 아님) + Secret(`<svc>-secret`, 비밀)으로 `envFrom` 주입. base 의 ConfigMap 은 인-클러스터 기본값이며 overlay 가 환경별 값(DB 호스트·공개 issuer)을 치환한다.

| 서비스 | 포트 | ConfigMap 주요 키 | Secret 키 | Redis | DB |
|---|---|---|---|---|---|
| gateway | 8000 | REDIS_HOST/PORT, GATEWAY_AUTH_ENABLED=true, AUTH_SERVER_ISSUER, AUTH_SERVER_JWKS_URI | JWT_SECRET | ✅(reactive) | — |
| auth-server | 9000 | SPRING_PROFILES_ACTIVE=prod, AUTH_SERVER_ISSUER, DB_URL(authdb), LOCK_TYPE=jdbc, SIGNING_KEY_ROTATION_ENABLED=true | DB_USER, DB_PASSWORD, FRAMEWORK_JWT_SECRET, AES_SECRET | — | authdb |
| user-service | 8080 | SPRING_PROFILES_ACTIVE=prod, REDIS_HOST/PORT, DB_URL(sidb), AUTH_SERVER_ISSUER/JWKS_URI, FILE_STORAGE_TYPE=s3, TRACE_SAMPLING | DB_USER, DB_PASSWORD, JWT_SECRET, AES_SECRET | ✅ | sidb |
| admin-service | 8081 | SPRING_PROFILES_ACTIVE=prod, REDIS_HOST/PORT, DB_URL(sidb), TRACE_SAMPLING | DB_USER, DB_PASSWORD, JWT_SECRET, AES_SECRET | ✅ | sidb |

> **issuer 주의**: `AUTH_SERVER_ISSUER` 는 토큰 `iss`(외부 공개 안정 URL)와 일치해야 gateway/RS 검증을 통과한다. JWKS 조회만 클러스터 내부(`http://auth-server:9000/oauth2/jwks`)로 단축할 수 있다. prod 오버레이가 issuer 를 `https://auth.example.com` 으로 치환한다.

---

## 3. Redis 인-클러스터 + DB 외부 Secret

- **Redis**: `base/redis` 의 `redis:7-alpine`, 비영속(`--save "" --appendonly no`), 비루트(uid 999). 레이트리밋 토큰버킷 + user/admin 토큰스토어/캐시용. 휘발 데이터라 emptyDir 로 충분. **운영은 매니지드(ElastiCache 등) 권장** — overlay 에서 `REDIS_HOST` 를 외부 엔드포인트로 치환하고 `base/redis` 를 빼면 된다.
- **DB**: 클러스터 밖(외부 PostgreSQL) 전제. 매니페스트는 DB 를 띄우지 않고 `DB_URL`(ConfigMap) + `DB_USER`/`DB_PASSWORD`(Secret) 로만 접속한다.

---

## 4. 관측 (ServiceMonitor)

`base/common/servicemonitor.yaml` 단일 ServiceMonitor 가 `part-of=si-msa` **AND** `component=service` 인 Service(=앱 4종, redis 제외)를 포트 이름 `http`, 경로 `/actuator/prometheus`, 15s 간격으로 스크레이프한다.

전제: ① kube-prometheus-stack(Prometheus Operator) 설치 → CRD 존재, ② Operator 의 `serviceMonitorSelector` 가 SM 의 `release: kube-prometheus-stack` 라벨을 고름(설치값에 맞게 조정), ③ 각 서비스가 `/actuator/prometheus` 를 노출(아래 §6 참고).

---

## 5. CI-CD (멀티서비스)

게이트(아키텍처/전 모듈 테스트/커버리지/OWASP/Sonar)는 **레포 전체 1회**, 이미지 빌드는 **서비스 매트릭스**, 배포는 **kustomize 1회**.

**GitHub Actions** (`deploy/cicd/ci-cd.yml`)
- `verify`(1회) → `build-and-push`(matrix: 4서비스 병렬, bootJar+docker 같은 잡) → `deploy`(kustomize prod + set image SHA).
- 이미지 빌드는 `JAR_FILE=services/<svc>/build/libs/<svc>-1.0.0.jar` 를 Dockerfile 에 넘긴다(plain jar 비활성 안 했으므로 boot jar 경로 명시).

**Jenkins** (`deploy/cicd/Jenkinsfile`)
- 게이트 스테이지 → `Flyway Validate`(authdb/sidb 병렬) → `Build & Push Images`(matrix `SERVICE` axis) → `Deploy to K8s`(matrix 밖 1회).

**배포 명령**
```bash
kubectl apply -k deploy/k8s/overlays/dev      # 개발
kubectl apply -k deploy/k8s/overlays/prod     # 운영
# 이미지 SHA 고정(CI 가 수행):
kubectl -n si-msa set image deployment/<svc> app=registry.example.com/si-msa/<svc>:<sha>
```

---

## 6. 멀티서비스 배포를 위해 메운 서비스-소스 갭

배포 자산만으로는 **기동/관측이 실제로 안 되는** 공백 3건을 user/admin 기존 패턴대로 메웠다.

1. **auth-server `application-prod.yml` 신설** — auth-server 는 prod 프로파일이 없어 `SPRING_PROFILES_ACTIVE=prod` 시 datasource 가 비어 기동 실패했다(base `application.yml` 에 datasource 블록 없음 — 로컬은 `application-local*` 오버레이에서만 옴). PostgreSQL datasource(`${DB_URL}/${DB_USER}/${DB_PASSWORD}`, 기본값 없음=필수) + 운영 하드닝(flyway clean-disabled, health show-details never, sampling 0.1) 추가.
2. **auth-server `application.yml` 에 `management` 블록 추가** — probes 그룹/prometheus 노출이 없어 k8s liveness/readiness 프로브가 404 로 실패했다. user/admin/gateway 와 동일하게 `endpoint.health.probes.enabled=true` + `exposure.include: health,info,prometheus,metrics` 추가.
3. **4개 서비스 build.gradle 에 `micrometer-registry-prometheus`(runtimeOnly)** — 어느 서비스도 레지스트리가 없어 `/actuator/prometheus` 가 404 → ServiceMonitor 가 빈 스크레이프였다. 버전은 Boot BOM 관리.

---

## 7. 시크릿 주입

- **dev**: `overlays/dev/secrets-dev.yaml` 에 **약한 placeholder** 시크릿 4개 동봉(편의용, 커밋됨). 운영 금지.
- **prod**: 오버레이에 **Secret 미포함**. `<svc>-secret` 4개를 **External Secrets Operator**(AWS Secrets Manager/Vault 동기화) 또는 **Sealed Secrets**(kubeseal 암호화본만 커밋)로 si-msa 네임스페이스에 사전 주입한다. 양식은 `overlays/prod/secrets-prod.example.yaml`(배포 안 됨) 참고. 미주입 시 파드가 기동 못 한다(의도된 fail-fast).

---

## 8. 검증 체크리스트 (받는 쪽 — kubectl/kustomize 가능 환경)

```bash
# 1) 렌더링 검증(적용 없이 매니페스트 확인)
kubectl kustomize deploy/k8s/overlays/dev
kubectl kustomize deploy/k8s/overlays/prod

# 2) 서버측 dry-run(스키마/어드미션 검증, 실제 변경 X)
kubectl apply -k deploy/k8s/overlays/dev  --dry-run=server
kubectl apply -k deploy/k8s/overlays/prod --dry-run=server

# 3) 실제 배포(dev)
kubectl apply -k deploy/k8s/overlays/dev
kubectl -n si-msa get deploy,po,svc,hpa,servicemonitor

# 4) 프로브/메트릭 노출 확인(파드 내부 또는 port-forward)
kubectl -n si-msa port-forward deploy/user-service 8080:8080
curl localhost:8080/actuator/health/liveness   # UP
curl localhost:8080/actuator/health/readiness  # UP
curl localhost:8080/actuator/prometheus | head # 메트릭 출력(404 면 registry 누락)

# 5) 빌드(이미지 산출물 경로 확인)
./gradlew :services:auth-server:bootJar         # build/libs/auth-server-1.0.0.jar
./gradlew spotlessApply
```

> 작성환경은 Maven Central 차단으로 gradle 실행 불가 + 릴리스 바이너리 CDN 차단으로 kustomize/kubectl 설치 불가 → 위 명령은 받는 쪽에서 실행. 작성환경에서는 **Python 정적 Kustomize 에뮬레이션 검증기**로 구조 정합성(파일 참조·패치 타깃·컨테이너 `app`/포트 `http` 규약·SM 셀렉터 일치·image repo 일치)을 확인했다(56 checks pass).
