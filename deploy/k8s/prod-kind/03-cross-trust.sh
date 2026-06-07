#!/usr/bin/env bash
# deploy/k8s/prod-kind/03-cross-trust.sh
# ─────────────────────────────────────────────────────────────────────────────
# 1단계 (4/4): 노드 Harbor 신뢰 — registry-trust DaemonSet 의 멀티클러스터판.
#   kind-svc 노드 containerd 가 Harbor(kind-cicd 에 있음)를 신뢰·해소해야 함(ArgoCD 는 pull 을 안 풀어줌, §2).
#   같은 `kind` 도커망이라 kind-svc 노드는 kind-cicd CP 노드 IP 로 도달 →
#   harbor.local → (노드 /etc/hosts: CICD_CP_IP) → :80(cicd ingress hostPort) → ingress → harbor-core.
#
#   기존 standalone-kind/registry-trust-daemonset.yaml 을 그대로 재사용(단일 소스) — ConfigMap 만
#   cicd CP IP 로 덮어쓴다. 08-harbor-install.sh 의 §7/§8(ConfigMap + 노드 직접기입) 패턴과 동일.
#
# ⚠️ Harbor 본체 설치는 2단계(ArgoCD/hub 이후). 따라서 이 단계는 *신뢰 배선 + cicd 도달* 까지만 보장.
#    실제 harbor.local pull 실증은 2단계(Harbor 설치 후).
#
# 실행:  bash deploy/k8s/prod-kind/03-cross-trust.sh
# 멱등:  ConfigMap 덮어쓰기 + DS rollout + 노드 직접기입(멱등 병합).
# 다음:  90-verify.sh (1단계 PASS 게이트)
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

SVC_CTX="${SVC_CTX:-kind-svc}"
CICD_CP_NODE="${CICD_CP_NODE:-cicd-control-plane}"
HARBOR_HOST="${HARBOR_HOST:-harbor.local}"
DS_YAML="${DS_YAML:-../standalone-kind/registry-trust-daemonset.yaml}"

echo "== 0) 전제 =="
kubectl --context "$SVC_CTX" get nodes >/dev/null 2>&1 || { echo "FAIL: $SVC_CTX 없음 → 00 선행"; exit 1; }
[ -f "$DS_YAML" ] || { echo "FAIL: $DS_YAML 없음(registry-trust DaemonSet 재사용)"; exit 1; }

echo "== 1) cicd control-plane 의 kind 망 IP 산출 (harbor.local 도달 좌표) =="
CICD_CP_IP="$(docker inspect -f '{{(index .NetworkSettings.Networks "kind").IPAddress}}' "$CICD_CP_NODE" 2>/dev/null || true)"
[ -n "$CICD_CP_IP" ] || { echo "FAIL: $CICD_CP_NODE 의 kind IP 산출 실패(kind-cicd 기동 확인)"; exit 1; }
echo "  $HARBOR_HOST → $CICD_CP_IP (cicd CP, cross-cluster)"

echo "== 2) registry-trust DaemonSet 적용(kind-svc) =="
kubectl --context "$SVC_CTX" apply -f "$DS_YAML"

echo "== 3) registry-hosts ConfigMap 덮어쓰기 (harbor.local 엔드포인트=이름 + node-etc-hosts=CICD_CP_IP) =="
HARBOR_TOML="$(printf '[host."http://%s"]\n  capabilities = ["pull", "resolve"]\n' "$HARBOR_HOST")"
NODE_HOSTS="$(printf '%s %s\n' "$CICD_CP_IP" "$HARBOR_HOST")"
# reg.local 은 이 트랙에서 미사용 — 빈 값으로 둠(DS 의 `[ -f ] || continue` 로 무해).
kubectl --context "$SVC_CTX" -n registry-system create cm registry-hosts \
  --from-literal="$HARBOR_HOST"="$HARBOR_TOML" \
  --from-literal=node-etc-hosts="$NODE_HOSTS" \
  --dry-run=client -o yaml | kubectl --context "$SVC_CTX" apply -f - >/dev/null
kubectl --context "$SVC_CTX" -n registry-system rollout restart ds/registry-trust
kubectl --context "$SVC_CTX" -n registry-system rollout status  ds/registry-trust --timeout=120s || true

echo "== 4) 노드 직접기입(즉효, DS 스케줄 대기 없이) — kind-svc 전 노드 certs.d + /etc/hosts =="
for n in $(docker ps --format '{{.Names}}' | grep '^svc-'); do
  docker exec "$n" mkdir -p "/etc/containerd/certs.d/$HARBOR_HOST"
  docker exec "$n" sh -c "printf '[host.\"http://$HARBOR_HOST\"]\n  capabilities = [\"pull\", \"resolve\"]\n' > /etc/containerd/certs.d/$HARBOR_HOST/hosts.toml"
  docker exec "$n" sh -c "grep -v '[[:space:]]$HARBOR_HOST\$' /etc/hosts > /tmp/h 2>/dev/null || true; echo '$CICD_CP_IP $HARBOR_HOST' >> /tmp/h; cat /tmp/h > /etc/hosts"
  echo "  $n: certs.d + /etc/hosts($CICD_CP_IP $HARBOR_HOST)"
done

echo
echo "✅ 노드 Harbor 신뢰 배선 완료(kind-svc → cicd CP $CICD_CP_IP)."
echo "   ⚠️ 실제 harbor.local pull 검증은 2단계(Harbor 설치 후). 지금은 좌표/신뢰파일까지."
echo "   다음: bash 90-verify.sh"
