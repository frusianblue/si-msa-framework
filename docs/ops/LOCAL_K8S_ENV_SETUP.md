# 로컬 환경 구성 가이드 — kind 사용을 위한 Docker / kubectl / kind 설치

`docs/modules/LOCAL_K8S_TEST.md` 의 사전 준비(0번)를 OS별로 풀어 쓴 문서다.
설치 순서는 항상 **① Docker → ② kubectl → ③ kind** 다. kind 는 클러스터 노드를
Docker 컨테이너로 띄우므로 **Docker(또는 Podman) 데몬이 떠 있는 것이 절대 전제**다.

설치가 끝나면 다음 단계는 `docs/modules/LOCAL_K8S_TEST.md`(클러스터 생성 → 이미지 적재 → `kubectl apply -k overlays/local`).
서비스 구동에 클러스터로 **추가 설치**해야 하는 애드온(metrics-server·Prometheus·Ingress·시크릿 오퍼레이터)은 `docs/modules/K8S_ADDONS.md` 참고.
소스 빌드/실행용 Windows 개발 툴체인(JDK21·IntelliJ·Git 등)은 `docs/modules/DEV_ENV_WINDOWS.md`.

---

## 공통 요구
- CPU 2코어+, RAM 8GB+ 권장(Docker 에 6GB 이상 할당). 서비스 4개 + redis + postgres 가 동시에 뜬다.
- 디스크 여유 10GB+ (이미지/노드 캐시).
- 인터넷(이미지 pull). 사내 폐쇄망이면 맨 아래 "사내 프록시/폐쇄망" 절 참고.

설치 도구 한눈에:

| OS | Docker | kubectl | kind |
|---|---|---|---|
| macOS | Docker Desktop / OrbStack / colima | `brew install kubectl` | `brew install kind` |
| Windows | Docker Desktop(+WSL2) / Rancher Desktop | `winget install Kubernetes.kubectl` | `winget install Kubernetes.kind` |
| Linux | Docker Engine(apt) | dl.k8s.io 바이너리 | 바이너리 / `go install` |

---

## A. macOS

### 1) Docker
가장 간단한 건 **Docker Desktop**(공식 사이트에서 .dmg). 설치 후 실행하면 메뉴바에 고래 아이콘이
뜨고 데몬이 돈다. Apple Silicon/Intel 빌드가 따로 있으니 칩에 맞게 받는다.
> 라이선스 부담이 있으면 **OrbStack**(가볍고 빠름) 또는 **colima**(`brew install colima && colima start`) 도 가능. 셋 중 무엇이든 `docker` CLI 가 동작하면 된다.

Docker Desktop 메모리: Settings → Resources → Memory 를 6GB 이상으로.

### 2) kubectl & 3) kind (Homebrew)
```bash
brew install kubectl kind
```
Homebrew 가 없으면 먼저 설치(brew.sh). 끝.

---

## B. Windows  (권장: Docker Desktop + WSL2)

Windows 에서 kind 는 **WSL2 백엔드** 위에서 가장 안정적이다. 또한 빌드 명령(`./gradlew`,
`docker build`)을 리눅스 환경에서 돌리는 게 깔끔하므로, **WSL2 안(Ubuntu)에서 작업**하는 것을 권한다.

### 1) WSL2 설치
관리자 PowerShell:
```powershell
wsl --install
```
재부팅 후 Ubuntu 가 설치된다(기본 배포판). 이미 WSL 이 있으면 `wsl --set-default-version 2` 로 버전 2 확인.

### 2) Docker Desktop
공식 사이트에서 Docker Desktop 설치 → 실행 → **Settings → General → "Use the WSL 2 based engine"** 켜기
→ **Settings → Resources → WSL Integration** 에서 사용하는 Ubuntu 배포판 토글 ON.
이러면 WSL2 의 Ubuntu 셸에서 `docker` 명령이 그대로 동작한다. 메모리는 Settings → Resources 에서 6GB+.
> Docker Desktop 라이선스가 걸리면 **Rancher Desktop**(WSL2 기반, 무료)으로 대체 가능.

### 3) kubectl & kind
**WSL2 Ubuntu 셸 안에서**(권장) 아래 Linux 절차(C) 를 그대로 따른다. 모든 빌드/배포 명령도 WSL2 안에서 실행한다.

만약 Windows 호스트(PowerShell)에서 직접 쓰고 싶다면 winget 으로:
```powershell
winget install -e --id Kubernetes.kubectl
winget install -e --id Kubernetes.kind
```
(choco 사용자는 `choco install kubernetes-cli kind`). 단 이 경우에도 Docker Desktop + WSL2 백엔드는 필요하다.

---

## C. Linux  (Ubuntu/Debian 기준 — WSL2 Ubuntu 포함)

### 1) Docker Engine
```bash
# 공식 편의 스크립트(테스트/개발용)
curl -fsSL https://get.docker.com | sh
# sudo 없이 docker 쓰려면 (재로그인 필요)
sudo usermod -aG docker $USER
newgrp docker
docker run --rm hello-world      # 동작 확인
```
> WSL2 + Docker Desktop 을 쓰는 경우 이 단계는 건너뛴다(Docker Desktop 이 제공). `docker` 가 이미 되면 OK.

