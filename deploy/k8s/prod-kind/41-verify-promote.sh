#!/usr/bin/env bash
# deploy/k8s/prod-kind/41-verify-promote.sh
# ─────────────────────────────────────────────────────────────────────────────
# 4단계 PASS 게이트 — promote 가 파드 green 까지 닿는가(GitOps 흐름 end-to-end).
#   G11  Harbor(hub) reachable + si-msa 프로젝트에 4서비스 repo + :<sha> 아티팩트 존재
#   G12  overlays/prod 핀 완료 — sentinel __GITSHA__ 소거 + newName=harbor.local + git 에 커밋·push 됨
#   G13  si-msa-prod = Synced + Healthy + kind-svc 파드 4서비스 Running(green) — ImagePullBackOff 해소
#
# 실행:  bash deploy/k8s/prod-kind/41-verify-promote.sh   (종료코드: FAIL 있으면 1)
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"
ROOT="$(cd ../../.. && pwd)"

CICD_CTX="kind-cicd"; SVC_CTX="kind-svc"; ARGO_NS="argocd"; APPNS="si-msa"; APP="si-msa-prod"
REGISTRY="${REGISTRY:-harbor.local}"; PROJECT="${PROJECT:-si-msa}"
HARBOR_USER="${HARBOR_USER:-admin}"; HARBOR_PASS="${HARBOR_PASS:-Harbor12345}"
SERVICES="gateway auth-server user-service admin-service"
OVERLAY="$ROOT/deploy/k8s/overlays/prod/kustomization.yaml"
FAILED=0
pass(){ echo "  [PASS] $1"; }
fail(){ echo "  [FAIL] $1"; FAILED=1; }
note(){ echo "  [NOTE] $1"; }

echo "════════ G11) Harbor(hub) + si-msa repo + :<sha> 아티팩트 ════════"
HARBOR_API="http://$REGISTRY/api/v2.0"
if curl -fsS -o /dev/null "http://$REGISTRY/api/v2.0/health" 2>/dev/null \
   || curl -fsS -o /dev/null -u "$HARBOR_USER:$HARBOR_PASS" "$HARBOR_API/projects/$PROJECT" 2>/dev/null; then
  pass "Harbor reachable (http://$REGISTRY)"
  MISS=0
  for s in $SERVICES; do
    CNT="$(curl -fsS -u "$HARBOR_USER:$HARBOR_PASS" \
      "$HARBOR_API/projects/$PROJECT/repositories/$s/artifacts?page_size=1" 2>/dev/null \
      | grep -o '"digest"' | wc -l | tr -d ' ')"
    if [ "${CNT:-0}" -ge 1 ]; then echo "    $s: 아티팩트 있음"; else echo "    ⚠️ $s: 아티팩트 없음"; MISS=1; fi
  done
  [ "$MISS" = 0 ] && pass "4서비스 repo 아티팩트 존재" || fail "일부 서비스 아티팩트 없음(40-promote push 확인)"
else
  fail "Harbor 도달 실패(http://$REGISTRY) — 30-harbor-hub-install + hosts/insecure-registries 확인"
fi

echo "════════ G12) overlay 핀 + 커밋 상태 ════════"
# ★ images 의 newTag 만 본다 — 헤더 주석에도 `__GITSHA__` 설명이 있어 파일 전체 grep 은 오탐.
if grep -q 'newTag: __GITSHA__' "$OVERLAY"; then
  fail "images newTag 에 sentinel __GITSHA__ 가 아직 남음 — 40-promote 핀 미적용"
else
  pass "images sentinel __GITSHA__ 소거됨(핀 적용)"
fi
PINNED="$(grep -c "newName: $REGISTRY/$PROJECT/" "$OVERLAY" 2>/dev/null || echo 0)"
[ "${PINNED:-0}" -ge 4 ] && pass "newName=$REGISTRY/$PROJECT/* 4건 핀" || fail "newName 핀 부족($PINNED/4)"
# 커밋·푸시 점검(ArgoCD 는 origin/master 만 봄).
if [ -n "$(git -C "$ROOT" status --porcelain deploy/k8s/overlays/prod/kustomization.yaml)" ]; then
  fail "overlay 가 미커밋 — ArgoCD 가 못 봄(git add/commit/push 필요)"
else
  AHEAD="$(git -C "$ROOT" rev-list --count @{u}..HEAD 2>/dev/null || echo '?')"
  [ "$AHEAD" = "0" ] && pass "overlay 커밋·푸시 됨(로컬=origin)" || fail "로컬이 origin 보다 $AHEAD 앞섬 — git push 필요"
fi

echo "════════ G13) ArgoCD Synced/Healthy + kind-svc 파드 green ════════"
if kubectl --context "$CICD_CTX" -n "$ARGO_NS" get application "$APP" >/dev/null 2>&1; then
  SYNC="$(kubectl --context "$CICD_CTX" -n "$ARGO_NS" get application "$APP" -o jsonpath='{.status.sync.status}' 2>/dev/null)"
  HEALTH="$(kubectl --context "$CICD_CTX" -n "$ARGO_NS" get application "$APP" -o jsonpath='{.status.health.status}' 2>/dev/null)"
  echo "  sync=$SYNC · health=$HEALTH"
  [ "$SYNC" = "Synced" ] && pass "ArgoCD Synced" || fail "ArgoCD sync=$SYNC(Synced 기대 — refresh 대기/트리아지)"
  [ "$HEALTH" = "Healthy" ] && pass "ArgoCD Healthy" || note "health=$HEALTH (파드 기동 중이면 Progressing — 잠시 후 재실행)"
else
  fail "$APP Application 없음(3단계 선행)"
fi
if kubectl --context "$SVC_CTX" -n "$APPNS" get deploy >/dev/null 2>&1; then
  echo "  kind-svc 파드 상태:"
  kubectl --context "$SVC_CTX" -n "$APPNS" get pods 2>/dev/null | sed 's/^/    /' | head -16
  NOTREADY="$(kubectl --context "$SVC_CTX" -n "$APPNS" get pods --no-headers 2>/dev/null \
    | awk '{print $2}' | awk -F/ '$1!=$2{c++} END{print c+0}')"
  TOTAL="$(kubectl --context "$SVC_CTX" -n "$APPNS" get pods --no-headers 2>/dev/null | wc -l | tr -d ' ')"
  if [ "${TOTAL:-0}" -ge 1 ] && [ "${NOTREADY:-1}" = "0" ]; then
    pass "kind-svc 파드 전부 Ready(green) — promote end-to-end 성립"
  else
    fail "Ready 아님(미준비 $NOTREADY/$TOTAL) — pull/startup/DB 트리아지(아래 힌트)"
    note "ImagePullBackOff → 노드 신뢰(03)/Harbor(30)/push(40 G11) · CrashLoop → DB(02 CoreDNS)/issuer/Authenticator(PITFALLS §9)"
  fi
else
  fail "kind-svc 에 Deployment 없음 — GitOps reconcile 미도달(92-verify-gitops 선행)"
fi

echo
echo "──────────────────────────────────────────────"
if [ "$FAILED" = 0 ]; then
  echo "✅ 4단계 PASS — bash promote 흐름 end-to-end 성립."
  echo "   빌드(호스트 docker) → push(harbor.local) → overlay 핀 → git commit/push → ArgoCD sync → kind-svc 파드 green."
  echo "   다음: 5단계(데이터/관측 정합) · Jenkins(Jenkinsfile.promote) 파이프라인화는 후속(흐름 증명 완료)."
else
  echo "❌ 일부 FAIL — 위 게이트 트리아지."
fi
exit "$FAILED"
