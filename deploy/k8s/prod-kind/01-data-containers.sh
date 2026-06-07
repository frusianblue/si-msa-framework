#!/usr/bin/env bash
# deploy/k8s/prod-kind/01-data-containers.sh
# ─────────────────────────────────────────────────────────────────────────────
# 1단계 (2/4): 클러스터 밖 데이터 = 우분투 도커 postgres/redis 를 `--network kind` 로 기동.
#   = 실 prod 의 "데이터 클러스터(K8s 밖, DBA 분리관리)" 역할의 리허설.
#   데이터를 굳이 kind 클러스터로 만들지 않음(부대비용 회피 + DB=K8s밖 현장모델 정직 재현, §1).
#
#   네트워크 별칭(--network-alias)으로 prod-postgres.internal / prod-redis.internal 부여 →
#   도커-도커(컨테이너끼리)는 임베드 DNS 로 즉시 해소. 단 **파드는 CoreDNS 로 해소**(도커 DNS 안 봄) →
#   파드용 이름해소는 02-coredns-db.sh 가 CoreDNS 에 주입(harbor.local 패턴과 동일).
#
# 실행:  bash deploy/k8s/prod-kind/01-data-containers.sh
# 멱등:  기존 동명 컨테이너 제거 후 재기동.  ⚠️ 재기동 = 데이터 초기화(리허설이므로 무방).
# 다음:  02-coredns-db.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

PG_NAME="${PG_NAME:-prod-postgres}";  PG_ALIAS="${PG_ALIAS:-prod-postgres.internal}"
RD_NAME="${RD_NAME:-prod-redis}";     RD_ALIAS="${RD_ALIAS:-prod-redis.internal}"
PG_IMAGE="${PG_IMAGE:-postgres:16-alpine}"
RD_IMAGE="${RD_IMAGE:-redis:7-alpine}"
PG_SUPERPASS="${PG_SUPERPASS:-postgres}"

echo "== 0) 전제 (kind 네트워크 존재 = 00 선행) =="
docker network inspect kind >/dev/null 2>&1 || { echo "FAIL: docker network 'kind' 없음 → 먼저 00-up-clusters.sh"; exit 1; }
[ -f initdb-prod.sql ] || { echo "FAIL: initdb-prod.sql 없음(같은 디렉터리)"; exit 1; }

echo "== 1) postgres 기동 ($PG_NAME, alias=$PG_ALIAS) =="
docker rm -f "$PG_NAME" >/dev/null 2>&1 || true
docker run -d --name "$PG_NAME" \
  --network kind --network-alias "$PG_ALIAS" \
  -e POSTGRES_PASSWORD="$PG_SUPERPASS" \
  -v "$HERE/initdb-prod.sql":/docker-entrypoint-initdb.d/initdb-prod.sql:ro \
  "$PG_IMAGE" >/dev/null
echo "  대기: postgres ready ..."
for i in $(seq 1 30); do
  if docker exec "$PG_NAME" pg_isready -U postgres >/dev/null 2>&1; then echo "  postgres ready."; break; fi
  sleep 2; [ "$i" = 30 ] && { echo "FAIL: postgres 가 60s 안에 ready 안 됨"; docker logs --tail 30 "$PG_NAME"; exit 1; }
done

echo "== 2) redis 기동 ($RD_NAME, alias=$RD_ALIAS) =="
docker rm -f "$RD_NAME" >/dev/null 2>&1 || true
docker run -d --name "$RD_NAME" \
  --network kind --network-alias "$RD_ALIAS" \
  "$RD_IMAGE" >/dev/null
for i in $(seq 1 15); do
  if [ "$(docker exec "$RD_NAME" redis-cli ping 2>/dev/null)" = "PONG" ]; then echo "  redis ready."; break; fi
  sleep 1; [ "$i" = 15 ] && { echo "FAIL: redis ping 실패"; docker logs --tail 20 "$RD_NAME"; exit 1; }
done

echo "== 3) DB 시드 확인 (authdb/sidb/admindb) =="
docker exec "$PG_NAME" psql -U postgres -tAc \
  "SELECT datname FROM pg_database WHERE datname IN ('authdb','sidb','admindb') ORDER BY 1;"

echo "== 4) kind 망 IP 확인 =="
PG_IP="$(docker inspect -f '{{(index .NetworkSettings.Networks "kind").IPAddress}}' "$PG_NAME")"
RD_IP="$(docker inspect -f '{{(index .NetworkSettings.Networks "kind").IPAddress}}' "$RD_NAME")"
echo "  $PG_NAME=$PG_IP · $RD_NAME=$RD_IP (kind 네트워크)"
[ -n "$PG_IP" ] && [ -n "$RD_IP" ] || { echo "FAIL: 데이터 컨테이너 kind IP 산출 실패"; exit 1; }

echo
echo "✅ 데이터 컨테이너(K8s 밖) 기동 완료. ⚠️ IP 는 재기동마다 바뀔 수 있음 → 02 가 매번 docker inspect 로 재주입."
echo "   다음: bash 02-coredns-db.sh"