### 2) kubectl (버전 자동 = 최신 stable)
```bash
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl
kubectl version --client
```

### 3) kind (바이너리)
```bash
# 최신 버전은 https://github.com/kubernetes-sigs/kind/releases 에서 확인(현재 v0.32.0 계열)
[ "$(uname -m)" = x86_64 ] && ARCH=amd64 || ARCH=arm64
curl -Lo ./kind "https://kind.sigs.k8s.io/dl/v0.32.0/kind-linux-${ARCH}"
chmod +x ./kind && sudo mv ./kind /usr/local/bin/kind
kind version
```
> Go(1.21+) 가 있으면 `go install sigs.k8s.io/kind@latest` 한 줄로도 설치된다(`$(go env GOPATH)/bin` 이 PATH 에 있어야 함).

---

## 설치 검증 (공통)
```bash
docker run --rm hello-world          # Docker 데몬 동작
kubectl version --client             # kubectl 동작
kind version                         # kind 동작

kind create cluster --name probe     # 시험용 클러스터
kubectl get nodes                    # control-plane Ready 확인
kind delete cluster --name probe     # 정리
```
`kubectl get nodes` 에서 `kind-control-plane ... Ready` 가 나오면 환경 준비 완료다.
이제 `docs/modules/LOCAL_K8S_TEST.md` 로 넘어가 si-msa 4서비스를 띄운다.

---

## (선택) kind 클러스터 옵션

기본 `kind create cluster` 는 단일 노드다. 멀티노드나 포트 매핑이 필요하면 config 파일을 쓴다.

```yaml
# kind-config.yaml  — 1 control-plane + 2 worker, 호스트 80/443 → 노드 노출(Ingress 실험용)
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraPortMappings:
      - { containerPort: 80,  hostPort: 80 }
      - { containerPort: 443, hostPort: 443 }
  - role: worker
  - role: worker
```
```bash
kind create cluster --name si-msa --config kind-config.yaml
```
> si-msa 로컬 테스트는 `port-forward` 로 충분하므로 멀티노드/포트매핑은 필수가 아니다(Ingress 까지 실험할 때만).

**이미지 적재**가 kind 의 핵심 편의다 — 레지스트리 푸시 없이 로컬 빌드 이미지를 노드에 직접 넣는다:
```bash
kind load docker-image si-msa/gateway:local --name si-msa
```
> 이미지명은 `si-msa/<svc>:local`(local overlay `images.newName` 기준). 가짜 `registry.example.com/` 접두어는 로컬엔 없다.
적재 목록 확인: `docker exec si-msa-control-plane crictl images | grep si-msa`.

---

## 사내 프록시 / 폐쇄망 (금융권 SI 주의)

- **이미지 pull 프록시**: Docker Desktop 은 Settings → Resources → Proxies, 또는 `~/.docker/config.json` 의 `proxies` 에 사내 프록시를 설정. Linux Docker Engine 은 `/etc/systemd/system/docker.service.d/http-proxy.conf` 에 `HTTP_PROXY`/`HTTPS_PROXY`/`NO_PROXY` 후 `systemctl daemon-reload && systemctl restart docker`.
- **kindest/node 이미지**: `kind create cluster` 가 `docker.io/kindest/node` 를 받는다. 폐쇄망이면 사내 레지스트리에 미러링하고 `kind create cluster --image <사내레지스트리>/kindest/node:<tag>` 로 지정.
- **사내 CA**: 사내 TLS 인터셉트 환경이면 Docker/노드가 사내 루트 CA 를 신뢰해야 pull 이 된다.
- **완전 오프라인**: 4개 서비스 이미지 + `kindest/node` + `redis:7-alpine` + `postgres:16-alpine` 를 USB/내부망으로 반입해 `kind load`(앱) / 사내 레지스트리(베이스)로 공급. 앱 이미지는 어차피 `kind load` 라 인터넷 불요, redis/postgres 베이스만 확보하면 된다.

---

## 트러블슈팅
- **`Cannot connect to the Docker daemon`** — Docker Desktop 실행 안 됨 / Linux 데몬 미기동(`sudo systemctl start docker`) / WSL Integration 토글 OFF.
- **`permission denied ... docker.sock`** — `sudo usermod -aG docker $USER` 후 재로그인(또는 `newgrp docker`).
- **kind create 가 느리거나 멈춤** — 첫 실행은 `kindest/node` 이미지 pull(수백 MB)로 느릴 수 있다. 프록시/네트워크 확인.
- **노드 NotReady / Pod Pending** — Docker 메모리 부족. Desktop Resources 에서 상향(6GB+).
- **WSL2 에서 `kubectl`/`kind` 못 찾음** — Windows 호스트에 깔고 WSL2 에서 찾는 경우. WSL2 안에서 작업한다면 WSL2 Ubuntu 에 직접 설치(C절).
