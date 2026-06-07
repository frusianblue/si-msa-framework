#!/usr/bin/env bash
# deploy/k8s/standalone-kind/07-reboot-recover.sh
# ─────────────────────────────────────────────────────────────────────────────
# PC/Docker Desktop 재부팅 후 한 방 복구. (2026-06-07 "auth-server 만 ImagePullBackOff" 재발 방지)
#
# 무엇을 하나:
#   1) registry-trust DaemonSet 적용 → 노드 certs.d 미러 자동 복구(휘발성 bind mount 의존 제거).
#   2) 즉효를 위해 노드 certs.d 를 직접도 1회 기입(DS 스케줄 대기 없이 바로 반영).
#   3) (폴백) 로컬 빌드 이미지를 배포 ref 로 노드에 직접 적재(kind load) — 레지스트리/인증 전부 우회.
#   4) 앱 Deployment 롤아웃 재시작 → 새 파드가 복구된 미러/노드캐시로 기동.
#   5) 6파드 Ready 검증.
#
# 실행:  bash deploy/k8s/standalone-kind/07-reboot-recover.sh
#        SKIP_LOAD=1 ... 07-reboot-recover.sh   # kind load 폴백 생략(미러만으로 충분할 때)
# 전제:  kind-sanity 클러스터가 (재부팅 후) 떠 있음. harbor-auth-reg/kind-registry 컨테이너는 --restart=always.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

CLUSTER="${CLUSTER:-sanity}"; CTX="kind-${CLUSTER}"
NS="si-msa"
NODES="${NODES:-sanity-control-plane sanity-worker sanity-worker2}"
SERVICES="gateway auth-server user-service admin-service"

echo "== 0) 전제 점검 =="
for b in docker kind kubectl; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done
kind get clusters 2>/dev/null | grep -qx "$CLUSTER" || { echo "FAIL: kind-$CLUSTER 없음(재부팅 후에도 보통 자동 복귀). 01-pull-sanity.sh 점검."; exit 1; }
echo "  OK (cluster=$CTX)"

echo "== 1) registry-trust DaemonSet 적용(재부팅 내성 미러) =="
kubectl --context "$CTX" apply -f registry-trust-daemonset.yaml
kubectl --context "$CTX" -n registry-system rollout status ds/registry-trust --timeout=120s || true

echo "== 2) 노드 certs.d 직접 기입(즉효) =="
# 레포의 certs.d(harbor.local/reg.local)를 노드에 직접 복사 — DS 스케줄 대기 없이 바로 반영.
for n in $NODES; do
  for r in harbor.local reg.local; do
    [ -f "certs.d/$r/hosts.toml" ] || continue
    docker exec "$n" mkdir -p "/etc/containerd/certs.d/$r"
    docker exec -i "$n" sh -c "cat > /etc/containerd/certs.d/$r/hosts.toml" < "certs.d/$r/hosts.toml"
  done
  echo "  $n certs.d 기입"
done

if [ "${SKIP_LOAD:-0}" != "1" ]; then
  echo "== 3) (폴백) 로컬 빌드 이미지를 배포 ref 로 노드 적재(kind load) =="
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
  echo "== 3) kind load 폴백 생략(SKIP_LOAD=1) =="
fi

echo "== 4) 앱 롤아웃 재시작 =="
for s in $SERVICES; do kubectl --context "$CTX" -n "$NS" rollout restart "deploy/$s"; done
for s in $SERVICES; do kubectl --context "$CTX" -n "$NS" rollout status "deploy/$s" --timeout=180s; done

echo "== 5) 상태 검증 =="
kubectl --context "$CTX" -n "$NS" get pods -o wide
READY=$(kubectl --context "$CTX" -n "$NS" get pods -l app.kubernetes.io/part-of=si-msa \
  -o jsonpath='{range .items[*]}{.status.containerStatuses[0].ready}{"\n"}{end}' 2>/dev/null | grep -c true || true)
echo
echo "──────────────────────────────────────────────────────────────"
echo "그린 기준: si-msa 앱 파드 전부 Ready(=true). 현재 ready=${READY}."
echo "재부팅 후엔 이 스크립트 1회 + (호스트 접근 필요 시) port-forward 만 재실행하면 됩니다."
