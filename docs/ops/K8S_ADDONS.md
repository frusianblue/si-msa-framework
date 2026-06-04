# K8s 애드온 가이드 — 서비스 구동을 위해 클러스터에 추가 설치하는 것들

`deploy/k8s` 매니페스트는 클러스터에 이미 존재한다고 **전제하는 구성요소**가 있다. 빈 클러스터(kind 기본)에는
없으므로, 기능에 따라 아래를 추가로 설치해야 실제로 뜨고/오토스케일/관측이 된다.
(클러스터 도구 설치 = `docs/modules/LOCAL_K8S_ENV_SETUP.md`, 배포 = `docs/modules/LOCAL_K8S_TEST.md`)

## 무엇이 무엇을 요구하나 (매핑)

| 매니페스트 / 기능 | 요구하는 클러스터 구성요소 | 설치 | 어느 환경 |
|---|---|---|---|
| `ServiceMonitor`(관측) | Prometheus Operator CRD | **kube-prometheus-stack** (Helm) | 관측 볼 때(dev/prod, 선택 local) |
| `HPA`(오토스케일, prod 오버레이) | **metrics-server** | metrics-server (Helm/매니페스트) | prod (HPA 쓰면) |
| gateway 외부 노출 | Ingress Controller | **ingress-nginx** (선택) | 외부 접근 시(아니면 port-forward) |
| prod 시크릿 주입 | 시크릿 오퍼레이터 | **External Secrets Operator** 또는 **Sealed Secrets** | prod |
| DB(`authdb`/`sidb`) | PostgreSQL | local=동봉(`overlays/local`)·dev/prod=외부/매니지드 | 전부 |
| 캐시/토큰/레이트리밋 | Redis | local/dev=인-클러스터(base)·prod=매니지드 권장 | 전부 |

**환경별 최소 셋**
- **local(kind, 기능 확인)**: 추가 설치 0 — `overlays/local` 이 PG 동봉 + ServiceMonitor 제거. 그냥 뜬다.
- **local + 관측까지**: `kube-prometheus-stack` 설치 후 local 의 SM 제거 패치를 뺀다.
- **local + HPA 실험**: `metrics-server` 설치(+kind 용 insecure-tls).
- **prod**: metrics-server(HPA) + 시크릿 오퍼레이터 + (관측) kube-prometheus-stack + (선택) ingress-nginx + 외부 PG/Redis.

---

## 0. Helm — 대부분의 애드온 설치 도구 (먼저 설치)

```bash
# macOS / Linux(WSL2)
brew install helm        # 또는: curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
# Windows 네이티브
winget install -e --id Helm.Helm
helm version
```

---

## 1. metrics-server  (HPA · `kubectl top`)

HPA(`overlays/prod/hpa.yaml`)는 CPU 사용률(Utilization 70%) 기준이라 metrics-server 가 있어야 동작한다.
없으면 HPA 가 `unknown` 으로 스케일하지 못한다.

```bash
helm repo add metrics-server https://kubernetes-sigs.github.io/metrics-server/
helm repo update
# kind 는 kubelet 인증서가 self-signed 라 --kubelet-insecure-tls 가 필요하다
helm install metrics-server metrics-server/metrics-server -n kube-system \
  --set 'args={--kubelet-insecure-tls}'
```
확인:
```bash
kubectl top nodes        # 값이 나오면 OK (초기 수집까지 1~2분)
```
> 실 운영 클러스터는 보통 metrics-server 가 이미 있거나 매니지드(EKS 애드온 등)로 제공된다 — insecure-tls 는 빼고.

---

## 2. kube-prometheus-stack  (ServiceMonitor / Prometheus / Grafana)

