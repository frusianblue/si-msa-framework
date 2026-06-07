#!/usr/bin/env bash
# deploy/k8s/standalone-kind/08-harbor-install.sh
# ─────────────────────────────────────────────────────────────────────────────
# 인-클러스터 Harbor 설치(Helm) — 【B안 (2026-06-07 세션4) — ingress 노출(harbor.local)】.
# 이전 NodePort(30002) 판을 대체. 호스트는 http://harbor.local 로 port-forward 없이 접속.
#
# split-horizon 해법(ingress 판): 세 주체가 같은 Harbor(ingress, Host=harbor.local)에 닿게 한다.
#   ┌ 주체 ─────────────┬ 경로 ───────────────────────────────────────────────┐
#   │ 호스트 브라우저     │ http://harbor.local → (hosts: 127.0.0.1) → :80(extraPortMappings) → ingress │
#   │ 인-클러스터 Kaniko  │ harbor.local → (CoreDNS hosts: INGRESS_CLUSTERIP) → ingress │
#   │ 노드 containerd     │ harbor.local → (노드 /etc/hosts: CP_IP) → CP:80(ingress hostPort) → ingress │
#   └────────────────────┴───────────────────────────────────────────────────┘
#   externalURL=http://harbor.local → 토큰 realm 도 같은 이름 → 세 주체 모두 해소 가능.
#   이미지 ref 는 그대로 harbor.local/si-msa/<svc>:<tag>(오버레이 무수정).
#
# 전제: 01-pull-sanity PASS + 10-ingress-nginx 설치(컨트롤러 Ready) + helm CLI.
# 실행: bash deploy/k8s/standalone-kind/08-harbor-install.sh
# 멱등: helm upgrade --install / project 409 무해 / CoreDNS·registry-hosts patch.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

CLUSTER="${CLUSTER:-sanity}"; CTX="kind-${CLUSTER}"
CP_NODE="${CP_NODE:-${CLUSTER}-control-plane}"
HARBOR_NS="harbor"; HOSTNAME_HARBOR="harbor.local"
ADMIN="admin"; PASS="${HARBOR_PASS:-Harbor12345}"; PROJECT="si-msa"

echo "== 0) 전제 =="
for b in docker kind kubectl helm; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done
kind get clusters 2>/dev/null | grep -qx "$CLUSTER" || { echo "FAIL: kind-$CLUSTER 없음"; exit 1; }
kubectl --context "$CTX" -n ingress-nginx get deploy ingress-nginx-controller >/dev/null 2>&1 \
  || { echo "FAIL: ingress-nginx 미설치 → 먼저 10-ingress-nginx.sh"; exit 1; }

echo "== 1) 좌표 산출 (CP_IP, INGRESS_CLUSTERIP) =="
CP_IP="$(docker inspect -f '{{(index .NetworkSettings.Networks "kind").IPAddress}}' "$CP_NODE" 2>/dev/null || true)"
[ -n "$CP_IP" ] || { echo "FAIL: $CP_NODE 의 kind 네트워크 IP 산출 실패"; exit 1; }
INGRESS_CLUSTERIP="$(kubectl --context "$CTX" -n ingress-nginx get svc ingress-nginx-controller -o jsonpath='{.spec.clusterIP}')"
[ -n "$INGRESS_CLUSTERIP" ] || { echo "FAIL: ingress-nginx-controller ClusterIP 산출 실패"; exit 1; }
echo "  CP_IP=$CP_IP (노드 /etc/hosts) · INGRESS_CLUSTERIP=$INGRESS_CLUSTERIP (CoreDNS) · externalURL=http://$HOSTNAME_HARBOR"

echo "== 2) registry-trust DaemonSet 적용(없으면) =="
kubectl --context "$CTX" apply -f registry-trust-daemonset.yaml

echo "== 3) Harbor 설치(Helm, ingress) =="
helm repo add harbor https://helm.goharbor.io >/dev/null 2>&1 || true
helm repo update >/dev/null
kubectl --context "$CTX" create namespace "$HARBOR_NS" >/dev/null 2>&1 || true
helm --kube-context "$CTX" upgrade --install harbor harbor/harbor -n "$HARBOR_NS" \
  -f harbor-values.yaml \
  --set externalURL="http://$HOSTNAME_HARBOR" \
  --wait --timeout 10m

