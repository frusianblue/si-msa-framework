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
#   그린: 6파드 Ready + 앱 Pulled>0 + authdb/sidb/admindb + (--smoke) access_token.

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
