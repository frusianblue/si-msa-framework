> # ⚠️ 노출 방식 정정(2026-06-06 세션2) — 이 문서의 "NodePort + localhost 자동노출" 전제는 **이 환경(`docker-desktop`)에서 틀렸다.** NodePort 는 호스트/데몬 비노출. **Harbor 노출은 `K8S_INGRESS_HARBOR.md`(ingress-nginx LoadBalancer 경로)를 따른다.** 아래 §1 설치값(persistence/admin/project/imagePullSecrets/push 스크립트)·§5 pull 함정은 유효하므로 참고용 보존.

# HARBOR_SETUP.md — 로컬 Harbor(NodePort) 설치 + 이미지 푸시 (S2)

> 목적: 현재 Docker Desktop kind 환경에서 **실 레지스트리 경로(불변 태그 push → imagePullSecrets pull)**를 리허설.
> 이번 단계 = **NodePort + HTTP**(단순). TLS/ingress 노출은 S4(ingress-nginx)에서 얹는다.
> 선행: S1 영속 postgres 검증 완료. 절차/트러블슈팅 `LOCAL_K8S_TEST.md`. ⚠️ 이 문서는 docker-desktop kind(NodePort+HTTP) 전제 — standalone kind 인-클러스터 Harbor 런북은 `STANDALONE_KIND_HARBOR_JENKINS.md` 가 정본.

---

## 0. 왜 레지스트리 주소를 `localhost:30002` 로 통일하나 (핵심)

로컬 레지스트리의 고질병 = **호스트가 push 하는 주소와 노드가 pull 하는 주소가 다름**. NodePort 를 쓰면 둘을 한 주소로 묶을 수 있다:
- **호스트 push** → `localhost:30002` : Docker Desktop 이 NodePort 를 호스트 localhost 로 포워딩.
- **노드 pull** → `localhost:30002` : NodePort 는 모든 노드의 `0.0.0.0:30002`(kube-proxy)에 바인딩 → 노드 자신의 localhost 로도 도달.
- **HTTP 신뢰**: docker 는 `localhost`/`127.0.0.1` 레지스트리를 **기본 insecure 허용** → `insecure-registries` 설정 불요(push 측).
  - ⚠️ **단, 노드 containerd 는 localhost HTTP 를 자동 신뢰하지 않음** → pull 측은 §5(S3 에서 처리).

## 1. Harbor 설치 (Helm, NodePort, HTTP, 영속)

```bash
helm repo add harbor https://helm.goharbor.io && helm repo update
kubectl create namespace harbor

helm install harbor harbor/harbor -n harbor \
  --set expose.type=nodePort \
  --set expose.tls.enabled=false \
  --set expose.nodePort.ports.http.nodePort=30002 \
  --set externalURL=http://localhost:30002 \
  --set harborAdminPassword=Harbor12345 \
  --set persistence.enabled=true            # registry/db/redis/jobservice/trivy PVC(기본 SC) — 영속·SC 재검증 겸

kubectl -n harbor get pods -w               # 전 컴포넌트 Running/Ready (수 분 소요)
kubectl -n harbor get pvc                    # 각 컴포넌트 PVC Bound (WaitForFirstConsumer → 파드 스케줄 후)
```

> 무겁다 싶으면 `--set trivy.enabled=false` 로 취약점 스캐너 제외 가능(리허설엔 불요).

## 2. 접속 확인 + 프로젝트 생성

```bash
# 호스트 /etc/hosts 불요(localhost 사용). 포털:  http://localhost:30002  (admin / Harbor12345)
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:30002/api/v2.0/systeminfo   # 200

# 프로젝트 si-msa — private(=imagePullSecrets 리허설 목적). public 으로 하면 pull 인증 불요(단순).
curl -u admin:Harbor12345 -X POST http://localhost:30002/api/v2.0/projects \
  -H "Content-Type: application/json" \
  -d '{"project_name":"si-msa","metadata":{"public":"false"}}'
```

## 3. 이미지 빌드 + 양태그 푸시

```bash
# 이미지가 없으면 먼저 빌드(compose 산출 재사용):
docker compose -f deploy/compose/docker-compose.yml build   # → si-msa/<svc>:local 4개

# 양태그(:<git-sha> + 채널) 태깅/푸시
chmod +x deploy/cicd/harbor-push.sh
./deploy/cicd/harbor-push.sh                # CHANNEL=dev 기본. semver 면 CHANNEL=1.0.0 ./...
```
푸시 후 포털 `si-msa` 프로젝트에 4 repo × 2 태그(`<sha>`,`dev`) 확인.

## 4. imagePullSecrets (private 프로젝트 pull 자격)

```bash
kubectl -n si-msa create secret docker-registry harbor-cred \
  --docker-server=localhost:30002 \
  --docker-username=admin --docker-password=Harbor12345

# default SA 에 부착 → si-msa ns 의 모든 파드가 자동 사용(deployment 별 부착 대신 1회)
kubectl -n si-msa patch serviceaccount default \
  -p '{"imagePullSecrets":[{"name":"harbor-cred"}]}'
```
> kustomize 로 고정하려면 overlay 의 deployment 패치에 `spec.template.spec.imagePullSecrets: [{name: harbor-cred}]` 추가(다음 드롭에서 prod-rehearsal overlay 에 포함).

## 5. ⚠️ pull 측 함정 (S3 에서 처리 — 지금 막히면 정상)

노드 **containerd 는 `localhost:30002` HTTP 를 자동 신뢰하지 않음** → S3 에서 파드가 `ErrImagePull`(http/x509)로 막힐 수 있다. 처리:
- **standalone kind**: kind 설정에 `containerdConfigPatches` 로 `localhost:30002` 를 http+skip_verify 등록 후 클러스터 재생성(또는 노드의 `/etc/containerd/certs.d/localhost:30002/hosts.toml` 추가 + `systemctl restart containerd`).
- **Docker Desktop k8s(kind 프로비저너)**: 노드 컨테이너 진입(`docker exec -it <node> sh`)해 동일 `hosts.toml` 작성 후 containerd 재시작. (registry-mirror 자동노출 모드라 push 한 이미지가 이미 보이면 pull 자체가 생략될 수도 있음 — S3 에서 실제 거동으로 확인.)
- 막히면 **그 시점의 `kubectl describe pod`(Events)** 를 공유 → 환경에 맞는 hosts.toml/설정으로 트리아지.

## 6. 완료 정의 (S2)
- Harbor 전 컴포넌트 Running + PVC Bound.
- `si-msa` 프로젝트에 4서비스 × (`<git-sha>`,`channel`) 푸시 확인.
- `harbor-cred` 시크릿 + default SA 부착.
- (pull 검증은 S3 dev apply 에서)
