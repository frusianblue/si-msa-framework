#!/usr/bin/env bash
# deploy/k8s/standalone-kind/08-harbor-install.sh
# ─────────────────────────────────────────────────────────────────────────────
# 인-클러스터 Harbor 설치(Helm, NodePort 30002, HTTP, 영속 PVC) + 노드/인-클러스터 이름해소 통일.
#
# split-horizon 해법(핵심):
#   노드 containerd 와 인-클러스터 파드(Kaniko)가 *같은 엔드포인트*로 Harbor 에 닿게 한다.
#   → control-plane 노드 IP(kind net 172.18.x)를 단일 좌표로:
#       · externalURL          = http://<CP_IP>:30002   (토큰 realm 도 이 주소 → 노드/파드 모두 도달)
#       · CoreDNS hosts        : harbor.local → <CP_IP>  (인-클러스터 파드가 harbor.local push 가능)
#       · 노드 certs.d         : harbor.local → http://<CP_IP>:30002 (노드 pull)
#   이미지 ref 는 그대로 harbor.local/si-msa/<svc>:<tag> (오버레이 무수정).
#
# 전제: 01/02 로 kind-sanity + registry-trust DaemonSet(07 또는 아래 자동 적용). helm CLI 필요.
# 실행: bash deploy/k8s/standalone-kind/08-harbor-install.sh
# 멱등: helm upgrade --install / project 존재 시 skip / ConfigMap patch.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

CLUSTER="${CLUSTER:-sanity}"; CTX="kind-${CLUSTER}"
CP_NODE="${CP_NODE:-${CLUSTER}-control-plane}"
HARBOR_NS="harbor"; NODEPORT="${NODEPORT:-30002}"
ADMIN="admin"; PASS="${HARBOR_PASS:-Harbor12345}"; PROJECT="si-msa"

echo "== 0) 전제 =="
for b in docker kind kubectl helm; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done
kind get clusters 2>/dev/null | grep -qx "$CLUSTER" || { echo "FAIL: kind-$CLUSTER 없음"; exit 1; }

echo "== 1) control-plane 노드 IP(kind net) 산출 =="
CP_IP="$(docker inspect -f '{{(index .NetworkSettings.Networks "kind").IPAddress}}' "$CP_NODE" 2>/dev/null || true)"
[ -n "$CP_IP" ] || { echo "FAIL: $CP_NODE 의 kind 네트워크 IP 산출 실패"; exit 1; }
echo "  CP_IP=$CP_IP  → externalURL=http://$CP_IP:$NODEPORT"

echo "== 2) registry-trust DaemonSet 적용(없으면) =="
kubectl --context "$CTX" apply -f registry-trust-daemonset.yaml

echo "== 3) Harbor 설치(Helm) =="
helm repo add harbor https://helm.goharbor.io >/dev/null 2>&1 || true
helm repo update >/dev/null
kubectl --context "$CTX" create namespace "$HARBOR_NS" >/dev/null 2>&1 || true
helm --kube-context "$CTX" upgrade --install harbor harbor/harbor -n "$HARBOR_NS" \
  -f harbor-values.yaml \
  --set externalURL="http://$CP_IP:$NODEPORT" \
  --wait --timeout 10m

echo "== 4) Harbor 컴포넌트 Ready + PVC Bound =="
kubectl --context "$CTX" -n "$HARBOR_NS" get pods
kubectl --context "$CTX" -n "$HARBOR_NS" get pvc

echo "== 5) si-msa 프로젝트 생성(public — 로컬 dev pull 무인증 단순화) =="
# 임시 curl 파드가 인-클러스터 core 서비스(harbor-core.harbor.svc)로 직접 POST.
#   (노드 wget 가용성/따옴표 의존 제거. CoreDNS 패치(§6) 이전에도 표준 svc DNS 로 해소됨.)
#   이미 있으면 409 — 무해(--rm 즉시 정리).
kubectl --context "$CTX" -n "$HARBOR_NS" run harbor-init-$$ --rm -i --restart=Never \
  --image=curlimages/curl:latest --quiet -- \
  -s -u "$ADMIN:$PASS" -X POST "http://harbor-core.${HARBOR_NS}.svc.cluster.local/api/v2.0/projects" \
  -H "Content-Type: application/json" \
  -d "{\"project_name\":\"$PROJECT\",\"metadata\":{\"public\":\"true\"}}" \
  -w 'HTTP=%{http_code}\n' || echo "  (프로젝트 생성 응답 비정상 — 이미 존재 409 면 무해)"

