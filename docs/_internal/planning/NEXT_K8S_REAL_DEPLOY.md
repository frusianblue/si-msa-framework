# NEXT_K8S_REAL_DEPLOY.md — 현재 환경에서 운영 토폴로지 전체 리허설 (2026-06-06 락, 세션2 진행반영)

> 목표: **Docker Desktop kind(`docker-desktop` 컨텍스트) 단일 환경에서 운영(prod) 경로 전부 리허설.** 외부 인프라(레지스트리/DB/TLS/시크릿)는 로컬 대역으로 끼운다. S3 객체스토리지는 범위 제외(나머지 검증 후).
> 제약: 작성 환경 빌드/`kubectl`/`helm` 직접 실행 불가 → 매니페스트 정적검증만, 실행/트리아지는 받는 쪽.
> 노출 정정: 이 환경은 **NodePort 호스트 비노출 / LoadBalancer 만 localhost 매핑** → Harbor 는 ingress 경유(`ops/K8S_INGRESS_HARBOR.md`). `ops/HARBOR_SETUP.md` 의 NodePort 전제는 폐기.

---

## ⭐ 진행 현황 (2026-06-06 세션2)

| 단계 | 상태 | 메모 |
|---|---|---|
| **S1 영속 postgres(PVC)** | ✅ **완료** | `components/postgres-persistent`(StatefulSet) apply → `data-postgres-0` **Bound**(5Gi/RWO/standard), `postgres-0` Running. Service 는 headless→**ClusterIP** 정정(clusterIP 불변 충돌 해소). |
| **S2 Harbor + push** | ✅ **완료(ingress 경로)** | ingress-nginx(LoadBalancer, EXTERNAL-IP=localhost) + Harbor `expose.type=ingress`(`harbor.local`) + Windows/WSL hosts + insecure-registries. `si-msa` 프로젝트에 4서비스 × (`7e935d6`,`dev`) push 확인(포털). |
| **S3 dev overlay apply** | ◐ **매니페스트 준비완료(이 드롭) · apply 대기** | `overlays/dev` 이미지 핀(`harbor.local/si-msa/<svc>:7e935d6`)+DB→인-클러스터 `postgres:5432`(auth/sidb/**admindb**)+파일저장 `/tmp/uploads`+`pull-secret-dev.yaml`(harbor-cred+default SA) 까지 매니페스트 완성·정적검증 통과. 남음=받는 쪽 `apply -k`+**pull 트리아지**(노드 containerd)+AS 토큰 스모크. |
| S4 애드온 | 대기 | metrics-server/HPA → kube-prometheus-stack → (ingress-nginx 는 S2 에서 선설치됨). |
| S5 prod-rehearsal overlay | 대기 | prod 토폴로지 + 로컬 대역. |
| S6 상위 흐름 스모크 | 대기 | OIDC RP·이중 발급기 우선. |
| S7 Jenkins | 대기 | 자동화 마지막. |

> 세션2 함정(되돌리지 말 것): ① 이 환경 NodePort 는 호스트/데몬 비노출, **port-forward 는 127.0.0.1 만 바인딩**해 Docker Desktop **VM 데몬이 못 봐서** `docker login` timeout → **LoadBalancer(ingress)** 가 정답(데몬 도달). ② Docker Desktop 데몬은 **Windows hosts** 를 참조 → `harbor.local` 은 Windows hosts 에 필수(WSL hosts 는 호스트 curl 용). ③ Service clusterIP 는 불변 → 기존 ClusterIP svc 를 headless 로 바꾸려다 거부됨(S1 정정).

## 0. 확정 결정 (락)

| # | 항목 | 결정 |
|---|---|---|
| 1 | 이미지 태그 | 양태그(`:<git-sha>` 핀 + 채널 `:dev`/semver). 매니페스트는 `kustomize edit set image` 로 SHA 핀. 둘 다 굴려보고 최종확정 |
| 2 | 이미지 서버 | **Harbor**(ingress 노출, `harbor.local`). push 데몬 도달=LoadBalancer 경로 |
| 3 | 영속 | 기본 StorageClass(`standard`, local-path) 동적 프로비저닝 PVC. postgres=StatefulSet, redis=휘발(설계). 객체스토리지(S3) 제외 |
| 4 | overlay | dev 먼저 → prod-rehearsal. dev≠prod(§1) |
| 5 | CI/CD | Jenkins 는 마지막 |

## 1. dev vs prod 차이 (요약)
dev=1레플리카·약한시크릿·port-forward / prod=HPA(metrics-server)·외부시크릿(ESO)·공개issuer·TLS ingress. dev 그린이 prod 보장 안 함 — 애드온 작업이 prod 추가분 검증과 겹침.

## 2. 외부 의존물 → 로컬 대역
레지스트리→Harbor(`harbor.local`) · 외부 DB→인-클러스터 영속 postgres(`components/postgres-persistent`, DB_URL=`postgres:5432`) · 외부 Redis→인-클러스터(base) · 실TLS→self-signed/`*.local` · ESO→평문 Secret.

## 3. 런북 (S3 부터 상세)

### ▶ S3 — dev overlay 실 apply (다음 섹션 시작점)
**0) 재부팅 검증(섹션 시작 시 1회)** — Docker Desktop 껐다 켠 뒤 ingress/LB·Harbor·postgres PVC 가 자동 복귀하는지(`kubectl get svc -n ingress-nginx` EXTERNAL-IP=localhost, `harbor.local` 포털, `data-postgres-0` Bound). = port-forward 졸업 확인.

**1) dev overlay 이미지 핀 + DB/파일저장 패치 — ✅ 이 드롭에서 매니페스트 완성(정적검증 통과)**
- `overlays/dev/kustomization.yaml`:
  - `images`: `newName: harbor.local/si-msa/<svc>` + `newTag: 7e935d6` ×4. (CI 갱신은 `kustomize edit set image registry.example.com/si-msa/<svc>=harbor.local/si-msa/<svc>:<sha>`.)
  - DB_URL 패치: `dev-postgres.internal:5432` → **`postgres:5432`**(S1 인-클러스터 영속). auth=authdb, user=sidb, **admin=admindb**(← 기존 overlay 는 `sidb` 였음 = user 와 같은 siuser·sidb 에서 Flyway V1 충돌 잠재버그. local overlay·initdb 와 동일하게 admindb 로 정정).
  - 파일저장 패치(local overlay 미러, **이 환경 필수**): user → `FILE_STORAGE_TYPE=local`+`FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads`, admin → `/tmp/uploads`. (이 환경엔 S3 없음 + `readOnlyRootFilesystem` → 안 하면 user/admin 부팅 깨짐. PITFALLS §9.)
  - `resources` 에 `../../components/postgres-persistent` 추가.
- ⚠️ **이미지 핀 7e935d6 ≠ 소스 HEAD(5d2dc2c)**: 사이 2커밋은 **문서만**(HANDOFF/NEXT/HARBOR docs) — 앱동작 동일, `SmokeClientSeeder` 도 7e935d6 에 포함(아래 4 확인됨). 핀 그대로 안전.

**2) imagePullSecrets — ✅ 선언형 매니페스트 제공(`overlays/dev/pull-secret-dev.yaml`)**
- `harbor-cred`(`kubernetes.io/dockerconfigjson`, harbor.local+admin/Harbor12345) + ServiceAccount/default 에 `imagePullSecrets:[{name:harbor-cred}]` → `resources` 에 포함. **즉 `apply -k` 한 방에 같이 적용**(아래 3).
- (명령형 동치, 선언형 대신 쓰려면):
```
kubectl -n si-msa create secret docker-registry harbor-cred \
  --docker-server=harbor.local --docker-username=admin --docker-password=Harbor12345
kubectl -n si-msa patch serviceaccount default -p '{"imagePullSecrets":[{"name":"harbor-cred"}]}'
```
- ⚠️ SA 의 imagePullSecrets 는 **신규 파드부터** 적용. dev 최초 apply 는 앱 파드가 이때 처음 생기므로 그대로 반영(기존 파드 있으면 `rollout restart`).

**3) apply + pull 트리아지**
```
kubectl apply -k deploy/k8s/overlays/dev
kubectl -n si-msa get pods -w
```
- ⚠️ **pull 실패는 두 층으로 구분**(Events 메시지로): ① **인증** `unauthorized`/`no basic auth credentials` → harbor-cred/SA 부착 확인(위 2), ② **이름해소/연결** → 노드 containerd 가 `harbor.local` 못 풂. 처리 후보:
  - (a) 이 환경 "로컬 빌드 이미지 자동노출"(registry-mirror) 특성으로 pull 생략될 수 있음 → 우선 그대로 apply 해보고 Events 확인.
  - (b) 막히면 노드측 `hosts.toml`(ingress/`harbor-core.harbor.svc` 매핑) — `K8S_INGRESS_HARBOR.md` §6.
  - → `kubectl describe pod <svc>` Events 로 환경 맞춤 트리아지.

**4) AS 토큰 스모크(그린 기준의 일부)** — prod 프로파일 AS 는 등록 클라이언트 0건(설계)이라 토큰 스모크엔 클라이언트가 필요. 7e935d6 이미지에 `SmokeClientSeeder` 포함 확인됨(`framework.auth.seed-smoke-client`, 기본 false). **리허설 한정**으로 dev AS 에 한시 옵트인:
```
# 리허설용(prod 토폴로지 검증). prod-rehearsal(S5)·운영엔 미적용.
kubectl -n si-msa set env deploy/auth-server FRAMEWORK_AUTH_SEED_SMOKE_CLIENT=true
kubectl -n si-msa rollout restart deploy/auth-server
```
그 뒤 demo-web(public+PKCE) authorization_code 또는 demo-service(client_credentials) 로 토큰 발급 확인(= `SmokeClientDbAuthFlowTest` 의 클러스터 등가물).
- **그린 기준**: 6파드 Ready, **Harbor 포털 Pull 수 > 0**(pull 경로 실증), AS 토큰 플로우 스모크 성공.

### S4 애드온 — metrics-server(HPA) → kube-prometheus-stack(SM). ingress-nginx 는 S2 에서 설치됨.
### S5 prod-rehearsal overlay — prod 토폴로지 + 로컬 대역(§2). HPA·TLS ingress·공개 issuer 까지.
### S6 상위 흐름 스모크 — OIDC RP·이중 발급기 우선.
### S7 Jenkins — build→양태그→Harbor push→`kustomize edit set image`→`apply`.

## 4. 산출물 인벤토리 (이번까지)
- `deploy/k8s/components/postgres-persistent/{postgres.yaml,kustomization.yaml}` (S1 ✅)
- `deploy/cicd/harbor-push.sh` (S2, 양태그 push — 기본 `REGISTRY=harbor.local` 로 정정) · `docs/ops/K8S_INGRESS_HARBOR.md` (S2 ingress 경로 ✅) · `docs/ops/HARBOR_SETUP.md`(NodePort 전제 폐기, 포인터)
- **S3(이 드롭)**: `overlays/dev/kustomization.yaml`(이미지 핀 7e935d6 + DB→postgres + admin=admindb 정정 + 파일저장 /tmp/uploads + postgres-persistent resources) · `overlays/dev/pull-secret-dev.yaml`(harbor-cred + default SA) · `PITFALLS.md`(+private Harbor pull) · 정적검증 통과(리소스/패치타깃/이미지/JSON6902/ref).
