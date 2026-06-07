#!/usr/bin/env bash
# deploy/k8s/prod-kind/36-reset-sidb-rehearsal.sh
# ─────────────────────────────────────────────────────────────────────────────
# 4단계 보조(데이터 정리): admin 이 sidb 에 남긴 Flyway 오염을 정리해 user-service 가 깨끗한 sidb 를 쓰게 한다.
#
#   ★ 왜 필요한가(실측): user↔admin 가 같은 sidb 를 공유하면 Flyway 충돌(PITFALLS §9). admin 이
#     (FileStorage 로 죽기 *전에*) sidb 에 자기 마이그레이션을 적용 → flyway_schema_history 오염 →
#     user 가 같은 sidb 에서 다른 체크섬으로 `Migration checksum mismatch`(version 1/2)로 CrashLoop.
#   해결 = ① admin 을 admindb 로 분리(prod overlay, initdb 가 admindb 생성해둠 — git 변경 = sync)
#          ② sidb 재생성으로 admin 오염 history 제거(이 스크립트) → user 가 V1 부터 깨끗이 재적용.
#
#   ⚠️ 리허설 전용 데이터 작업. 실 prod 는 user↔admin DB 가 처음부터 분리(DBA 관리) — 이런 정리 불요.
#      sidb 의 user 데이터도 함께 초기화된다(리허설이라 무방).
#
# 전제: prod overlay(admin→admindb) + deployment-hardening(file base-path) 이 **commit/push/sync** 됨.
#       (admin 이 이미 admindb 로 옮겨갔어야 sidb 재생성이 의미 있음. sync 전이면 admin 이 다시 sidb 오염.)
# 실행: bash deploy/k8s/prod-kind/36-reset-sidb-rehearsal.sh
# 멱등: DROP IF EXISTS + CREATE. 재실행 시 sidb 초기화(리허설).
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
PG_NAME="${PG_NAME:-prod-postgres}"; SVC_CTX="${SVC_CTX:-kind-svc}"; NS="${NS:-si-msa}"
DB="${DB:-sidb}"; OWNER="${OWNER:-siuser}"

echo "== 0) 전제 =="
for b in docker kubectl; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done
docker ps --format '{{.Names}}' | grep -qx "$PG_NAME" \
  || { echo "FAIL: $PG_NAME 컨테이너 없음 → 01-data-containers.sh"; exit 1; }

echo "== 1) admin 분리 확인(권고) =="
# admin 이 아직 sidb 를 보고 있으면 재생성해도 다시 오염된다. sync 됐는지 가볍게 점검.
ADMIN_DB="$(kubectl --context "$SVC_CTX" -n "$NS" get cm admin-service-config -o jsonpath='{.data.DB_URL}' 2>/dev/null || true)"
case "$ADMIN_DB" in
  *admindb*) echo "  admin-service-config DB_URL=$ADMIN_DB (admindb 분리됨 — OK)";;
  *)         echo "  ⚠️ admin-service-config DB_URL=$ADMIN_DB — 아직 admindb 아님!";
             echo "     prod overlay(admin→admindb) commit/push/sync 먼저(안 그러면 admin 이 sidb 재오염).";;
esac

echo "== 2) sidb 재생성 (PG16 WITH FORCE = 활성 연결 강제 종료 후 drop) =="
docker exec "$PG_NAME" psql -U postgres -c "DROP DATABASE IF EXISTS $DB WITH (FORCE);"
docker exec "$PG_NAME" psql -U postgres -c "CREATE DATABASE $DB OWNER $OWNER;"
echo "  $DB 재생성 완료(빈 DB, owner=$OWNER) — user 가 V1 부터 깨끗이 Flyway 재적용."

echo "== 3) user/admin 파드 재기동(깨끗한 sidb/admindb 에 재적용) =="
kubectl --context "$SVC_CTX" -n "$NS" delete pod --all --wait=false >/dev/null 2>&1 || true
echo "  파드 재생성 트리거."

echo
echo "──────────────────────────────────────────────────────────────"
echo "✅ sidb 오염 정리 완료. user=sidb(깨끗) · admin=admindb(분리)."
echo "   파드 startup(Flyway 재적용)+DB 연결 대기 후:  bash 41-verify-promote.sh"