echo "== 4) Harbor 컴포넌트 Ready + PVC Bound + Ingress 생성 확인 =="
kubectl --context "$CTX" -n "$HARBOR_NS" get pods
kubectl --context "$CTX" -n "$HARBOR_NS" get pvc
kubectl --context "$CTX" -n "$HARBOR_NS" get ingress

echo "== 5) si-msa 프로젝트 생성(public — 로컬 dev pull 무인증 단순화) =="
kubectl --context "$CTX" -n "$HARBOR_NS" run harbor-init-$$ --rm -i --restart=Never \
  --image=curlimages/curl:latest --quiet -- \
  -s -u "$ADMIN:$PASS" -X POST "http://harbor-core.${HARBOR_NS}.svc.cluster.local/api/v2.0/projects" \
  -H "Content-Type: application/json" \
  -d "{\"project_name\":\"$PROJECT\",\"metadata\":{\"public\":\"true\"}}" \
  -w 'HTTP=%{http_code}\n' || echo "  (프로젝트 생성 응답 비정상 — 이미 존재 409 면 무해)"

echo "== 6) CoreDNS 에 harbor.local → $INGRESS_CLUSTERIP 주입(인-클러스터 Kaniko push) =="
# Kaniko 파드가 harbor.local 을 ingress-nginx ClusterIP 로 해소 → 인-클러스터 경로(노드 hostPort 우회).
CURRENT="$(kubectl --context "$CTX" -n kube-system get cm coredns -o jsonpath='{.data.Corefile}')"
PATCHED="$(printf '%s\n' "$CURRENT" | awk -v ip="$INGRESS_CLUSTERIP" '
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

echo "== 7) 노드 좌표 전환 — registry-hosts ConfigMap(certs.d http://harbor.local + node-etc-hosts CP_IP) =="
HARBOR_TOML="$(printf '[host."http://%s"]\n  capabilities = ["pull", "resolve"]\n' "$HOSTNAME_HARBOR")"
REG_TOML="$(printf '[host."http://kind-registry:5000"]\n  capabilities = ["pull", "resolve"]\n')"
NODE_HOSTS="$(printf '%s %s\n' "$CP_IP" "$HOSTNAME_HARBOR")"
kubectl --context "$CTX" -n registry-system create cm registry-hosts \
  --from-literal=harbor.local="$HARBOR_TOML" \
  --from-literal=reg.local="$REG_TOML" \
  --from-literal=node-etc-hosts="$NODE_HOSTS" \
  --dry-run=client -o yaml | kubectl --context "$CTX" apply -f - >/dev/null
kubectl --context "$CTX" -n registry-system rollout restart ds/registry-trust
kubectl --context "$CTX" -n registry-system rollout status ds/registry-trust --timeout=120s || true

echo "== 8) 즉효 직접기입(노드 certs.d + /etc/hosts) — DS 스케줄 대기 없이 바로 =="
for n in $(docker ps --format '{{.Names}}' | grep "^${CLUSTER}-"); do
  docker exec "$n" mkdir -p "/etc/containerd/certs.d/$HOSTNAME_HARBOR"
  docker exec "$n" sh -c "printf '[host.\"http://$HOSTNAME_HARBOR\"]\n  capabilities = [\"pull\", \"resolve\"]\n' > /etc/containerd/certs.d/$HOSTNAME_HARBOR/hosts.toml"
  # /etc/hosts 멱등 병합(같은 이름 줄 제거 후 추가)
  docker exec "$n" sh -c "grep -v '[[:space:]]$HOSTNAME_HARBOR\$' /etc/hosts > /tmp/h 2>/dev/null || true; echo '$CP_IP $HOSTNAME_HARBOR' >> /tmp/h; cat /tmp/h > /etc/hosts"
  echo "  $n: certs.d + /etc/hosts($CP_IP $HOSTNAME_HARBOR)"
done

echo
echo "──────────────────────────────────────────────────────────────"
echo "✅ Harbor 설치 완료 (ingress, http://$HOSTNAME_HARBOR)."
echo "   호스트 접속 전제: Windows hosts + WSL /etc/hosts 에  '127.0.0.1 harbor.local jenkins.local'"
echo "   포털:  http://$HOSTNAME_HARBOR  ($ADMIN/$PASS)"
echo "   좌표:  노드 /etc/hosts→$CP_IP · CoreDNS→$INGRESS_CLUSTERIP · realm=http://$HOSTNAME_HARBOR"
echo "   다음: 09-jenkins-install.sh → 11-host-access-verify.sh → Jenkinsfile.kind 파이프라인 1회."
