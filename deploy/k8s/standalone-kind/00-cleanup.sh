#!/usr/bin/env bash
# deploy/k8s/standalone-kind/00-cleanup.sh
# ───────────────────────────────────────────────────────────────────────────────
# A) 현 docker-desktop kind 의 S3 트리아지 잔여물 정리(pulltest, node-debugger-*).
# B) (옵션) standalone sanity 클러스터 + 로컬 레지스트리 teardown.
#
# 사용:
#   bash 00-cleanup.sh                      # A 만(잔여 디버그 파드 정리)
#   bash 00-cleanup.sh --teardown-sanity    # A + B(standalone 클러스터/레지스트리 제거)
# ───────────────────────────────────────────────────────────────────────────────
set -uo pipefail

DD_CTX="${DD_CTX:-docker-desktop}"
CLUSTER="${CLUSTER:-sanity}"
REG_NAME="${REG_NAME:-kind-registry}"

echo "== A) docker-desktop kind 잔여 디버그 파드 정리(컨텍스트: ${DD_CTX}) =="
if kubectl config get-contexts -o name 2>/dev/null | grep -qx "${DD_CTX}"; then
  kubectl --context "${DD_CTX}" delete pod pulltest -n si-msa --ignore-not-found
  # node-debugger-* (kubectl debug node 잔여) — default ns 에서 이름 패턴으로 제거
  for p in $(kubectl --context "${DD_CTX}" get pod -n default -o name 2>/dev/null | grep -i 'node-debugger' || true); do
    kubectl --context "${DD_CTX}" delete -n default "$p" --ignore-not-found
  done
  echo "  정리 완료(없으면 ignore-not-found 로 무해)."
else
  echo "  컨텍스트 ${DD_CTX} 없음 — 건너뜀."
fi

if [ "${1:-}" = "--teardown-sanity" ]; then
  echo "== B) standalone sanity teardown =="
  kind delete cluster --name "${CLUSTER}" 2>/dev/null || true
  docker rm -f "${REG_NAME}" 2>/dev/null || true
  docker rm -f "${AUTH_REG_NAME:-harbor-auth-reg}" 2>/dev/null || true
  echo "  클러스터(${CLUSTER})·레지스트리(${REG_NAME}, ${AUTH_REG_NAME:-harbor-auth-reg}) 제거."
else
  echo "== B) (standalone teardown 생략 — 필요하면 --teardown-sanity) =="
fi
