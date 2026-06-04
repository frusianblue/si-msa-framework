# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**그릇 정비 종료 — k8s/CI-CD 멀티서비스화(2026-06-04).** 배포 자산을 user-service 단일 → **4개 서비스 일괄**로 확장하고 **Kustomize base+overlay** 로 재구성(중복 제거). 선택 = **Redis 인-클러스터 + DB 외부 Secret**. 핵심: 서비스별 `deployment.yaml` 은 차이값(이미지/포트/envFrom)만, 공통 하드닝(비루트·readOnlyRootFS·프로브·리소스)은 **단일 패치**로 `hardened=true` Deployment 에 일괄 적용(**규약: 컨테이너 `app`·포트 `http`**). **단일 ServiceMonitor**(앱 4종, redis 제외). dev=1레플리카+약한시크릿 동봉, prod=HPA+외부 DB/issuer+Secret 미포함(ESO/SealedSecrets). CI=GH Actions verify(1회)→build matrix(4서비스)→deploy(`kubectl apply -k overlays/prod`+set image SHA), Jenkinsfile=게이트→Flyway(authdb/sidb)→Build matrix→Deploy. **멀티서비스가 실제 기동/관측되도록 서비스 소스 최소 보강 3건**: auth-server `application-prod.yml` 신설(prod datasource 공백→기동실패 해소)·auth-server management 블록(probes/prometheus)·4서비스 `micrometer-registry-prometheus`(runtimeOnly). **Python pyyaml 정적 Kustomize 에뮬레이션 검증기 56 checks 통과.** 받는 쪽 = `kubectl kustomize`/`apply -k --dry-run=server` + `:services:<svc>:bootJar`·`spotlessApply`.
**(이어서) 로컬 자기완결 테스트 + 환경 온보딩 정비** — `overlays/local`(인-클러스터 PG 동봉·SM 제외·`:local` 이미지)로 `kubectl apply -k deploy/k8s/overlays/local` 한 줄 검증 가능. 온보딩 문서 4종 신설: `DEV_ENV_WINDOWS`(개발 툴체인)·`LOCAL_K8S_ENV_SETUP`(Docker/kubectl/kind 크로스 OS)·`K8S_ADDONS`(서비스 구동용 클러스터 애드온: metrics-server·Prometheus·Ingress·시크릿 오퍼레이터)·`LOCAL_K8S_TEST`(kind 배포 절차). README 배포 섹션을 `apply -k overlays/<env>` 로 교정.

## 최종 갱신
- 일자: 2026-06-04 · 갱신자: k8s/CI-CD 멀티서비스화 + 로컬 테스트/온보딩 문서 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / Jackson 3(tools.jackson.*) · Kustomize · 4 services(gateway 8000 / auth-server 9000 / user 8080 / admin 8081)

