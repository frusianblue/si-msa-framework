#!/usr/bin/env bash
# deploy/k8s/prod-kind/02-coredns-db.sh
# ─────────────────────────────────────────────────────────────────────────────
# 1단계 (3/4): 파드 → 클러스터 밖 DB 이름해소.
#   파드는 CoreDNS 로 해소(도커 임베드 DNS 안 봄) → kind-svc CoreDNS 에
#   prod-postgres.internal / prod-redis.internal → 데이터 컨테이너 kind-IP 를 주입.
#   (08-harbor-install.sh 의 harbor.local CoreDNS 주입과 동일 awk 패턴.)
#   → prod overlay 의 DB_URL=jdbc:postgresql://prod-postgres.internal:5432/... 를 *무수정* 으로 해소.
#
# 실행:  bash deploy/k8s/prod-kind/02-coredns-db.sh
# 멱등:  같은 이름 stanza 제거 후 재주입(IP 변동 추적). 데이터 컨테이너 재기동 후 반드시 재실행.
# 다음:  03-cross-trust.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

CTX="${CTX:-kind-svc}"          # 서비스 클러스터(파드가 DB 를 쓰는 곳). 필요 시 kind-cicd 도 동일 호출.
PG_NAME="${PG_NAME:-prod-postgres}"; PG_HOST="${PG_HOST:-prod-postgres.internal}"
RD_NAME="${RD_NAME:-prod-redis}";    RD_HOST="${RD_HOST:-prod-redis.internal}"

echo "== 0) 전제 =="
kubectl --context "$CTX" get nodes >/dev/null 2>&1 || { echo "FAIL: 컨텍스트 $CTX 없음 → 00 선행"; exit 1; }
PG_IP="$(docker inspect -f '{{(index .NetworkSettings.Networks "kind").IPAddress}}' "$PG_NAME" 2>/dev/null || true)"
RD_IP="$(docker inspect -f '{{(index .NetworkSettings.Networks "kind").IPAddress}}' "$RD_NAME" 2>/dev/null || true)"
[ -n "$PG_IP" ] && [ -n "$RD_IP" ] || { echo "FAIL: 데이터 컨테이너 kind IP 산출 실패 → 01 선행"; exit 1; }
echo "  $PG_HOST→$PG_IP · $RD_HOST→$RD_IP (CTX=$CTX)"

# CoreDNS Corefile 에 <name>:53 { hosts { IP name; fallthrough } } stanza 를 멱등 upsert.
upsert_host() {  # $1=ip $2=name
  local ip="$1" name="$2"
  local cur patched
  cur="$(kubectl --context "$CTX" -n kube-system get cm coredns -o jsonpath='{.data.Corefile}')"
  patched="$(printf '%s\n' "$cur" | awk -v ip="$ip" -v nm="$name" '
    $0 ~ ("^" nm ":53 \\{") {skip=1}
    skip && /^\}/ {skip=0; next}
    skip {next}
    {print}
    END{
      print nm ":53 {"
      print "    hosts {"
      print "        " ip " " nm
      print "        fallthrough"
      print "    }"
      print "}"
    }')"
  kubectl --context "$CTX" -n kube-system create cm coredns \
    --from-literal=Corefile="$patched" --dry-run=client -o yaml \
    | kubectl --context "$CTX" -n kube-system apply -f - >/dev/null
  echo "  CoreDNS upsert: $name → $ip"
}

echo "== 1) CoreDNS stanza 주입 =="
upsert_host "$PG_IP" "$PG_HOST"
upsert_host "$RD_IP" "$RD_HOST"

echo "== 2) CoreDNS 재기동 =="
kubectl --context "$CTX" -n kube-system rollout restart deploy/coredns
kubectl --context "$CTX" -n kube-system rollout status  deploy/coredns --timeout=120s

echo
echo "✅ CoreDNS 에 DB 엔드포인트 주입 완료. 검증은 90-verify.sh(G4: 파드→prod-postgres.internal psql)."
echo "   다음: bash 03-cross-trust.sh"