base 의 `ServiceMonitor`(`si-msa-services`)는 **Prometheus Operator CRD** 가 있어야 적용된다. 이 스택이
Operator + Prometheus + Grafana + Alertmanager + CRD 를 한 번에 깐다.

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace
```

그다음 **si-msa 의 ServiceMonitor 가 스크레이프되게** 두 가지를 맞춘다:
1. `overlays/local` 을 쓰는 경우, ServiceMonitor 제거 패치(`$patch: delete`)를 **주석 처리하고 재적용**한다(없애야 SM 이 생성됨).
2. SM 의 라벨 `release: kube-prometheus-stack` 이 오퍼레이터의 `serviceMonitorSelector` 와 맞아야 한다(위 helm release 이름과 동일하게 설치하면 기본적으로 맞음. 다르면 조정).

접속(port-forward):
```bash
kubectl -n monitoring port-forward svc/kube-prometheus-stack-grafana 3000:80   # Grafana (admin/ 초기 비번은 secret)
kubectl -n monitoring port-forward svc/kube-prometheus-stack-prometheus 9090:9090
```
Prometheus Targets 화면에서 si-msa 서비스 4종의 `/actuator/prometheus` 가 UP 인지 확인.

---

## 3. ingress-nginx  (선택 — 외부 노출, port-forward 대체)

로컬 테스트는 `port-forward` 로 충분하다. gateway 를 호스트에서 `http://localhost` 로 직접 치고 싶을 때만 깐다.

kind 는 클러스터 생성 시 80/443 포트 매핑이 필요하다(`LOCAL_K8S_ENV_SETUP.md` 의 kind-config 예시).
```bash
kubectl apply -f https://kind.sigs.k8s.io/examples/ingress/deploy-ingress-nginx.yaml
kubectl -n ingress-nginx wait --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller --timeout=120s
```
> ⚠️ **gateway 용 Ingress 리소스(host/path 라우팅)는 아직 매니페스트에 없다**(백로그). 컨트롤러만 깔아두고,
> 노출이 필요하면 gateway Service(8000) 를 가리키는 Ingress 를 별도 작성해야 한다. 그 전까지는 port-forward 가 정석.

---

## 4. prod 시크릿 주입  (External Secrets / Sealed Secrets)

`overlays/prod` 는 Secret 을 **커밋하지 않는다**(의도). 클러스터에 `<svc>-secret` 4개가 미리 있어야 파드가 뜬다.
둘 중 하나를 도입한다(둘 다 Helm):

- **External Secrets Operator** — AWS Secrets Manager/Vault 등 외부 저장소를 `ExternalSecret` 으로 동기화.
```bash
helm repo add external-secrets https://charts.external-secrets.io
helm install external-secrets external-secrets/external-secrets -n external-secrets --create-namespace
```
- **Sealed Secrets** — 암호화본(SealedSecret)만 커밋, 클러스터 컨트롤러가 복호화.
```bash
helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
helm install sealed-secrets sealed-secrets/sealed-secrets -n kube-system
```
양식·필요 키는 `overlays/prod/secrets-prod.example.yaml` 참고. local/dev 는 시크릿이 동봉되어 이 단계 불필요.

---

## 5. PostgreSQL / Redis

- **local**: `overlays/local` 이 인-클러스터 PG(authdb/sidb)+Redis 를 동봉 → 추가 설치 없음.
- **dev**: base 의 인-클러스터 Redis 사용, DB 는 개발 서버(외부) 가리킴 → 외부 PG 준비.
- **prod**: **매니지드 권장** — RDS/Aurora(PG), ElastiCache(Redis). 오버레이에서 `DB_URL`/`REDIS_HOST` 를 외부 엔드포인트로 치환. 인-클러스터 PG/Redis 는 prod 비권장(영속/HA/백업 부담).

---

## 설치 순서 요약 (관측+오토스케일까지 보는 로컬 풀셋, kind)

```bash
# 1) 도구 (LOCAL_K8S_ENV_SETUP.md) — Docker/kubectl/kind/helm
# 2) 클러스터 (포트매핑 원하면 config 동반)
kind create cluster --name si-msa
# 3) 애드온
helm install metrics-server metrics-server/metrics-server -n kube-system --set 'args={--kubelet-insecure-tls}'
helm install kube-prometheus-stack prometheus-community/kube-prometheus-stack -n monitoring --create-namespace
# 4) si-msa 배포 (관측 보려면 local 오버레이의 SM 제거 패치를 먼저 뺀다)
#    + 이미지 4종 kind load  (LOCAL_K8S_TEST.md)
kubectl apply -k deploy/k8s/overlays/local
```

> Helm 차트 버전은 시간이 지나면 올라간다 — `helm search repo <chart>` 로 확인하고 필요 시 `--version` 으로 고정한다.
