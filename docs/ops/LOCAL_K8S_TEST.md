# 로컬 k8s 테스트 가이드 (kind) — si-msa-framework

`deploy/k8s/overlays/local` 오버레이로 4개 서비스(gateway/auth-server/user-service/admin-service)를
로컬 클러스터에 한 줄로 띄워 **실제 기동·헬스·DB 연결**을 확인하는 절차다.

local 오버레이가 dev/prod 와 다른 점:
- **인-클러스터 Postgres 동봉**(`postgres.yaml`) — `authdb`/`sidb` 두 DB 와 역할(authuser/siuser)을 initdb 로 생성. base 의 `DB_URL`(`postgres:5432/...`)이 그대로 풀려 **DB_URL 패치 없음**.
- **ServiceMonitor 제거** — 로컬엔 Prometheus Operator CRD 가 없으므로 `$patch: delete` 로 뺀다(없으면 apply 전체 실패).
- **로컬 빌드 이미지 `:local`** — 가짜 레지스트리(`registry.example.com`) 대신 노드에 직접 적재. `:latest` 가 아니라 기본 `imagePullPolicy=IfNotPresent` → 레지스트리 접속 안 함.
- **강한 시크릿**(`secrets-local.yaml`) — base 가 `prod` 프로파일로 뜨므로 dev 의 placeholder AES 는 `AesMasterKeySafetyGuard` 에 막힌다. 가드를 통과하는 32바이트 AES 등 로컬 전용 값 동봉.

---

## 0. 사전 준비

- Docker 데몬
- kubectl (kustomize 내장 — 별도 설치 불필요)
- kind (`go install sigs.k8s.io/kind@latest` 또는 `brew install kind` / `choco install kind`)
- Docker 메모리 ≥ 6GB 권장(서비스 4개 × 요청 512Mi + redis + postgres)

> Docker/kubectl/kind 설치를 OS별(macOS/Windows+WSL2/Linux)로 푼 가이드는 `docs/modules/LOCAL_K8S_ENV_SETUP.md` 참고.

---

## 1. 클러스터 생성

```bash
kind create cluster --name si-msa
kubectl cluster-info --context kind-si-msa
```

---

## 2. 이미지 빌드 후 노드에 적재 (:local)

```bash
for svc in gateway auth-server user-service admin-service; do
  ./gradlew :services:$svc:bootJar
  docker build -f deploy/docker/Dockerfile \
    --build-arg JAR_FILE=services/$svc/build/libs/$svc-1.0.0.jar \
    -t registry.example.com/si-msa/$svc:local .
  kind load docker-image registry.example.com/si-msa/$svc:local --name si-msa
done
```

> minikube 면 `minikube image load registry.example.com/si-msa/$svc:local`.
> Docker Desktop 내장 k8s 면 같은 데몬이라 적재 단계가 아예 불필요(빌드만 하면 됨).

---

## 3. 배포 (한 줄)

```bash
kubectl apply -k deploy/k8s/overlays/local
```

namespace `si-msa` 에 redis + postgres + 4개 서비스 + 각 ConfigMap/Secret/Service 가 생성된다.

---

## 4. 기동 확인

```bash
kubectl -n si-msa get pods -w
```

- postgres·redis 가 먼저 `Running` → 그다음 서비스들이 뜬다.
- **앱이 처음 몇 번 `CrashLoopBackOff` 후 살아나는 건 정상**: postgres 가 준비되기 전에 먼저 떠서 DB 연결에 실패하면 k8s 가 재시작하고, postgres 가 ready 되면 Flyway 마이그레이션 후 정상화된다. startup 프로브가 최대 ~150초 유예를 준다.

```bash
kubectl -n si-msa rollout status deploy/auth-server
kubectl -n si-msa get deploy,svc,pods
```

다 안 뜨면 원인 확인:
```bash
kubectl -n si-msa logs deploy/auth-server
kubectl -n si-msa describe pod <pod>
```

---

## 5. 스모크 체크 (port-forward)

```bash
# auth-server 헬스/프로브/메트릭
kubectl -n si-msa port-forward svc/auth-server 9000:9000 &
curl -s localhost:9000/actuator/health/liveness
curl -s localhost:9000/actuator/health/readiness
curl -s localhost:9000/actuator/prometheus | head        # micrometer 메트릭 노출 확인
curl -s localhost:9000/.well-known/openid-configuration   # SAS discovery

# gateway
kubectl -n si-msa port-forward svc/gateway 8000:8000 &
curl -s localhost:8000/actuator/health
```

DB 직접 확인:
```bash
kubectl -n si-msa exec -it deploy/postgres -- psql -U postgres -c "\l"   # authdb, sidb 존재
kubectl -n si-msa exec -it deploy/postgres -- psql -U authuser -d authdb -c "\dt"   # Flyway 테이블
```

---

## 6. 정리

```bash
kind delete cluster --name si-msa
```

local 의 postgres 는 `emptyDir`(휘발)이라 클러스터를 지우면 데이터도 사라진다(로컬 재현용).

---

## 7. (선택) 관측까지 — kube-prometheus-stack

ServiceMonitor·Prometheus 스크레이프까지 보려면 오퍼레이터를 먼저 깔고 local 오버레이의 SM 제거 패치를 뺀다.

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm install kube-prometheus-stack prometheus-community/kube-prometheus-stack -n monitoring --create-namespace
# 그다음 overlays/local/kustomization.yaml 의 patches(ServiceMonitor $patch:delete) 를 주석 처리하고 재적용
```

> SM 의 `release: kube-prometheus-stack` 라벨이 오퍼레이터의 `serviceMonitorSelector` 와 맞아야 스크레이프된다(설치값에 따라 조정).

---

## 8. 트러블슈팅

- **ImagePullBackOff** — 이미지 적재 누락. `kind load docker-image ...:local` 재실행(태그가 `:local` 인지 확인). 노드 적재 목록: `docker exec si-msa-control-plane crictl images | grep si-msa`.
- **Pod Pending(Insufficient cpu/memory)** — Docker 메모리 상향, 또는 일시적으로 서비스 수를 줄여 테스트. 요청값은 `base/common/deployment-hardening.yaml`(250m/512Mi).
- **앱이 계속 CrashLoop(DB)** — postgres ready 확인(`kubectl -n si-msa logs deploy/postgres`), 비번 불일치면 `secrets-local.yaml` 의 DB_USER/DB_PASSWORD 와 `postgres.yaml` initdb 역할이 일치하는지 확인.
- **앱이 부팅 차단(AES/JWT)** — prod 프로파일 가드. `secrets-local.yaml` 의 강한 값을 쓰는지(dev 시크릿을 잘못 끌어오지 않았는지) 확인.
- **apply 시 `no matches for kind "ServiceMonitor"`** — SM 제거 패치가 적용 안 됨. local 오버레이로 배포했는지(`-k deploy/k8s/overlays/local`) 확인.

---

## 검증 사다리 요약

| 단계 | 명령 | 클러스터 |
|---|---|---|
| 렌더링 | `kubectl kustomize deploy/k8s/overlays/local` | 불요 |
| 드라이런 | `kubectl apply -k deploy/k8s/overlays/local --dry-run=server` | 필요 |
| 실기동 | `kubectl apply -k deploy/k8s/overlays/local` | 필요(+이미지 적재) |
