# NEXT_K8S_REAL_DEPLOY.md — 현재 환경에서 운영 토폴로지 전체 리허설 (2026-06-06 락, 세션2 진행반영)

> 목표: **Docker Desktop kind(`docker-desktop` 컨텍스트) 단일 환경에서 운영(prod) 경로 전부 리허설.** 외부 인프라(레지스트리/DB/TLS/시크릿)는 로컬 대역으로 끼운다. S3 객체스토리지는 범위 제외(나머지 검증 후).
> 제약: 작성 환경 빌드/`kubectl`/`helm` 직접 실행 불가 → 매니페스트 정적검증만, 실행/트리아지는 받는 쪽.
> 노출 정정: 이 환경은 **NodePort 호스트 비노출 / LoadBalancer 만 localhost 매핑** → Harbor 는 ingress 경유(`ops/K8S_INGRESS_HARBOR.md`). `ops/HARBOR_SETUP.md` 의 NodePort 전제는 폐기.

---

> **▶ 다음 세션 시작점(2026-06-07 S5 종료 기준)**: ① **commit/push 먼저**(S5 산출 `observability/grafana-dashboard-jvm.yaml` + `standalone-kind/06-grafana-jvm-dashboard.sh` + 문서 누적 uncommitted — 그린 박제). ② **S7 Jenkins 착수**(아래 §S7 킥오프 체크리스트) — 기존 `deploy/cicd/{Jenkinsfile,harbor-push.sh,ci-cd.yml}`+`redeploy.sh` 검토 → sha 핀 자동주입(`kustomize edit set image`)+dry-run 게이트 정합. 배포 대상 overlay=dev. 클러스터=standalone `kind-sanity` 3노드(S4/S5 그린: 6파드 Running + metrics-server + kube-prometheus-stack + JVM 대시보드 + 4/4 타깃 UP). 재부팅 보존=PVC(DB)·ConfigMap(대시보드) 자동복귀, port-forward 재실행 필요. (연기) S6 상위흐름·(최종) 실부하 HPA.

## ⭐ 진행 현황 (2026-06-06 세션2)

