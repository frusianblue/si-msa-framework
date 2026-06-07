#!/usr/bin/env bash
# deploy/k8s/prod-kind/90-verify.sh
# ─────────────────────────────────────────────────────────────────────────────
# 1단계 PASS 게이트. 인프라 토대가 섰는지 4종 검증(이론 맹신 금지 — 실측 PASS 로만 다음 단계).
#   G1 두 클러스터 Ready + 공유 `kind` 도커망
#   G2 클러스터간 네트워크 (kind-svc 파드 → kind-cicd API /healthz 도달)
#   G3 노드 Harbor 신뢰 배선 (kind-svc 노드 certs.d + /etc/hosts) — 실 pull 은 2단계
#   G4 서비스→DB 도달 (kind-svc 파드 → prod-postgres.internal pg_isready + prod-redis.internal ping)
#
# 실행:  bash deploy/k8s/prod-kind/90-verify.sh
# 종료코드: 하나라도 FAIL 이면 1.
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

CICD_CTX="kind-cicd"; SVC_CTX="kind-svc"
CICD_CP_NODE="cicd-control-plane"
PG_HOST="prod-postgres.internal"; RD_HOST="prod-redis.internal"
FAILED=0
pass(){ echo "  [PASS] $1"; }
fail(){ echo "  [FAIL] $1"; FAILED=1; }

echo "════════ G1) 두 클러스터 Ready + 공유 kind 망 ════════"
for ctx in "$CICD_CTX" "$SVC_CTX"; do
  if kubectl --context "$ctx" wait --for=condition=Ready nodes --all --timeout=60s >/dev/null 2>&1; then
    pass "$ctx 노드 Ready"
  else
    fail "$ctx 노드 Ready 아님(또는 컨텍스트 없음)"
  fi
done
NET="$(docker network inspect kind -f '{{range .Containers}}{{.Name}} {{end}}' 2>/dev/null || true)"
for n in cicd-control-plane svc-control-plane; do
  echo "$NET" | grep -qw "$n" && pass "$n 이 kind 망에 있음" || fail "$n 이 kind 망에 없음"
done

echo "════════ G2) 클러스터간 네트워크 (svc 파드 → cicd API) ════════"
CICD_CP_IP="$(docker inspect -f '{{(index .NetworkSettings.Networks "kind").IPAddress}}' "$CICD_CP_NODE" 2>/dev/null || true)"
if [ -z "$CICD_CP_IP" ]; then
  fail "cicd CP IP 산출 실패"
else
  echo "  cicd CP IP = $CICD_CP_IP"
  OUT="$(kubectl --context "$SVC_CTX" run nettest-$$ --rm -i --restart=Never --image=curlimages/curl:latest --quiet -- \
         curl -sk --max-time 8 "https://${CICD_CP_IP}:6443/healthz" 2>/dev/null || true)"
  echo "  응답: [$OUT]"
  echo "$OUT" | grep -q "ok" && pass "svc 파드 → cicd:6443/healthz = ok (cross-cluster L3/L4)" \
                              || fail "svc 파드 → cicd API 도달 실패"
fi

echo "════════ G3) 노드 Harbor 신뢰 배선 (kind-svc) ════════"
SVC_NODE="$(docker ps --format '{{.Names}}' | grep '^svc-' | head -1)"
if [ -z "$SVC_NODE" ]; then
  fail "svc 노드 컨테이너 없음"
else
  echo "  검사 노드: $SVC_NODE"
  docker exec "$SVC_NODE" cat /etc/containerd/certs.d/harbor.local/hosts.toml >/dev/null 2>&1 \
    && pass "certs.d/harbor.local/hosts.toml 존재" || fail "certs.d/harbor.local/hosts.toml 부재(03 선행)"
  if docker exec "$SVC_NODE" grep -q "[[:space:]]harbor.local\$" /etc/hosts 2>/dev/null; then
    pass "노드 /etc/hosts 에 harbor.local → $(docker exec "$SVC_NODE" awk '/[[:space:]]harbor.local$/{print $1}' /etc/hosts)"
  else
    fail "노드 /etc/hosts 에 harbor.local 없음(03 선행)"
  fi
  echo "  (실제 harbor.local pull 검증은 2단계 Harbor 설치 후)"
fi

echo "════════ G4) 서비스 → DB 도달 (kind-svc 파드) ════════"
PGOUT="$(kubectl --context "$SVC_CTX" run pgcheck-$$ --rm -i --restart=Never --image=postgres:16-alpine --quiet -- \
         pg_isready -h "$PG_HOST" -p 5432 2>/dev/null || true)"
echo "  pg_isready: [$PGOUT]"
echo "$PGOUT" | grep -q "accepting connections" && pass "파드 → $PG_HOST:5432 accepting connections" \
                                                || fail "파드 → $PG_HOST pg_isready 실패(02 CoreDNS 주입 확인)"
RDOUT="$(kubectl --context "$SVC_CTX" run rdcheck-$$ --rm -i --restart=Never --image=redis:7-alpine --quiet -- \
         redis-cli -h "$RD_HOST" ping 2>/dev/null || true)"
echo "  redis ping: [$RDOUT]"
echo "$RDOUT" | grep -q "PONG" && pass "파드 → $RD_HOST:6379 PONG" \
                               || fail "파드 → $RD_HOST redis ping 실패(02 CoreDNS 주입 확인)"

echo
echo "──────────────────────────────────────────────"
if [ "$FAILED" = 0 ]; then
  echo "✅ 1단계 PASS — 인프라 토대 완료. 다음 섹션: 2단계 ArgoCD(hub) 설치 + kind-svc 원격등록."
else
  echo "❌ 일부 FAIL — 위 항목 트리아지 후 해당 스크립트 재실행."
fi
exit "$FAILED"
