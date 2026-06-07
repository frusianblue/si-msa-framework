#!/usr/bin/env bash
# deploy/k8s/prod-kind/90-verify.sh
# ─────────────────────────────────────────────────────────────────────────────
# 1단계 PASS 게이트. 인프라 토대 4종 검증(이론 맹신 금지 — 실측 PASS 로만 다음 단계).
#   G1 두 클러스터 Ready + 공유 `kind` 도커망
#   G2 클러스터간 네트워크 (kind-svc 파드 → kind-cicd API /healthz)
#   G3 노드 Harbor 신뢰 배선 (kind-svc 노드 certs.d + /etc/hosts) — 실 pull 은 2단계
#   G4 서비스→DB 도달 (kind-svc 파드 → prod-postgres.internal / prod-redis.internal)
#
# ★ 2026-06-08 개정: 파드 출력에서 에러를 *가리지 않는다*(stderr 합치고 --quiet 제거).
#   FAIL 시 events + CoreDNS Corefile 을 자동 덤프 → 빈 출력 미스터리 제거(이미지pull/DNS/도달 구분).
# 실행:  bash deploy/k8s/prod-kind/90-verify.sh   (종료코드: 하나라도 FAIL 이면 1)
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

CICD_CTX="kind-cicd"; SVC_CTX="kind-svc"
CICD_CP_NODE="cicd-control-plane"
PG_HOST="prod-postgres.internal"; RD_HOST="prod-redis.internal"
FAILED=0
pass(){ echo "  [PASS] $1"; }
fail(){ echo "  [FAIL] $1"; FAILED=1; }

# 파드 1회 실행 — stdout+stderr 합쳐서 그대로 반환(에러 안 가림). --quiet 미사용.
run_pod(){ # $1=ctx $2=image ; 나머지=커맨드
  local ctx="$1" img="$2"; shift 2
  kubectl --context "$ctx" run "verify-$$-$RANDOM" --restart=Never -i --rm \
    --image="$img" --image-pull-policy=IfNotPresent --command -- "$@" 2>&1
}
diag(){ # $1=ctx — FAIL 트리아지용 덤프
  local ctx="$1"
  echo "  ── 트리아지($ctx) ──"
  kubectl --context "$ctx" get events --sort-by=.lastTimestamp 2>/dev/null | tail -8 | sed 's/^/    /'
}

echo "════════ G1) 두 클러스터 Ready + 공유 kind 망 ════════"
for ctx in "$CICD_CTX" "$SVC_CTX"; do
  if kubectl --context "$ctx" wait --for=condition=Ready nodes --all --timeout=60s >/dev/null 2>&1; then
    pass "$ctx 노드 Ready"; else fail "$ctx 노드 Ready 아님"; fi
done
NET="$(docker network inspect kind -f '{{range .Containers}}{{.Name}} {{end}}' 2>/dev/null || true)"
for n in cicd-control-plane svc-control-plane; do
  echo "$NET" | grep -qw "$n" && pass "$n kind 망 존재" || fail "$n kind 망 없음"
done

echo "════════ G2) 클러스터간 네트워크 (svc 파드 → cicd API) ════════"
CICD_CP_IP="$(docker inspect -f '{{(index .NetworkSettings.Networks "kind").IPAddress}}' "$CICD_CP_NODE" 2>/dev/null || true)"
if [ -z "$CICD_CP_IP" ]; then fail "cicd CP IP 산출 실패"; else
  echo "  cicd CP IP = $CICD_CP_IP"
  OUT="$(run_pod "$SVC_CTX" curlimages/curl:latest curl -sS -k --max-time 8 "https://${CICD_CP_IP}:6443/healthz")"
  echo "  응답: [$(printf '%s' "$OUT" | tr '\n' ' ')]"
  if printf '%s' "$OUT" | grep -q "ok"; then pass "svc 파드 → cicd:6443/healthz = ok"; else fail "svc 파드 → cicd API 도달 실패"; diag "$SVC_CTX"; fi
fi

echo "════════ G3) 노드 Harbor 신뢰 배선 (kind-svc) ════════"
SVC_NODE="$(docker ps --format '{{.Names}}' | grep '^svc-' | head -1)"
if [ -z "$SVC_NODE" ]; then fail "svc 노드 컨테이너 없음"; else
  echo "  검사 노드: $SVC_NODE"
  docker exec "$SVC_NODE" cat /etc/containerd/certs.d/harbor.local/hosts.toml >/dev/null 2>&1 \
    && pass "certs.d/harbor.local/hosts.toml 존재" || fail "certs.d/harbor.local/hosts.toml 부재"
  if docker exec "$SVC_NODE" grep -q "[[:space:]]harbor.local\$" /etc/hosts 2>/dev/null; then
    pass "노드 /etc/hosts harbor.local → $(docker exec "$SVC_NODE" awk '/[[:space:]]harbor.local$/{print $1}' /etc/hosts)"
  else fail "노드 /etc/hosts harbor.local 없음"; fi
  echo "  (실제 harbor.local pull 검증은 2단계 Harbor 설치 후)"
fi

echo "════════ G4) 서비스 → DB 도달 (kind-svc 파드) ════════"
PGOUT="$(run_pod "$SVC_CTX" postgres:16-alpine pg_isready -h "$PG_HOST" -p 5432)"
echo "  pg_isready: [$(printf '%s' "$PGOUT" | tr '\n' ' ')]"
if printf '%s' "$PGOUT" | grep -q "accepting connections"; then pass "파드 → $PG_HOST:5432 accepting"; else fail "파드 → $PG_HOST 실패"; PGFAIL=1; fi
RDOUT="$(run_pod "$SVC_CTX" redis:7-alpine redis-cli -h "$RD_HOST" ping)"
echo "  redis ping: [$(printf '%s' "$RDOUT" | tr '\n' ' ')]"
if printf '%s' "$RDOUT" | grep -q "PONG"; then pass "파드 → $RD_HOST:6379 PONG"; else fail "파드 → $RD_HOST 실패"; PGFAIL=1; fi
if [ "${PGFAIL:-0}" = 1 ]; then
  echo "  ── DB 트리아지 ──"
  echo "  CoreDNS 주입 상태(prod- stanza 있어야 함):"
  kubectl --context "$SVC_CTX" -n kube-system get cm coredns -o jsonpath='{.data.Corefile}' 2>/dev/null | grep -A3 'prod-' | sed 's/^/    /' || echo "    (prod- stanza 없음 → 02-coredns-db.sh 재실행)"
  echo "  데이터 컨테이너:"; docker ps --format '{{.Names}}\t{{.Status}}' | grep prod- | sed 's/^/    /' || echo "    (prod-postgres/redis 없음 → 01 재실행)"
  diag "$SVC_CTX"
fi

echo
echo "──────────────────────────────────────────────"
if [ "$FAILED" = 0 ]; then echo "✅ 1단계 PASS — 인프라 토대 완료. 다음: 2단계 ArgoCD(hub)."; else echo "❌ 일부 FAIL — 위 트리아지 덤프 확인."; fi
exit "$FAILED"