echo "== 6) CoreDNS 에 harbor.local → $CP_IP 주입(인-클러스터 파드용) =="
# Corefile 에 hosts 블록 추가(멱등: 이미 있으면 교체). kube-system/coredns ConfigMap 패치.
CURRENT="$(kubectl --context "$CTX" -n kube-system get cm coredns -o jsonpath='{.data.Corefile}')"
if printf '%s' "$CURRENT" | grep -q "harbor.local"; then
  echo "  이미 harbor.local 매핑 존재 — IP 갱신 위해 재적용"
fi
# hosts 플러그인 블록을 .:53 블록 맨 앞(ready 다음)에 삽입. 간단화를 위해 별도 서버블록으로 추가.
PATCHED="$(printf '%s\n' "$CURRENT" | awk -v ip="$CP_IP" '
  BEGIN{added=0}
  /^harbor\.local:53 \{/{skip=1}
  skip && /^\}/{skip=0; next}
  skip{next}
  {print}
  END{
    print "harbor.local:53 {"
    print "    hosts {"
    print "        " ip " harbor.local"
    print "        fallthrough"
    print "    }"
    print "}"
  }')"
kubectl --context "$CTX" -n kube-system create cm coredns \
  --from-literal=Corefile="$PATCHED" --dry-run=client -o yaml \
  | kubectl --context "$CTX" -n kube-system apply -f - >/dev/null
kubectl --context "$CTX" -n kube-system rollout restart deploy/coredns
kubectl --context "$CTX" -n kube-system rollout status deploy/coredns --timeout=120s

echo "== 7) 노드 certs.d 미러를 Harbor(NodePort)로 전환 =="
# registry-hosts ConfigMap 의 harbor.local 값을 CP_IP:30002 로 교체 + DS rollout + 즉효 직접기입.
kubectl --context "$CTX" -n registry-system create cm registry-hosts \
  --from-literal=harbor.local="$(printf '[host."http://%s:%s"]\n  capabilities = ["pull", "resolve"]\n' "$CP_IP" "$NODEPORT")" \
  --from-literal=reg.local="$(printf '[host."http://kind-registry:5000"]\n  capabilities = ["pull", "resolve"]\n')" \
  --dry-run=client -o yaml | kubectl --context "$CTX" apply -f - >/dev/null
kubectl --context "$CTX" -n registry-system rollout restart ds/registry-trust
for n in $(docker ps --format '{{.Names}}' | grep "^${CLUSTER}-"); do
  docker exec "$n" mkdir -p /etc/containerd/certs.d/harbor.local
  docker exec "$n" sh -c "printf '[host.\"http://$CP_IP:$NODEPORT\"]\n  capabilities = [\"pull\", \"resolve\"]\n' > /etc/containerd/certs.d/harbor.local/hosts.toml"
done

echo
echo "──────────────────────────────────────────────────────────────"
echo "✅ Harbor 설치 완료."
echo "   포털(호스트):  kubectl -n harbor port-forward svc/harbor 8080:80   → http://localhost:8080 ($ADMIN/$PASS)"
echo "   externalURL : http://$CP_IP:$NODEPORT   (노드/파드 공통 좌표)"
echo "   다음: 09-jenkins-install.sh → Jenkinsfile.kind 파이프라인(Kaniko build→push harbor.local→deploy)."
echo "   검증(노드 pull 경로): 09 파이프라인 첫 실행 또는 수동 push 후 dev overlay 롤아웃에서 Pulled 확인."
