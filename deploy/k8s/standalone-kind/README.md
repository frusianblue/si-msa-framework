# standalone-kind — 외부 레지스트리 → 노드 pull 실증 트랙 (§S3')

> **왜 있나**: Docker Desktop kind 는 노드 containerd 가 모든 pull 을 내장 미러(`registry-mirror:1273`)로
> 가로채 `harbor.local` 같은 레지스트리 한정 이름 직접 pull 이 500 으로 막힌다(PITFALLS §9, NEXT_K8S_REAL_DEPLOY §0 락#6).
> Docker Desktop 의 "Modify Kubernetes Cluster" GUI 에도 노드 containerd 를 박을 입구가 없다(Advanced Settings 는
> "Show system containers" 가시성 토글뿐). → **`kind` CLI 로 직접 생성하면 생성 시점에 노드 containerd 를 선언적으로 박을 수 있다.**

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
| `00-cleanup.sh` | docker-desktop kind 잔여 디버그 파드 정리 + (옵션)standalone teardown |

## 쓰는 법
```bash
# (선택) 현 docker-desktop kind 잔여 디버그 파드 먼저 정리
bash deploy/k8s/standalone-kind/00-cleanup.sh

# 최소 pull sanity (5분)
bash deploy/k8s/standalone-kind/01-pull-sanity.sh
#   → "✅ PASS" 면 노드 pull 유효 → 3단계로.
#   → "❌ FAIL" 이면 스크립트가 트리아지 힌트 출력(certs.d 마운트/이름규칙/네트워크).

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
