#!/usr/bin/env bash
# deploy/k8s/standalone-kind/07-reboot-recover.sh
# ─────────────────────────────────────────────────────────────────────────────
# PC/Docker Desktop 재부팅 후 한 방 복구. 【B안 (2026-06-07 세션4) — ingress 좌표 재산출 포함】
#
# 무엇을 하나:
#   1) registry-trust DaemonSet 적용 → 노드 certs.d/+/etc/hosts 자동 복구(휘발성 bind mount 의존 제거).
#   2) ★ CP_IP 재산출 → registry-hosts CM 의 node-etc-hosts 를 새 CP_IP 로 갱신(재부팅으로 노드 IP 가 바뀌어도 추종).
#      (harbor.local certs.d 엔드포인트는 *이름*이라 불변 — IP 는 /etc/hosts 가 흡수.)
#   3) 즉효 직접기입(노드 certs.d + /etc/hosts) — DS 스케줄 대기 없이 바로 반영.
#   4) (폴백) 로컬 빌드 이미지를 배포 ref 로 노드 적재(kind load).
#   5) 앱 Deployment 롤아웃 재시작 → 6파드 Ready 검증.
#
# 실행:  bash deploy/k8s/standalone-kind/07-reboot-recover.sh
#        SKIP_LOAD=1 ... 07-reboot-recover.sh   # kind load 폴백 생략(미러만으로 충분할 때)
# 전제:  kind-sanity 클러스터가 (재부팅 후) 떠 있음. 08(Harbor ingress) 이 한 번은 돌아 좌표가 잡힘.
#        Harbor 좌표를 아직 안 잡았으면 08-harbor-install.sh 를 먼저 실행.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

CLUSTER="${CLUSTER:-sanity}"; CTX="kind-${CLUSTER}"
CP_NODE="${CP_NODE:-${CLUSTER}-control-plane}"
NS="si-msa"
HOSTNAME_HARBOR="harbor.local"
SERVICES="gateway auth-server user-service admin-service"

echo "== 0) 전제 점검 =="
for b in docker kind kubectl; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done
kind get clusters 2>/dev/null | grep -qx "$CLUSTER" || { echo "FAIL: kind-$CLUSTER 없음(재부팅 후 보통 자동복귀). 01-pull-sanity.sh 점검."; exit 1; }
echo "  OK (cluster=$CTX)"

echo "== 1) registry-trust DaemonSet 적용(재부팅 내성 미러+hosts) =="
kubectl --context "$CTX" apply -f registry-trust-daemonset.yaml

echo "== 2) CP_IP 재산출 → registry-hosts CM node-etc-hosts 갱신(IP 변동 추종) =="
CP_IP="$(docker inspect -f '{{(index .NetworkSettings.Networks "kind").IPAddress}}' "$CP_NODE" 2>/dev/null || true)"
if [ -n "$CP_IP" ]; then
  echo "  CP_IP=$CP_IP"
  HARBOR_TOML="$(printf '[host."http://%s"]\n  capabilities = ["pull", "resolve"]\n' "$HOSTNAME_HARBOR")"
  REG_TOML="$(printf '[host."http://kind-registry:5000"]\n  capabilities = ["pull", "resolve"]\n')"
  NODE_HOSTS="$(printf '%s %s\n' "$CP_IP" "$HOSTNAME_HARBOR")"
  kubectl --context "$CTX" -n registry-system create cm registry-hosts \
    --from-literal=harbor.local="$HARBOR_TOML" \
    --from-literal=reg.local="$REG_TOML" \
    --from-literal=node-etc-hosts="$NODE_HOSTS" \
    --dry-run=client -o yaml | kubectl --context "$CTX" apply -f - >/dev/null
  kubectl --context "$CTX" -n registry-system rollout restart ds/registry-trust
else
  echo "  ⚠️ CP_IP 산출 실패 — CM 갱신 생략(기존 값 사용)."
fi
kubectl --context "$CTX" -n registry-system rollout status ds/registry-trust --timeout=120s || true

echo "== 3) 즉효 직접기입(노드 certs.d + /etc/hosts) =="
for n in $(docker ps --format '{{.Names}}' | grep "^${CLUSTER}-"); do
  docker exec "$n" mkdir -p "/etc/containerd/certs.d/$HOSTNAME_HARBOR" /etc/containerd/certs.d/reg.local
  docker exec "$n" sh -c "printf '[host.\"http://$HOSTNAME_HARBOR\"]\n  capabilities = [\"pull\", \"resolve\"]\n' > /etc/containerd/certs.d/$HOSTNAME_HARBOR/hosts.toml"
  docker exec "$n" sh -c "printf '[host.\"http://kind-registry:5000\"]\n  capabilities = [\"pull\", \"resolve\"]\n' > /etc/containerd/certs.d/reg.local/hosts.toml"
  if [ -n "$CP_IP" ]; then
    docker exec "$n" sh -c "grep -v '[[:space:]]$HOSTNAME_HARBOR\$' /etc/hosts > /tmp/h 2>/dev/null || true; echo '$CP_IP $HOSTNAME_HARBOR' >> /tmp/h; cat /tmp/h > /etc/hosts"
  fi
  echo "  $n certs.d/+/etc/hosts 기입"
done

if [ "${SKIP_LOAD:-0}" != "1" ]; then
  echo "== 4) (폴백) 로컬 빌드 이미지를 배포 ref 로 노드 적재(kind load) =="
  for s in $SERVICES; do
    if docker image inspect "si-msa/$s:local" >/dev/null 2>&1; then
      docker tag "si-msa/$s:local" "harbor.local/si-msa/$s:dev"
      kind load docker-image "harbor.local/si-msa/$s:dev" --name "$CLUSTER" >/dev/null 2>&1 \
        && echo "  loaded harbor.local/si-msa/$s:dev" \
        || echo "  ⚠️ $s load 실패(이미 존재거나 클러스터명 불일치 가능)"
    else
      echo "  - si-msa/$s:local 없음 → load 생략(미러 경로로 pull 시도됨)"
    fi
  done
else
  echo "== 4) kind load 폴백 생략(SKIP_LOAD=1) =="
fi

echo "== 5) 앱 롤아웃 재시작 =="
for s in $SERVICES; do kubectl --context "$CTX" -n "$NS" rollout restart "deploy/$s"; done
for s in $SERVICES; do kubectl --context "$CTX" -n "$NS" rollout status "deploy/$s" --timeout=180s; done

echo "== 6) 상태 검증 =="
kubectl --context "$CTX" -n "$NS" get pods -o wide
READY=$(kubectl --context "$CTX" -n "$NS" get pods -l app.kubernetes.io/part-of=si-msa \
  -o jsonpath='{range .items[*]}{.status.containerStatuses[0].ready}{"\n"}{end}' 2>/dev/null | grep -c true || true)
echo
echo "──────────────────────────────────────────────────────────────"
echo "그린 기준: si-msa 앱 파드 전부 Ready(=true). 현재 ready=${READY}."
echo "재부팅 후엔 이 스크립트 1회면 노드 좌표(certs.d+/etc/hosts)와 앱이 복구됩니다(port-forward 불요)."
