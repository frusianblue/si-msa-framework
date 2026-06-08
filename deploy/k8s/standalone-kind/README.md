# standalone-kind — 외부 레지스트리 → 노드 pull 실증 트랙 (§S3')

> **왜 있나**: Docker Desktop kind 는 노드 containerd 가 모든 pull 을 내장 미러(`registry-mirror:1273`)로
> 가로채 `harbor.local` 같은 레지스트리 한정 이름 직접 pull 이 500 으로 막힌다(PITFALLS §9, NEXT_K8S_REAL_DEPLOY §0 락#6).
> Docker Desktop 의 "Modify Kubernetes Cluster" GUI 에도 노드 containerd 를 박을 입구가 없다(Advanced Settings 는
> "Show system containers" 가시성 토글뿐). → **`kind` CLI 로 직접 생성하면 생성 시점에 노드 containerd 를 선언적으로 박을 수 있다.**

## 선행 (설치) — Docker Desktop 내장 kind ≠ standalone kind CLI
Docker Desktop 의 "Kubernetes(kind 모드)" 는 **자기 내부에서만** kind 를 돌려 standalone `kind` 바이너리를 PATH 에 깔지 않는다.
→ 이 트랙은 `kind` CLI 가 별도로 필요(없으면 `01-pull-sanity.sh` 가 `FAIL: 'kind' 가 PATH 에 없음`).
**Docker Desktop 의 Kubernetes 토글은 꺼도 무방**(끄면 오히려 내장 k8s 와 충돌 없음) — Docker *엔진*은 그대로라 standalone kind 가 그 위에 노드를 띄운다.
```bash
# 0) 엔진/클라이언트 확인 (WSL)
docker ps                         # 에러면 Docker Desktop > Settings > Resources > WSL Integration 활성화
kubectl version --client          # 보통 이미 설치돼 있음

# 1) kind 설치 (Linux/amd64, 최신 릴리스)
curl -Lo ./kind https://github.com/kubernetes-sigs/kind/releases/latest/download/kind-linux-amd64
chmod +x ./kind && sudo mv ./kind /usr/local/bin/kind && kind --version
# (버전 고정이 필요하면: https://kind.sigs.k8s.io/dl/v0.32.0/kind-linux-amd64)
```

## 무엇을 증명하나 (단 하나)
**standalone kind 의 노드 containerd 가 레지스트리 한정 이름(`reg.local/...`)을 *직접* pull 한다.**
= Docker Desktop kind 에서 막혔던 바로 그 동작. 이게 되면 "빌드→push→**노드 pull**"(Harbor 깐 목적)을 닫을 수 있다.

## 합의 순서에서의 위치
1. PITFALLS 제약 못박기 — ✅ 완료
2. **최소 pull sanity ← 여기 (이 폴더)** — 통과해야 3 착수(이론 맹신 금지)
3. Harbor/ingress/postgres 풀 재구축(스크립트화)
4. push → 노드 pull(Harbor Pull>0) → `dev` overlay apply → DB/admindb/파일저장/AS 토큰

## 구성
| 파일 | 역할 |
|---|---|
| `kind-config.yaml` | 3노드(현 토폴로지 재현) + `containerdConfigPatches`(config_path) + `certs.d` extraMounts |
| `certs.d/reg.local/hosts.toml` | 노드의 `reg.local` 해소 규칙 → `kind-registry:5000` 직접 pull |
| `01-pull-sanity.sh` | 레지스트리1 + 더미 이미지(busybox) push → **노드 pull → 파드 Ready** 까지 검증(PASS/FAIL) |
| `02-auth-pull-sanity.sh` | **비공개(Basic auth) 레지스트리** + `harbor-cred`(imagePullSecrets) → 노드 pull 검증. dev overlay 의 인증 경로 실증(3단계 첫 조각) |
| `03-dev-overlay-up.sh` | **4단계**: 실 이미지 빌드(compose) → harbor.local push → `dev` overlay apply → 6파드 Ready/DB 검증(`--smoke` 시 AS 토큰) |
| `smoke-authcode-pkce.sh` | **검증**: 실클러스터 authorization_code+PKCE+`DbAuthenticator` 전체 흐름(폼로그인→code→토큰→id_token sub=tester→jwks). `SmokeClientDbAuthFlowTest` 의 실클러스터 등가물 |
| `04-metrics-hpa.sh` | **S4-1**: metrics-server 설치(kind `--kubelet-insecure-tls`) → `kubectl top` → 일회용 HPA 로 metrics→HPA 파이프라인 확인(`--load` 스케일 관찰·`--keep` 유지) |
| `05-prometheus-stack.sh` | **S4-2**: kube-prometheus-stack(Helm) 설치 → ServiceMonitor 적용 → Prometheus `si-msa-services` 타깃 UP 스모크(`--grafana` 접속정보) |
| `06-grafana-jvm-dashboard.sh` | **S5(관측 마감)**: 자작 JVM/Micrometer 대시보드 ConfigMap(`../observability/grafana-dashboard-jvm.yaml`) 적용(Grafana sidecar 자동 import) + Prometheus 4서비스 **4/4 타깃 UP 정밀 검증**(`--grafana` 접속정보). 05 는 UP≥1 에서 멈추지만 06 은 4/4 를 요구 |
| `certs.d/harbor.local/hosts.toml` | 노드의 `harbor.local` 해소 → `harbor-auth-reg:5000`(02 용) |
| `00-cleanup.sh` | docker-desktop kind 잔여 디버그 파드 정리 + (옵션)standalone teardown |

## 쓰는 법
```bash
# (선택) 현 docker-desktop kind 잔여 디버그 파드 먼저 정리
bash deploy/k8s/standalone-kind/00-cleanup.sh

# 최소 pull sanity (5분) — ✅ 2026-06-06 PASS(node=sanity-worker, reg.local 직접 pull)
bash deploy/k8s/standalone-kind/01-pull-sanity.sh
#   → "✅ PASS" 면 노드 pull 유효 → 다음.

# 비공개(인증) 레지스트리 pull sanity — 3단계 첫 조각(dev overlay 인증 경로 실증)
bash deploy/k8s/standalone-kind/02-auth-pull-sanity.sh
#   → "✅ PASS" 면 harbor-cred + imagePullSecrets 로 노드가 비공개 레지스트리 pull.
#      이 레지스트리(harbor.local)가 곧 4단계(실 si-msa 이미지 push → dev overlay apply)의 토대.

# 4단계 — 실 이미지 빌드 → push → dev overlay apply → 검증 (B 결정: 인증 레지스트리로 충분)
bash deploy/k8s/standalone-kind/03-dev-overlay-up.sh          # 빌드+push+apply+6파드/DB 검증
bash deploy/k8s/standalone-kind/03-dev-overlay-up.sh --smoke  # + AS 토큰(client_credentials) 스모크
#   그린: 6파드 Ready + 앱 Pulled>0 + authdb/userdb/admindb + (--smoke) access_token.

# OAuth2 운영 인증 경로(authorization_code+PKCE+DbAuthenticator) 실클러스터 검증 — 시더 on 가정(아니면 ENSURE_SEEDER=1)
bash deploy/k8s/standalone-kind/smoke-authcode-pkce.sh
#   그린: 7단계 ✅ + sub=tester + jwks=200 → ② 종료.

# S4-1: metrics-server + HPA 스모크
bash deploy/k8s/standalone-kind/04-metrics-hpa.sh
#   그린: kubectl top OK + HPA TARGETS≠<unknown>.

# S4-2: kube-prometheus-stack + ServiceMonitor 스크랩 스모크
bash deploy/k8s/standalone-kind/05-prometheus-stack.sh            # --grafana 로 접속정보
#   그린: si-msa-services 타깃 UP → 관측 파이프라인 정상.

# S5(관측 마감): JVM 대시보드 자동적재 + 4/4 타깃 정밀검증
bash deploy/k8s/standalone-kind/06-grafana-jvm-dashboard.sh       # --grafana 로 접속정보
#   그린: JVM 대시보드 import + Prometheus 4서비스 4/4 UP.

# 끝나면 정리
bash deploy/k8s/standalone-kind/00-cleanup.sh --teardown-sanity
```

## 설계 결정(둘 다 Windows/Docker Desktop 함정 회피)
- **레지스트리 이름엔 점(.) 필수** — `kind-registry`(점 없음)는 containerd 가 Docker Hub org 로 파싱해 테스트가 무의미.
  `reg.local`(점 있음)이라야 "레지스트리 한정 이름 직접 pull"(= harbor.local 실패모드)을 진짜 재현.
- **certs.d 디렉터리엔 콜론 금지** — `localhost:5001`(콜론)은 NTFS 디렉터리로 못 만들어 `extraMounts` 가 깨짐.
  → 포트 없는 `reg.local` 로 잡고 certs.d 안에서 `kind-registry:5000` 으로 리다이렉트.
- **push 이름 ≠ pull 이름, 리포지토리 경로는 동일** — 호스트는 published 포트(`localhost:5001`)로 push,
  노드는 `reg.local`(certs.d→`kind-registry:5000`)로 pull. 레지스트리는 호스트명이 아니라 리포지토리 경로로
  저장/서빙하므로 같은 블롭(`sanity/busybox`)을 가리킨다.

## 주의
- `kind` CLI 가 만든 `kind-sanity` 는 **docker-desktop kind 와 별개 클러스터/컨텍스트**. 현 클러스터는 안 건드린다.
- `extraMounts` hostPath(`./certs.d`)는 `kind create` 실행 디렉터리 기준 → 스크립트가 이 폴더로 cd 한다.
  Windows 는 이 경로가 Docker Desktop **File sharing** 에 포함돼야 마운트됨.
- 노드 수 변경/재설정은 클러스터 리셋 → `00-cleanup.sh --teardown-sanity` 후 재생성.
- **ArgoCD/GitOps 로도 이 문제는 안 풀린다** — CD 는 매니페스트 apply 만, pull 은 언제나 kubelet+노드 containerd.
  GitOps 는 노드 pull 정상 전제 위에 얹는 층.

## 이미지 태그 = 불변 sha(주입식, 가변 :dev 폐기)
- 무엇이 뜨는지의 단일 진실 = **불변 태그**(CI=git short sha, 수동 03=커밋 short sha). 가변 `:dev` 배포핀과
  명령형 `kubectl set image` 덮어쓰기는 폐기(git↔클러스터 드리프트·stale 함정 제거 — PITFALLS §9).
- dev overlay 의 `images.newTag` 는 sentinel `__GITSHA__` — **그대로 `apply -k` 하면 ImagePullBackOff(fail-loud)**.
  배포 직전 **`bash deploy/k8s/pin-image-tag.sh <overlay-dir> <tag>`** 로 sentinel 을 불변 태그로 치환한다.
  - `03-dev-overlay-up.sh`/CI 가 이 헬퍼를 호출(워크스페이스만 치환, 되커밋 없음; 수동은 apply 후 작업트리 복원).
- 재부팅 복구(`07-reboot-recover.sh`)는 **살아있는 Deployment 의 image ref 태그**로 kind load(하드코딩 `:dev` 아님).
- 단일 서비스 빠른 반복은 `redeploy.sh`(소스 콘텐츠 다이제스트 태그) — 미커밋 변경까지 추적.


## 관측(Grafana/Prometheus) 호스트 접속 = ingress (B안)
- `05`/`06` 를 `--grafana` 로 실행하면 kube-prometheus-stack 을 `monitoring-values.yaml`(grafana/prometheus.ingress + grafana.ini)로 설치하고 접속정보를 출력한다.
- 호스트 hosts 파일에 1줄(노드는 불요 — Harbor 와 달리 pull 대상 아님):
  ```
  127.0.0.1 grafana.local prometheus.local
  ```
- 접속: `http://grafana.local`(admin / `kubectl -n monitoring get secret kube-prometheus-stack-grafana -o jsonpath='{.data.admin-password}'|base64 -d`), `http://prometheus.local`.
- HTTP 평문이라 ingress 에 `ssl-redirect: "false"` 가 박혀 있다(없으면 https 308). 점검: `curl -H "Host: grafana.local" http://localhost/api/health`.
- (대안) hosts 없이 즉시: `kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 3000:80`.