## 직전에 한 것 (Done — 정적 검증 통과 / gradle·kubectl 은 받는 쪽 대기)
- **Kustomize 트리 신설** `deploy/k8s/` — 기존 flat 4파일(user-service/configmap/hpa/observability) 삭제, `base/{namespace, common/{deployment-hardening, servicemonitor}, redis, gateway, auth-server, user-service, admin-service}` + `overlays/{dev, prod}`. base 통합 kustomization 이 공통 패치를 `labelSelector: siframework.io/hardened=true` 로 적용.
- **공통 하드닝 패치**(`base/common/deployment-hardening.yaml`): pod securityContext(runAsNonRoot/uid1001/fsGroup1001/seccomp) + 컨테이너 `app`(allowPrivilegeEscalation=false·readOnlyRootFilesystem+/tmp emptyDir·capabilities drop ALL·resources req250m/512Mi lim1/1Gi·startup `/actuator/health` fail30·liveness/readiness `port: http`). redis 는 `component: cache`(라벨 없음)로 패치 제외.
- **인-클러스터 Redis**(`base/redis`): redis:7-alpine 비영속(`--save "" --appendonly no`)·비루트(uid999). 운영은 매니지드 권장(overlay 에서 REDIS_HOST 치환).
- **서비스별 base**(4종): deployment(차이값만)·service(port http·labels name/part-of/**component=service**)·configmap(SPRING_PROFILES_ACTIVE=prod·DB_URL·REDIS·issuer 등)·kustomization. Secret 은 base 미포함(의도).
- **overlays**: dev(replicas 1·images :dev·`secrets-dev.yaml` 약한 placeholder·DB_URL→dev 호스트 JSON6902) · prod(`hpa.yaml` 4종 CPU70%·images :latest(CI 가 SHA)·issuer→https://auth.example.com·DB_URL→prod 호스트·**Secret 미포함**, `secrets-prod.example.yaml` 양식만).
- **단일 ServiceMonitor**: selector `part-of=si-msa` AND `component=service`, endpoint `port: http` path `/actuator/prometheus` interval 15s, label `release: kube-prometheus-stack`.
- **CI 멀티서비스화**: `deploy/cicd/ci-cd.yml`(verify 1회 → build-and-push 4서비스 matrix, bootJar+docker 동일 잡, `JAR_FILE=services/<svc>/build/libs/<svc>-1.0.0.jar` → deploy `kubectl apply -k overlays/prod` + `set image app=<sha>` 루프) · `Jenkinsfile`(게이트 → Flyway Validate authdb/sidb 병렬 → Build&Push `SERVICE` matrix → Deploy kustomize, matrix 밖).
- **서비스 소스 보강 3건**: `services/auth-server/.../application-prod.yml` 신설(postgres datasource via env·flyway clean-disabled·health show-details never) · auth-server `application.yml` management 블록(probes.enabled + exposure health,info,prometheus,metrics) · `gateway/auth-server/user-service/admin-service` build.gradle 에 `runtimeOnly micrometer-registry-prometheus`.
- **문서**: `docs/modules/K8S_CICD_MULTISERVICE.md` 신설(레이아웃·env 계약표·redis/DB 전제·ServiceMonitor·갭 3건·CI matrix·시크릿 주입·검증 체크리스트) · 4개 서비스 README §8 에 Kustomize 배포 포인터 · `HANDOFF.md`(§6 함정 묶음·§7 완료+우선순위 마킹) · 이 문서.
- **(후속) `overlays/local` 추가 — kind/minikube 자기완결 로컬 테스트**: 인-클러스터 Postgres(`postgres.yaml`, authdb+sidb+역할 initdb, Service 이름 `postgres` → base DB_URL 무패치로 연결) + ServiceMonitor `$patch: delete`(로컬엔 Prometheus Operator CRD 없음) + 로컬 빌드 이미지 `:local`(IfNotPresent) + `secrets-local.yaml`(prod 프로파일 `AesMasterKeySafetyGuard` 통과용 강한 값, AES 정확히 32B; DB 계정은 initdb 역할과 일치). 한 줄 배포 `kubectl apply -k deploy/k8s/overlays/local`. kind 단계별 절차 `docs/modules/LOCAL_K8S_TEST.md`(클러스터 생성→4이미지 `kind load`→apply→port-forward 스모크→teardown, 관측은 kube-prometheus-stack 선택). 정적 검증: 역할/DB/시크릿 정합·SM delete 타깃·이미지 repo 일치 통과.
- **(후속) 환경 온보딩 문서 3종 + README 교정**: `docs/modules/DEV_ENV_WINDOWS.md`(Windows 개발 툴체인 — JDK21·IntelliJ+Lombok 어노테이션 처리·Docker; Gradle 8.14 래퍼=설치불요; local 프로파일=H2 무설치 기동)·`LOCAL_K8S_ENV_SETUP.md`(Docker/kubectl/kind macOS·Windows(WSL2)·Linux 설치 + 사내 프록시/폐쇄망 절·kind config 예시)·`K8S_ADDONS.md`(빈 클러스터에 서비스 구동용으로 추가 설치하는 것 — Helm·metrics-server[HPA, kind 는 `--kubelet-insecure-tls`]·kube-prometheus-stack[ServiceMonitor]·ingress-nginx[선택, gateway Ingress 리소스는 백로그]·ESO/SealedSecrets[prod 시크릿] + "무엇이 무엇을 요구하나" 매핑표). README "컨테이너/배포" 의 옛 `kubectl apply -f deploy/k8s/` → `apply -k overlays/{dev,prod,local}` 교정 + 4 문서 포인터 + 사전준비에 DEV_ENV_WINDOWS 링크. `K8S_CICD_MULTISERVICE.md`·`LOCAL_K8S_TEST.md`·`LOCAL_K8S_ENV_SETUP.md` 상호 포인터. HANDOFF §6 함정 묶음(local: base DB_URL=postgres 무패치·prod 프로파일 AES 가드·`$patch:delete`·metrics-server insecure-tls)·§7 완료 등록.

## 새로 확정한 함정 (HANDOFF §6 등록)
- **컨테이너 이름 `app` + 포트 이름 `http` 규약** — 공통 하드닝 패치(전략적머지)와 CI `set image deployment/<svc> app=…` 가 이 이름에 의존. 서비스별 deployment 는 차이값만.
- **redis 에 `hardened` 라벨 금지** — 패치가 컨테이너 `app` 을 머지하므로 redis 에 붙으면 엉뚱한 컨테이너 추가. `component: cache` 로 패치·SM 둘 다 제외.
- **auth-server prod 프로파일 부재 → datasource 공백 기동실패** — base 에 datasource 없음(profile 오버레이에서만) → `application-prod.yml` 신설(필수 env).
- **auth-server management 블록 부재 → probes/prometheus 노출 X → liveness/readiness 404** → base `application.yml` 보강.
- **prometheus 레지스트리 미보유 → /actuator/prometheus 404 → SM 빈 스크레이프** → 4서비스 runtimeOnly 추가(framework-core 는 actuator 만 api 전이).
- **base/prod 오버레이 Secret 미포함**(base=단독배포불가 의도, prod=ESO/SealedSecrets 사전주입). **Dockerfile=`JAR_FILE` build-arg**(기존 ci-cd 가 미사용 SERVICE 넘겨 깨져 있었음)·plain jar 비활성 안 됨→경로 명시·bootJar+docker 동일 잡.
- **작성환경**: dash 는 brace expansion 미지원(명시 경로) · GitHub 릴리스 바이너리 CDN(objects.githubusercontent.com) 차단 403 → kustomize/kubectl 설치 불가 → Python 정적 검증.

## 실행/검증 (받는 쪽 — kubectl/kustomize·gradle 가능 환경)
```bash
kubectl kustomize deploy/k8s/overlays/dev          # 렌더링 확인
kubectl kustomize deploy/k8s/overlays/prod
kubectl apply -k deploy/k8s/overlays/dev  --dry-run=server   # 스키마/어드미션
kubectl apply -k deploy/k8s/overlays/prod --dry-run=server
kubectl apply -k deploy/k8s/overlays/dev           # 실제 배포(dev)
kubectl -n si-msa get deploy,po,svc,hpa,servicemonitor
# 프로브/메트릭: port-forward 후 /actuator/health/{liveness,readiness}·/actuator/prometheus
./gradlew :services:auth-server:bootJar            # build/libs/auth-server-1.0.0.jar
./gradlew spotlessApply
```
> 작성환경은 Maven Central + 릴리스 CDN 차단으로 gradle/kustomize/kubectl 실행 불가 → 위는 받는 쪽. 작성환경에서는 **Python pyyaml 정적 Kustomize 에뮬레이션 검증기 56 checks 통과**(파일 참조·패치 타깃·컨테이너 `app`/포트 `http` 규약·SM 셀렉터 일치·image repo 일치).

## 다음 (Next) 후보
- **▶ commit/push** (이번 세션 누적 산출물 = `deploy/k8s/` Kustomize 트리 + `overlays/local`(PG/시크릿/kustomization) + `deploy/cicd/{ci-cd.yml,Jenkinsfile}` matrix + auth-server `application-prod.yml`/management + 4서비스 build.gradle prometheus + `docs/modules/{K8S_CICD_MULTISERVICE,LOCAL_K8S_TEST,LOCAL_K8S_ENV_SETUP,K8S_ADDONS,DEV_ENV_WINDOWS}.md` + README 배포 섹션 교정 + HANDOFF/HANDOFF_SUMMARY). 받는 쪽 `kubectl kustomize overlays/{dev,prod,local}`/`--dry-run=server` + `:services:<svc>:bootJar`·`spotlessApply` + (선택) kind 로컬 1회 기동 확인 후.
- (devops 후속) gateway 외부 노출(Ingress/LoadBalancer + TLS)·NetworkPolicy(백엔드 인그레스 제한)·prod redis 매니지드 전환·PodDisruptionBudget·레이트리밋 429 Testcontainers Redis 통합테스트. (로컬 검증 환경 = `overlays/local` + `docs/modules/LOCAL_K8S_TEST.md` 로 마련됨.)
- (보류) OIDC B안 전체 흐름 e2e(confidential demo-rp) · 게이트웨이 AS aud 검증 · 서명키 KMS/Vault 백엔드 · SAML 6.2-B SP-initiated SLO · 6.4 Passwordless(WebAuthn).
- (선택 백로그) 아카이빙 tar/tar.gz(commons-compress) · RetryUtils · 규제특화 잔여(pki/hsm/recon/egov) · saga 단계별 타임아웃/보상 재시도 · S3 멀티파트 병렬 업로드(TransferManager).
<!-- 갱신 끝 -->
