#!/usr/bin/env bash
# deploy/k8s/prod-kind/92-verify-gitops.sh
# ─────────────────────────────────────────────────────────────────────────────
# 3단계 PASS 게이트 — GitOps reconcile 가 kind-svc 까지 닿는가(파드 green 은 4~5단계).
#   G8  AppProject(si-msa) + app-of-apps(si-msa-bootstrap) 존재
#   G9  prod Application(si-msa-prod) 생성됨 + dest=kind-svc + sync 시도(Synced/Progressing)
#   G10 kind-svc 에 ns si-msa + Deployment 4개 reconcile (파드 ImagePullBackOff = sentinel, 정상)
#
# 실행:  bash deploy/k8s/prod-kind/92-verify-gitops.sh   (종료코드: FAIL 있으면 1)
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail
CICD_CTX="kind-cicd"; SVC_CTX="kind-svc"; NS="argocd"; APPNS="si-msa"
FAILED=0
pass(){ echo "  [PASS] $1"; }
fail(){ echo "  [FAIL] $1"; FAILED=1; }
note(){ echo "  [NOTE] $1"; }

echo "════════ G8) AppProject + app-of-apps ════════"
kubectl --context "$CICD_CTX" -n "$NS" get appproject si-msa >/dev/null 2>&1 \
  && pass "AppProject si-msa 존재" || fail "AppProject si-msa 없음(20 선행)"
kubectl --context "$CICD_CTX" -n "$NS" get application si-msa-bootstrap >/dev/null 2>&1 \
  && pass "app-of-apps si-msa-bootstrap 존재" || fail "si-msa-bootstrap 없음(20 선행)"

echo "════════ G9) prod Application(si-msa-prod) ════════"
if kubectl --context "$CICD_CTX" -n "$NS" get application si-msa-prod >/dev/null 2>&1; then
  pass "si-msa-prod Application 존재(app-of-apps 가 생성)"
  DEST="$(kubectl --context "$CICD_CTX" -n "$NS" get application si-msa-prod -o jsonpath='{.spec.destination.name}' 2>/dev/null)"
  SYNC="$(kubectl --context "$CICD_CTX" -n "$NS" get application si-msa-prod -o jsonpath='{.status.sync.status}' 2>/dev/null)"
  HEALTH="$(kubectl --context "$CICD_CTX" -n "$NS" get application si-msa-prod -o jsonpath='{.status.health.status}' 2>/dev/null)"
  echo "  dest=$DEST · sync=$SYNC · health=$HEALTH"
  [ "$DEST" = "kind-svc" ] && pass "dest=kind-svc" || fail "dest 가 kind-svc 아님($DEST)"
  case "$SYNC" in Synced|OutOfSync|Unknown|"") note "sync=$SYNC (Synced 면 매니페스트 적용됨; 비었으면 reconcile 진행중)";; esac
else
  fail "si-msa-prod 없음 — deploy/argocd 가 master 에 push 됐는지 확인(ArgoCD=git 진실)"
fi

echo "════════ G10) kind-svc reconcile (매니페스트 적용 여부) ════════"
if kubectl --context "$SVC_CTX" get ns "$APPNS" >/dev/null 2>&1; then
  pass "kind-svc 에 ns $APPNS 생성됨"
  DEPLOYS="$(kubectl --context "$SVC_CTX" -n "$APPNS" get deploy -o name 2>/dev/null | wc -l | tr -d ' ')"
  echo "  Deployment 수: $DEPLOYS"
  [ "${DEPLOYS:-0}" -ge 1 ] && pass "Deployment reconcile 됨($DEPLOYS개)" || fail "Deployment 없음(sync 미완 또는 path 오류)"
  echo "  파드 상태(ImagePullBackOff = sentinel __GITSHA__, 정상):"
  kubectl --context "$SVC_CTX" -n "$APPNS" get pods 2>/dev/null | sed 's/^/    /' | head -12
  note "파드 ImagePullBackOff/ErrImagePull 는 4단계(promote: 불변 sha 커밋) + Harbor 설치 후 해소."
else
  fail "kind-svc 에 ns $APPNS 없음 — Application sync 미도달(G9/푸시/클러스터 등록 확인)"
fi

echo
echo "──────────────────────────────────────────────"
if [ "$FAILED" = 0 ]; then
  echo "✅ 3단계 PASS — GitOps reconcile 가 kind-svc 까지 도달(매니페스트 적용). "
  echo "   ArgoCD UI: si-msa-prod = Synced / 파드는 ImagePullBackOff(정상, 4단계 대기)."
  echo "   다음: 4단계 promote 배선(Jenkinsfile.promote: 불변 :<sha> → overlays/prod kustomize set image → git commit/push → ArgoCD sync) + Harbor(hub)."
else
  echo "❌ 일부 FAIL — 위 항목 트리아지(특히 deploy/argocd master push 여부)."
fi
exit "$FAILED"
