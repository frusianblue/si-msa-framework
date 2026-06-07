#!/usr/bin/env bash
# deploy/k8s/prod-kind/91-verify-argocd.sh
# ─────────────────────────────────────────────────────────────────────────────
# 2단계 PASS 게이트.
#   G5 cicd ingress-nginx Ready + 호스트 localhost:80 진입(404)
#   G6 ArgoCD server Ready + argocd.local 도달(Host 헤더 라우팅)
#   G7 kind-svc 등록(cluster Secret 존재) + cicd→svc API 도달(cross-cluster, ArgoCD 가 쓸 경로)
#
# 실행:  bash deploy/k8s/prod-kind/91-verify-argocd.sh   (종료코드: FAIL 있으면 1)
# ─────────────────────────────────────────────────────────────────────────────
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

CICD_CTX="kind-cicd"; NS="argocd"
SVC_CP_NODE="svc-control-plane"; CLUSTER_NAME="kind-svc"
FAILED=0
pass(){ echo "  [PASS] $1"; }
fail(){ echo "  [FAIL] $1"; FAILED=1; }

# detached 실행 → 완료 대기 → logs(확실) → 삭제. (90-verify 와 동일 — 짧은 명령 attach 누락 회피.)
run_pod(){ # $1=ctx $2=image ; 나머지=커맨드
  local ctx="$1" img="$2"; shift 2
  local name="vfy-$$-$RANDOM" phase="" i
  kubectl --context "$ctx" run "$name" --restart=Never \
    --image="$img" --image-pull-policy=IfNotPresent --command -- "$@" >/dev/null 2>&1
  for i in $(seq 1 40); do
    phase="$(kubectl --context "$ctx" get pod "$name" -o jsonpath='{.status.phase}' 2>/dev/null || true)"
    case "$phase" in Succeeded|Failed) break ;; esac; sleep 2
  done
  kubectl --context "$ctx" logs "$name" 2>&1
  kubectl --context "$ctx" delete pod "$name" --force --grace-period=0 >/dev/null 2>&1 || true
}

echo "════════ G5) cicd ingress-nginx + 호스트 진입 ════════"
if kubectl --context "$CICD_CTX" -n ingress-nginx wait --for=condition=Ready pod \
     --selector=app.kubernetes.io/component=controller --timeout=60s >/dev/null 2>&1; then
  pass "ingress-nginx 컨트롤러 Ready"; else fail "ingress-nginx 컨트롤러 Ready 아님"; fi
CODE="$(curl -s -o /dev/null -w '%{http_code}' http://localhost/ 2>/dev/null || echo 000)"
echo "  curl http://localhost/ → $CODE"
[ "$CODE" = "404" ] && pass "호스트 localhost:80 = 404(컨트롤러 정상)" || fail "호스트 진입 비정상($CODE)"

echo "════════ G6) ArgoCD server + argocd.local ════════"
if kubectl --context "$CICD_CTX" -n "$NS" rollout status deploy/argocd-server --timeout=60s >/dev/null 2>&1; then
  pass "argocd-server Ready"; else fail "argocd-server Ready 아님"; fi
ACODE="$(curl -s -o /dev/null -w '%{http_code}' -H 'Host: argocd.local' http://localhost/ 2>/dev/null || echo 000)"
echo "  curl -H Host:argocd.local http://localhost/ → $ACODE"
case "$ACODE" in 200|302|307) pass "argocd.local 도달($ACODE)";; *) fail "argocd.local 도달 실패($ACODE)";; esac

echo "════════ G7) kind-svc 등록 + cicd→svc API 도달 ════════"
if kubectl --context "$CICD_CTX" -n "$NS" get secret "$CLUSTER_NAME" \
     -l argocd.argoproj.io/secret-type=cluster >/dev/null 2>&1; then
  pass "cluster Secret '$CLUSTER_NAME' 존재"; else fail "cluster Secret '$CLUSTER_NAME' 없음(12 선행)"; fi
SVC_CP_IP="$(docker inspect -f '{{(index .NetworkSettings.Networks "kind").IPAddress}}' "$SVC_CP_NODE" 2>/dev/null || true)"
if [ -z "$SVC_CP_IP" ]; then fail "svc CP IP 산출 실패"; else
  echo "  svc CP IP = $SVC_CP_IP (ArgoCD 가 등록한 server)"
  OUT="$(run_pod "$CICD_CTX" curlimages/curl:latest curl -sS -k --max-time 8 "https://${SVC_CP_IP}:6443/healthz")"
  echo "  cicd 파드 → svc:6443/healthz: [$(printf '%s' "$OUT" | tr '\n' ' ')]"
  printf '%s' "$OUT" | grep -q "ok" && pass "cicd → svc API 도달(ArgoCD reconcile 경로)" || fail "cicd → svc API 도달 실패"
fi

echo
echo "──────────────────────────────────────────────"
if [ "$FAILED" = 0 ]; then
  echo "✅ 2단계 PASS — ArgoCD(hub) + kind-svc 원격등록 완료."
  echo "   ArgoCD UI(http://argocd.local) → Settings → Clusters 에서 '$CLUSTER_NAME' Successful 최종 확인."
  echo "   다음: 3단계 GitOps 자산(deploy/argocd/ AppProject + prod Application + app-of-apps)."
else
  echo "❌ 일부 FAIL — 위 항목 트리아지."
  echo "   힌트: G7 도달 실패면 → SVC_TLS_INSECURE=true bash 12-register-svc.sh 재시도(kind apiserver SAN)."
fi
exit "$FAILED"
