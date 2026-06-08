#!/usr/bin/env bash
# deploy/k8s/prod-kind/36-reset-userdb-rehearsal.sh
# ─────────────────────────────────────────────────────────────────────────────
# 리허설 데이터 작업: userdb 를 재생성(초기화)한다.
#
#   ★ 배경(개명 후): user/admin 은 처음부터 별도 DB(userdb/admindb)·별도 계정(user_app/admin_app)으로
#     분리되어, 과거의 "공유 sidb Flyway 충돌" 시나리오는 원천 소멸했다(PITFALLS §9 보정).
#     따라서 이 스크립트는 오염 정리가 아니라 "userdb 리허설 데이터 초기화" 용도다.
#
#   ⚠️ 개명(sidb→userdb) 적용 시 주의: prod-postgres 의 initdb 는 1회성(PGDATA 빈 최초 부팅).
#     기존 컨테이너에는 옛 sidb 만 있고 userdb 는 없다 → 신 스키마 전환의 정석은
#     `01-data-containers.sh` 재실행(컨테이너 재생성 = initdb-prod.sql 개명판 재실행, 리허설 데이터 버림).
#     이 스크립트는 컨테이너를 유지한 채 userdb 만 초기화할 때 쓴다(없으면 user_app 와 함께 생성).
#
# 실행: bash deploy/k8s/prod-kind/36-reset-userdb-rehearsal.sh
# 멱등: DROP IF EXISTS + CREATE. 재실행 시 userdb 초기화(리허설).
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
PG_NAME="${PG_NAME:-prod-postgres}"; SVC_CTX="${SVC_CTX:-kind-svc}"; NS="${NS:-si-msa}"
DB="${DB:-userdb}"; OWNER="${OWNER:-user_app}"; OWNER_PW="${OWNER_PW:-user_app_pw}"

echo "== 0) 전제 =="
for b in docker kubectl; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done
docker ps --format '{{.Names}}' | grep -qx "$PG_NAME" \
  || { echo "FAIL: $PG_NAME 컨테이너 없음 → 01-data-containers.sh"; exit 1; }

echo "== 1) user 분리 확인(권고) =="
USER_DB="$(kubectl --context "$SVC_CTX" -n "$NS" get cm user-service-config -o jsonpath='{.data.DB_URL}' 2>/dev/null || true)"
case "$USER_DB" in
  *userdb*) echo "  user-service-config DB_URL=$USER_DB (userdb — OK)";;
  *)        echo "  ⚠️ user-service-config DB_URL=$USER_DB — 아직 userdb 아님!";
            echo "     개명 매니페스트(base configmap/overlay) commit/push/sync 먼저.";;
esac

echo "== 2) user_app role 보강(없으면 생성) + userdb 재생성 (PG16 WITH FORCE = 활성 연결 강제 종료) =="
docker exec "$PG_NAME" psql -U postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname='$OWNER'" | grep -q 1 \
  || docker exec "$PG_NAME" psql -U postgres -c "CREATE ROLE $OWNER WITH LOGIN PASSWORD '$OWNER_PW';"
docker exec "$PG_NAME" psql -U postgres -c "DROP DATABASE IF EXISTS $DB WITH (FORCE);"
docker exec "$PG_NAME" psql -U postgres -c "CREATE DATABASE $DB OWNER $OWNER;"
echo "  $DB 재생성 완료(빈 DB, owner=$OWNER) — user 가 V1 부터 깨끗이 Flyway 재적용."

echo "== 3) user/admin 파드 재기동(깨끗한 userdb/admindb 에 재적용) =="
kubectl --context "$SVC_CTX" -n "$NS" delete pod --all --wait=false >/dev/null 2>&1 || true
echo "  파드 재생성 트리거."

echo
echo "──────────────────────────────────────────────────────────────"
echo "✅ userdb 초기화 완료. user=userdb(깨끗·user_app) · admin=admindb(분리·admin_app)."
echo "   파드 startup(Flyway 재적용)+DB 연결 대기 후:  bash 41-verify-promote.sh"
