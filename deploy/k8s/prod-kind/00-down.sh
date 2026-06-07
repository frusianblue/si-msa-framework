#!/usr/bin/env bash
# deploy/k8s/prod-kind/00-down.sh
# ─────────────────────────────────────────────────────────────────────────────
# prod-kind 리허설 teardown — 2클러스터 + 데이터 컨테이너 제거.
#   bash 00-down.sh            # 데이터 컨테이너만(클러스터 유지)
#   bash 00-down.sh --all      # 클러스터(cicd, svc) + 데이터 컨테이너 전부
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail

PG_NAME="${PG_NAME:-prod-postgres}"; RD_NAME="${RD_NAME:-prod-redis}"

echo "== 데이터 컨테이너 제거 =="
docker rm -f "$PG_NAME" "$RD_NAME" 2>/dev/null || true
echo "  $PG_NAME, $RD_NAME 제거(없으면 무해)."

if [ "${1:-}" = "--all" ]; then
  echo "== 클러스터 제거 (cicd, svc) =="
  kind delete cluster --name cicd 2>/dev/null || true
  kind delete cluster --name svc  2>/dev/null || true
  echo "  kind-cicd, kind-svc 제거."
else
  echo "== 클러스터 유지(전체 제거는 --all) =="
fi