| 단계 | 상태 | 메모 |
|---|---|---|
| **S1 영속 postgres(PVC)** | ✅ **완료** | `components/postgres-persistent`(StatefulSet) apply → `data-postgres-0` **Bound**(5Gi/RWO/standard), `postgres-0` Running. Service 는 headless→**ClusterIP** 정정(clusterIP 불변 충돌 해소). |
| **S2 Harbor + push** | ✅ **완료(ingress 경로)** | ingress-nginx(LoadBalancer, EXTERNAL-IP=localhost) + Harbor `expose.type=ingress`(`harbor.local`) + Windows/WSL hosts + insecure-registries. `si-msa` 프로젝트에 4서비스 × (`7e935d6`,`dev`) push 확인(포털). |
| **S3 dev overlay apply** | ⛔ **Docker Desktop kind 에선 BLOCKED(노드 pull 구조적 한계) · standalone kind 트랙으로 전환** | 매니페스트는 완성·정적검증 통과(DB/admindb/파일저장/pull-secret). 그러나 **노드 containerd 가 모든 pull 을 내장 미러(`registry-mirror:1273`)로 가로채 `harbor.local` 직접 pull 이 500** → 외부 레지스트리 pull *실증 불가*(인증은 정상이었음). `:dev`(실재 태그)로도 동일. **결정: 레지스트리 pull 검증은 standalone kind 로(§S3').** dev overlay 핀은 stale `7e935d6`→`:dev` 로 정정(sha 핀은 CI 몫). |
| **S3' standalone kind 트랙** | ✅ **본체 그린(6파드 Running) · AS 토큰 1줄만 잔여** | ②무인증 pull ✅ → ③인증 pull ✅(결정 B) → ④ `03-dev-overlay-up.sh` 실행: 빌드→push→`apply -k overlays/dev`→**6파드 1/1 Running RESTARTS 0** + authdb/sidb/admindb ✅. 결함3건 수정(default SA 레이스→imagePullSecrets 파드주입 / prod 프로파일 시크릿가드 차단→통과값 / gateway 생존단서). **잔여**: client_credentials access_token — wrong-secret→401 로 인증 메커니즘 정상 확인, 깨끗한 port-forward 로 1줄 확인만 남음(다음 세션 첫 항목). |
| S4 애드온 | 대기 | metrics-server/HPA → kube-prometheus-stack → (ingress-nginx 는 S2 에서 선설치됨). |
| S5 prod-rehearsal overlay | ✅ **관측 마감 종료** | JVM 대시보드 자동적재 + Prometheus **4/4 타깃 UP 실측 PASS**(2026-06-07 받는 쪽, `t=5s UP=4/4`). prod overlay live 리허설=S7 흡수, 실부하 HPA 스케일업=최종 수용시험. |
| S6 상위 흐름 스모크 | 대기 | OIDC RP·이중 발급기 우선. |
| S7 Jenkins | 대기 | 자동화 마지막. |

> 세션2 함정(되돌리지 말 것): ① 이 환경 NodePort 는 호스트/데몬 비노출, **port-forward 는 127.0.0.1 만 바인딩**해 Docker Desktop **VM 데몬이 못 봐서** `docker login` timeout → **LoadBalancer(ingress)** 가 정답(데몬 도달). ② Docker Desktop 데몬은 **Windows hosts** 를 참조 → `harbor.local` 은 Windows hosts 에 필수(WSL hosts 는 호스트 curl 용). ③ Service clusterIP 는 불변 → 기존 ClusterIP svc 를 headless 로 바꾸려다 거부됨(S1 정정).

## 0. 확정 결정 (락)

| # | 항목 | 결정 |
|---|---|---|
| 1 | 이미지 태그 | 양태그(`:<git-sha>` 핀 + 채널 `:dev`/semver). **불변 sha 핀은 CI(S7)가 `kustomize edit set image` 로 주입** — 사람이 매니페스트에 타이핑하지 않음(수동 핀이 stale 되는 게 7e935d6 사건의 교훈). 수동 리허설 중에는 채널 `:dev` 사용 |
| 2 | 이미지 서버 | **Harbor**(ingress 노출, `harbor.local`). 호스트 push 도달=LoadBalancer 경로(정상). **단 노드 pull 실증은 Docker Desktop kind 에서 불가**(#6) |
| 3 | 영속 | 기본 StorageClass(`standard`, local-path) 동적 프로비저닝 PVC. postgres=StatefulSet, redis=휘발(설계). 객체스토리지(S3) 제외 |
| 4 | overlay | dev 먼저 → prod-rehearsal. dev≠prod(§1) |
| 5 | CI/CD | Jenkins 는 마지막 |
| 6 | **클러스터(신규 락)** | **레지스트리 pull 실증은 standalone kind(`kind` CLI 직접 생성)에서.** Docker Desktop kind 는 노드 containerd 미러 인터셉트로 외부 레지스트리 직접 pull 불가·노드 설정 입구 없음(세션3 실측, PITFALLS §9). standalone 은 노드 수 자유(현 3노드 재현) + `containerdConfigPatches` 로 harbor 직접 pull |

## 1. dev vs prod 차이 (요약)
dev=1레플리카·약한시크릿·port-forward / prod=HPA(metrics-server)·외부시크릿(ESO)·공개issuer·TLS ingress. dev 그린이 prod 보장 안 함 — 애드온 작업이 prod 추가분 검증과 겹침.

## 2. 외부 의존물 → 로컬 대역
레지스트리→Harbor(`harbor.local`) · 외부 DB→인-클러스터 영속 postgres(`components/postgres-persistent`, DB_URL=`postgres:5432`) · 외부 Redis→인-클러스터(base) · 실TLS→self-signed/`*.local` · ESO→평문 Secret.

## 3. 런북 (S3 부터 상세)

### S3 — dev overlay 실 apply (Docker Desktop kind: ⛔ BLOCKED — 아래 결론, 다음은 §S3')

> **결론(2026-06-06 세션3)**: 매니페스트는 완성됐고 호스트 push 도 정상이지만, **Docker Desktop kind 에선 노드 containerd 가 모든 pull 을 내장 미러(`registry-mirror:1273`)로 가로채 `harbor.local` 직접 pull 이 500** → 외부 레지스트리 pull *실증 불가*. `:dev`(실재 태그)로도 `ImagePullBackOff` 로 확정(인증·태그 문제 아님 — `harbor-cred`/SA 정상 부착 확인됨, 호스트 `docker pull` 은 성공하나 *다른 주체*). 노드 containerd 설정은 `kind create --config` 입구가 없어 선언적으로 못 박고, 노드 직접 수정(`kubectl debug node`)은 재현 불가라 운영 패턴 아님. **→ 레지스트리 pull 검증은 standalone kind(§S3')로 이관.** 아래 1~4 는 Docker Desktop kind 에서 *시도한 기록*(앱/토폴로지 검증 로직은 standalone 에서 재사용). PITFALLS §9 \"Docker Desktop kind 미러 인터셉트\" 참조.

**0) 재부팅 검증(섹션 시작 시 1회)** — Docker Desktop 껐다 켠 뒤 ingress/LB·Harbor·postgres PVC 가 자동 복귀하는지(`kubectl get svc -n ingress-nginx` EXTERNAL-IP, `harbor.local` 포털, `data-postgres-0` Bound). = port-forward 졸업 확인.

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

**3) apply + pull 트리아지 — 실측 결과: ⛔ 미러 인터셉트 500**
```
kubectl apply -k deploy/k8s/overlays/dev
kubectl -n si-msa get pods -w
```
- 실측: 새 파드 `ImagePullBackOff`, `describe` Events =
  `failed to resolve reference "harbor.local/si-msa/<svc>:<tag>": HEAD http://registry-mirror:1273/v2/si-msa/<svc>/manifests/<tag>?ns=harbor.local: 500`.
- 절단 확인: `harbor-cred`/default SA/파드 spec 모두 `imagePullSecrets` 정상(=인증 아님), `:dev`(실재 태그)로도 동일(=태그 아님), 호스트 `docker pull harbor.local/...` 성공(=호스트 데몬과 노드 containerd 는 다른 주체). → **노드↔레지스트리 도달층이 막힘. Docker Desktop kind 구조적 한계** → §S3'.
- (참고) 자동노출은 **짧은 이름**(`si-msa/<svc>:tag`)만. `harbor.local/...` 레지스트리 한정 이름은 미러가 pull-through 시도→500.

**4) AS 토큰 스모크(그린 기준의 일부)** — prod 프로파일 AS 는 등록 클라이언트 0건(설계)이라 토큰 스모크엔 클라이언트가 필요. 이미지에 `SmokeClientSeeder` 포함(`framework.auth.seed-smoke-client`, 기본 false). **리허설 한정** 옵트인:
```
kubectl -n si-msa set env deploy/auth-server FRAMEWORK_AUTH_SEED_SMOKE_CLIENT=true
kubectl -n si-msa rollout restart deploy/auth-server
```
그 뒤 demo-web(public+PKCE) authorization_code 또는 demo-service(client_credentials) 로 토큰 발급 확인(= `SmokeClientDbAuthFlowTest` 의 클러스터 등가물). **단 이 단계는 6파드 Ready(=pull 통과) 전제 → standalone(§S3')에서 실행.**
- **그린 기준**: 6파드 Ready, **Harbor 포털 Pull 수 > 0**(pull 경로 실증), AS 토큰 플로우 스모크 성공.

### ▶ S3' — standalone kind 트랙 (다음 섹션 시작점)
> **왜**: Docker Desktop kind 는 노드 containerd 를 선언적으로 설정할 입구가 없어 외부 레지스트리 pull 실증 불가(위 S3 결론). standalone kind 는 클러스터 생성 시 노드 containerd 를 박을 수 있다 → Harbor 깐 목적(빌드→push→**노드 pull**)을 닫는다.
> **소스는 github 에 있으므로** 기존 Docker Desktop kind 자산(인-클러스터 Harbor/PVC) 소멸은 무방 — 매니페스트·push 스크립트·Harbor 설정값 전부 git/zip 보존됨. 호스트 push 경로(S2)는 그대로 재사용.

**합의 순서(이 순서로 진행)**:
1. **PITFALLS 제약 못박기** — ✅ 완료(이 세션: §9 "Docker Desktop kind 미러 인터셉트 = 외부 pull 불가" + 자동노출 편의의 이면).
2. **최소 pull sanity(먼저 검증, 이론 맹신 금지)** — ✅ **PASS(2026-06-06)**: `deploy/k8s/standalone-kind/01-pull-sanity.sh` 가 node=sanity-worker 에서 `reg.local/sanity/busybox:test`(레지스트리 한정 이름)을 직접 pull(Pulled 1건). **메커니즘 실증** — Docker Desktop kind 미러 인터셉트가 standalone 에선 없음.
   - 설계 결정 2(Windows 함정 회피, PITFALLS §9): 레지스트리 이름 점(.) 필수 · certs.d 콜론 금지. 선행: `kind` CLI 설치(DD 내장 kind ≠ standalone CLI).
3. **Harbor/ingress/postgres 풀 재구축(스크립트화)** — 2 PASS 후 착수. **첫 조각 = 인증 pull sanity `02-auth-pull-sanity.sh`(드롭)**: htpasswd 비공개 레지스트리(harbor.local) + `harbor-cred`(imagePullSecrets) → 노드 pull. docker-desktop kind 에선 도달층에서 막혀 못 봤던 *인증 경로*를 끝까지 검증(secret/SA 부착은 맞았으나 pull 자체가 안 됐었음). PASS 면 dev overlay `pull-secret-dev.yaml` 경로가 standalone 에서 유효 + 이 레지스트리가 4단계 토대.
   - ⚠️ **결정(받는 쪽) = B 확정**: 4단계 레지스트리는 `02` 의 인증 레지스트리(harbor.local)로 충분(프레임워크 검증=DB/admindb/파일저장/AS 토큰이 목적). Harbor 제품(포털/RBAC/스캔)은 미설치 — 필요 시 S4 이후 별도 트랙.
4. **실 이미지 → push → dev overlay apply → 검증** — ✅ **실행·본체 그린**(`03-dev-overlay-up.sh`): 빌드→`localhost:5443/si-msa/<svc>:dev` push(노드는 harbor.local pull)→`apply -k overlays/dev`→**6파드 1/1 Running RESTARTS 0** + authdb/sidb/admindb 분리 확인. AS client_credentials access_token 만 잔여(wrong-secret→401=인증 정상, 깨끗한 port-forward 로 1줄 확인). 결함3건(default SA 레이스·prod 시크릿가드·gateway 단서) 수정 반영. **산출물 드롭 `03-dev-overlay-up.sh`**. 빌드(`docker compose build` = Dockerfile.build 컨테이너 Gradle) → `localhost:5443/si-msa/<svc>:dev` push(노드는 harbor.local pull) → `kubectl apply -k overlays/dev` → postgres/redis/4앱 rollout 대기 → 6파드 Ready + 앱 Pulled>0 + authdb/sidb/admindb 검증. `--smoke` 면 시드 클라이언트(`FRAMEWORK_AUTH_SEED_SMOKE_CLIENT=true`) + demo-service client_credentials access_token.
   - ✅ **prod 프로파일 그린 전제 레포 확인됨**: `ProdAuthenticatorConfig.java`(auth-server, `@Profile("!local")`) · AS `/actuator/**` permitAll · user/admin `framework-redis` 의존 · REDIS_HOST=redis · DB_URL/admindb 패치 · 파일저장 /tmp/uploads 패치.
   - ✅ **standalone kind = NetworkPolicy 비집행(kindnet)** → base default-deny 가 apps→postgres 를 막지 않음(docker-desktop kind 와 결정적 차이). postgres allow 규칙 불요(만약 Connect timed out/08001 이면 집행 CNI 신호 → allow-postgres 추가).
3. **Harbor/ingress/postgres 풀 재구축**(스크립트화) — 2 통과 후.
4. **push → 노드 pull(Harbor Pull>0) → dev overlay apply → DB/admindb/파일저장/AS 토큰**(S3 의 앱·토폴로지 검증 로직 재사용).

**standalone kind 노드 설정(실제 산출물 — `deploy/k8s/standalone-kind/`)**:
`kind-config.yaml`(3노드 + `containerdConfigPatches`(config_path) + `certs.d` extraMounts) + `certs.d/reg.local/hosts.toml`(노드의 `reg.local` 해소 → `kind-registry:5000`). 상세·실행법은 그 폴더 `README.md`.
```yaml
# 요지(전문은 kind-config.yaml): 노드에 손대지 않고 *생성 시* 선언
containerdConfigPatches:
  - |-
    [plugins."io.containerd.grpc.v1.cri".registry]
      config_path = "/etc/containerd/certs.d"
# 각 노드 extraMounts: ./certs.d -> /etc/containerd/certs.d (3노드 재현)
```
```toml
# certs.d/reg.local/hosts.toml — 노드가 reg.local 을 kind-registry:5000 으로 직접 pull
[host."http://kind-registry:5000"]
  capabilities = ["pull", "resolve"]
```
> ⚠️ **레지스트리 이름엔 점(.) 필수**(`reg.local`) — 점 없는 이름(`kind-registry`)은 containerd 가 Docker Hub org 로 파싱(이름 규칙) → 노드 pull 테스트가 무의미. **certs.d 디렉터리엔 콜론 금지**(`localhost:5001` 은 NTFS 디렉터리 불가 → extraMounts 깨짐) → 포트 없는 `reg.local` 로 잡고 hosts.toml 안에서 `:5000` 리다이렉트. (PITFALLS §9 신규 2항)
> `harbor.local` 의 노드측 해소는 sanity 통과 후 3단계에서 hosts.toml `[host."..."]` 를 ingress IP/ClusterIP 로 두는 식으로 확정.
> ⚠️ **ArgoCD/GitOps 로도 이 문제는 안 풀린다** — CD 는 매니페스트 apply 만, 이미지 pull 은 언제나 노드 containerd(PITFALLS §9).

### S4 애드온 — metrics-server(HPA) → kube-prometheus-stack(SM). ingress-nginx 는 S2 에서 설치됨.
### S5 관측 마감 ✅ — JVM Grafana 대시보드 자동적재 + Prometheus 4/4 타깃 정밀검증(`06-grafana-jvm-dashboard.sh`). prod overlay live 리허설=S7 흡수, 실부하 HPA 스케일업=최종 수용시험.
### S6 상위 흐름 스모크 — (연기, Chae 결정) S7 후 선택. 핵심 상위흐름(authorization_code+PKCE+id_token+jwks)은 `smoke-authcode-pkce.sh` 로 실클러스터 실측 완료. 잔여=confidential RP 풀콜백·게이트웨이 이중 issuer 분기 실클러스터(둘 다 in-process 테스트 검증됨, 저위험).
### S7 Jenkins — build→양태그→Harbor push→`kustomize edit set image`→(dry-run 렌더검증)→`apply`. **▶ 다음 착수.**
> **S7 킥오프 체크리스트(다음 세션 시작점)**:
> 1. 기존 자산 검토 = `deploy/cicd/Jenkinsfile`(게이트→Flyway Validate→Build&Push matrix→Deploy)·`deploy/cicd/harbor-push.sh`(양태그 push)·`deploy/cicd/ci-cd.yml`(GH Actions 등가)·`deploy/k8s/standalone-kind/redeploy.sh`(콘텐츠해시 태그→`set image`→rollout, 단일서비스 로컬 루프=S7 의 1서비스 등가물).
> 2. **불변 sha 핀 자동주입** — 사람이 매니페스트 타이핑 금지(7e935d6 stale 교훈). CI 가 `kustomize edit set image registry/si-msa/<svc>=harbor.local/si-msa/<svc>:<git-sha>` 로 주입 → `apply -k`. 수동 리허설은 채널 `:dev`.
> 3. **배포 대상 overlay = dev**(외부 prod 미사용 토폴로지). prod overlay 는 예시 유지. **prod overlay dry-run 렌더검증을 파이프라인 게이트로 흡수**(`kubectl kustomize overlays/prod`/`apply --dry-run=server` = 배선 깨짐 조기 검출, 외부 인프라 도달 불요).
> 4. push 대상 = Harbor(`harbor.local`, S2 ingress 경로). 노드 pull 실증은 standalone `kind-sanity`(02/03 경로).
> 5. 산출물 후보: Jenkinsfile 의 `kustomize edit set image`→`apply -k overlays/dev` 스테이지 정합 + dry-run 게이트 추가 + (선택) `redeploy.sh` 의 다이제스트 승격 로직을 파이프라인 등가로 일반화. 작성환경=정적검증(Jenkinsfile 린트/kustomize 에뮬), 실 파이프라인 실행=받는 쪽(Jenkins 인스턴스).

## 4. 산출물 인벤토리 (이번까지)
- `deploy/k8s/components/postgres-persistent/{postgres.yaml,kustomization.yaml}` (S1 ✅)
- `deploy/cicd/harbor-push.sh` (S2, 양태그 push — 기본 `REGISTRY=harbor.local` 로 정정) · `docs/ops/K8S_INGRESS_HARBOR.md` (S2 ingress 경로 ✅) · `docs/ops/HARBOR_SETUP.md`(NodePort 전제 폐기, 포인터)
- **S3'(2·3단계 ✅ PASS + 4단계 드롭)**: `deploy/k8s/standalone-kind/{kind-config.yaml, certs.d/{reg.local,harbor.local}/hosts.toml, 01-pull-sanity.sh(✅), 02-auth-pull-sanity.sh(✅), 03-dev-overlay-up.sh, 00-cleanup.sh, README.md}` · `PITFALLS.md` §9 신규 3항 · 이 문서 §S3' 정정(B 결정·prod 그린 전제 레포 확인).
- **S3(세션3 결과)**: `overlays/dev/kustomization.yaml`(DB→postgres + admin=admindb 정정 + 파일저장 /tmp/uploads + ServiceMonitor `$patch:delete` + postgres-persistent resources; **이미지 핀 stale `7e935d6`→`:dev` 로 정정**) · `overlays/dev/pull-secret-dev.yaml`(harbor-cred + default SA) · `PITFALLS.md`(Docker Desktop kind 미러 인터셉트=외부 pull 불가 항목 완성 + private-Harbor 예측 실측 정정) · **결론: Docker Desktop kind 노드 pull BLOCKED → standalone kind 트랙(§S3')으로 이관**.
