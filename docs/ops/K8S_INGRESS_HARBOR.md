# K8S_INGRESS_HARBOR.md — ingress-nginx + Harbor 노출 (docker-desktop, S2 노출 정정 / S4 ingress 선행)

> 환경 확정: `kubectl config current-context = docker-desktop`(노드 desktop-control-plane/worker, kind 프로비저너).
> → **LoadBalancer 서비스가 `localhost` 로 자동 매핑**된다(port-forward 와 달리 Docker Desktop 이 호스트에 실제 게시 → **데몬도 도달**).
> 이 문서가 `HARBOR_SETUP.md` 의 "NodePort+localhost 자동노출" 전제를 **정정**한다(이 환경은 NodePort 는 호스트 비노출, LoadBalancer 만 localhost). port-forward 졸업.

---

## 0. 왜 ingress 인가 (이 환경 기준)

| 노출 방식 | 호스트 도달 | 데몬(VM) 도달 | 재부팅 후 |
|---|---|---|---|
| `kubectl port-forward` | ⭕(127.0.0.1) | ❌(데몬 localhost 다름) | 매번 재실행 |
| NodePort | ❌(이 환경 비노출) | ❌ | — |
| **ingress-nginx(LoadBalancer)** | ⭕(localhost:80) | ⭕(Docker Desktop 게시 포트) | **자동 유지** |

→ Harbor 를 ingress 로 노출하면 push(데몬)·포털(호스트)·향후 앱 게이트웨이를 **한 컨트롤러(:80)에서 Host 라우팅**으로 처리. 재부팅마다 port-forward 불요.

## 1. ingress-nginx 설치 (LoadBalancer)

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx && helm repo update
helm install ingress-nginx ingress-nginx/ingress-nginx -n ingress-nginx --create-namespace \
  --set controller.service.type=LoadBalancer

kubectl -n ingress-nginx wait --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller --timeout=180s
kubectl -n ingress-nginx get svc ingress-nginx-controller   # EXTERNAL-IP 가 'localhost' 여야 함
```
> EXTERNAL-IP 가 `localhost`(또는 `127.0.0.1`)면 정상 — Docker Desktop 이 :80/:443 을 호스트에 게시.

## 2. Harbor 를 ingress 노출로 전환 (NodePort → ingress)

```bash
helm upgrade harbor harbor/harbor -n harbor --reuse-values \
  --set expose.type=ingress \
  --set expose.tls.enabled=false \
  --set expose.ingress.className=nginx \
  --set expose.ingress.hosts.core=harbor.local \
  --set externalURL=http://harbor.local
kubectl -n harbor rollout restart deploy     # externalURL 반영(core 등 재기동)
kubectl -n harbor get ingress                 # harbor-ingress, HOSTS=harbor.local
```
> HTTP 인데 308 https 리다이렉트가 나면(드묾): `--set "expose.ingress.annotations.nginx\.ingress\.kubernetes\.io/ssl-redirect=false"` 추가해 재upgrade.

## 3. 이름 해소 — `harbor.local` (호스트 + 데몬 둘 다)

데몬(VM)이 `harbor.local` 을 풀어야 push 가 된다. **Docker Desktop 데몬은 Windows hosts 를 참조**하므로 양쪽에 추가:

- **Windows** `C:\Windows\System32\drivers\etc\hosts` (관리자 메모장):
  ```
  127.0.0.1 harbor.local
  ```
- **WSL** `/etc/hosts` (호스트 curl 용):
  ```
  127.0.0.1 harbor.local
  ```

## 4. insecure-registries (HTTP 허용)

`harbor.local` 은 `localhost` 가 아니므로 docker 가 HTTPS 로 시도 → HTTP Harbor 와 어긋난다. **Docker Desktop → Settings → Docker Engine** 에 추가 후 **Apply & Restart**:
```json
{
  "insecure-registries": ["harbor.local"]
}
```

## 5. 검증 + 프로젝트 + 푸시

```bash
# 호스트 해소 확인
curl -s -o /dev/null -w "%{http_code}\n" http://harbor.local/api/v2.0/systeminfo     # 200
# 데몬 해소 확인(이게 핵심 — 여기서 막히면 §3 Windows hosts / §4 재시작 점검)
docker login harbor.local -u admin -p Harbor12345                                     # Login Succeeded

# 프로젝트(아직 없으면)
curl -u admin:Harbor12345 -X POST http://harbor.local/api/v2.0/projects \
  -H "Content-Type: application/json" -d '{"project_name":"si-msa","metadata":{"public":"false"}}'

# 양태그 푸시 — 레지스트리 주소를 harbor.local 로
REGISTRY=harbor.local ./deploy/cicd/harbor-push.sh
```
포털 `http://harbor.local`(admin/Harbor12345) → `si-msa` 에 4 repo × 2 태그 확인 = **S2 push 완료**.

## 6. ⚠️ pull 측 (S3) — 여전히 별개

이건 **호스트/데몬 push 경로**를 푼 것이다. **노드 containerd 의 pull 은 또 다른 문제**(노드 안에서 `harbor.local` 해소 안 됨). S3(dev apply)에서 둘 중 하나로 처리:
- 이미지 ref 를 **인-클러스터 Service** 로(`harbor-core.harbor.svc.cluster.local`) 두고 push 도 그 이름으로 — 단 데몬이 그 이름을 못 푼다(비대칭 재발).
- 또는 노드 containerd 에 `harbor.local` → ingress/Harbor svc 매핑(hosts.toml) — docker-desktop 노드 진입(`docker exec -it desktop-worker sh`).
- 또는 registry-mirror 자동노출(이 환경 특성)로 pull 이 생략되는지 S3 에서 실거동 확인.
→ S3 에서 `kubectl describe pod` Events 로 트리아지.

## 7. 완료 정의 (S2, ingress 경로)
- ingress-nginx EXTERNAL-IP=localhost.
- `harbor.local` 포털/`docker login` 양쪽 도달.
- `si-msa` 에 4×2 태그 push.
- port-forward 불요(재부팅 후에도 ingress 유지).
