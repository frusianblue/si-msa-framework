#!/usr/bin/env bash
# deploy/k8s/prod-kind/11-argocd-install.sh
# ─────────────────────────────────────────────────────────────────────────────
# 2단계 (2/3): kind-cicd(hub)에 ArgoCD 설치(Helm) + argocd.local ingress.
#   server.insecure=true(values) → HTTP 서빙 → ingress 평문 라우팅(B안).
#   ingress 는 별도 매니페스트(argocd-server-ingress.yaml)로 apply(차트 버전 무관).
#
# 전제: 10-cicd-ingress.sh PASS + helm CLI.
# 실행: bash deploy/k8s/prod-kind/11-argocd-install.sh
# 멱등: helm upgrade --install / ingress apply 재실행 무해.
# 다음: 12-register-svc.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

CTX="kind-cicd"; NS="argocd"; HOST="argocd.local"

echo "== 0) 전제 =="
for b in kubectl helm docker; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done
kind get clusters 2>/dev/null | grep -qx cicd || { echo "FAIL: kind-cicd 없음"; exit 1; }
kubectl --context "$CTX" -n ingress-nginx get deploy ingress-nginx-controller >/dev/null 2>&1 \
  || { echo "FAIL: ingress-nginx 미설치 → 먼저 10-cicd-ingress.sh"; exit 1; }
[ -f argocd-values.yaml ] && [ -f argocd-server-ingress.yaml ] || { echo "FAIL: argocd-values/ingress yaml 없음"; exit 1; }

echo "== 1) ArgoCD 설치(Helm) =="
helm repo add argo https://argoproj.github.io/argo-helm >/dev/null 2>&1 || true
helm repo update >/dev/null
kubectl --context "$CTX" create namespace "$NS" >/dev/null 2>&1 || true
helm --kube-context "$CTX" upgrade --install argocd argo/argo-cd -n "$NS" \
  -f argocd-values.yaml --wait --timeout 10m

echo "== 2) argocd.local ingress 적용 =="
kubectl --context "$CTX" apply -f argocd-server-ingress.yaml

echo "== 3) 컴포넌트 Ready 확인 =="
kubectl --context "$CTX" -n "$NS" rollout status deploy/argocd-server --timeout=180s
kubectl --context "$CTX" -n "$NS" get pods

echo "== 4) admin 초기 비밀번호 =="
PW="$(kubectl --context "$CTX" -n "$NS" get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' 2>/dev/null | base64 -d 2>/dev/null || true)"
echo "  admin / ${PW:-(secret 없음 — 이미 변경됐거나 미생성)}"

echo
echo "──────────────────────────────────────────────"
echo "✅ ArgoCD 설치 완료 (hub=kind-cicd)."
echo "   호스트 접속: Windows + WSL hosts 에  '127.0.0.1 argocd.local'  추가 → http://argocd.local"
echo "   로그인: admin / 위 비밀번호"
echo "   다음: bash 12-register-svc.sh  (kind-svc 원격 등록)"
