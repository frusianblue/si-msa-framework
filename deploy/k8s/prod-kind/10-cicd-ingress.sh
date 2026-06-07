#!/usr/bin/env bash
# deploy/k8s/prod-kind/10-cicd-ingress.sh
# ─────────────────────────────────────────────────────────────────────────────
# 2단계 (1/3): kind-cicd(hub)에 ingress-nginx 설치. argocd.local/harbor.local/jenkins.local 진입 계층.
#   standalone-kind/10-ingress-nginx.sh 와 동일 원리·동일 함정 보정(컨트롤러 control-plane 고정).
#   cicd 는 호스트 80/443 게시(extraPortMappings) → 호스트 localhost:80 으로 진입.
#
# 실행:  bash deploy/k8s/prod-kind/10-cicd-ingress.sh
# 멱등:  apply 재실행 무해.
# 다음:  11-argocd-install.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

CTX="kind-cicd"; CLUSTER="cicd"
ING_VER="${ING_VER:-controller-v1.13.0}"
MANIFEST="https://raw.githubusercontent.com/kubernetes/ingress-nginx/${ING_VER}/deploy/static/provider/kind/deploy.yaml"

echo "== 0) 전제 =="
for b in kubectl docker; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done
kind get clusters 2>/dev/null | grep -qx "$CLUSTER" || { echo "FAIL: kind-$CLUSTER 없음 → 00-up-clusters.sh"; exit 1; }

echo "== 0.5) ingress-ready 라벨 확인(kind-cicd-config 전제) =="
LABELED="$(kubectl --context "$CTX" get nodes -l ingress-ready=true -o name 2>/dev/null | wc -l | tr -d ' ')"
[ "$LABELED" = "0" ] && kubectl --context "$CTX" label node "${CLUSTER}-control-plane" ingress-ready=true --overwrite || true

echo "== 1) ingress-nginx(kind provider, ${ING_VER}) 설치 =="
kubectl --context "$CTX" apply -f "$MANIFEST"

echo "== 1.5) 컨트롤러를 control-plane 에 고정(★ v1.13.0 nodeSelector 누락 보정, PITFALLS §9) =="
kubectl --context "$CTX" -n ingress-nginx patch deploy ingress-nginx-controller --type merge -p \
  '{"spec":{"template":{"spec":{"nodeSelector":{"ingress-ready":"true","kubernetes.io/os":"linux"},"tolerations":[{"key":"node-role.kubernetes.io/control-plane","operator":"Exists","effect":"NoSchedule"}]}}}}'

echo "== 2) 컨트롤러 Ready 대기 =="
kubectl --context "$CTX" -n ingress-nginx wait --for=condition=Available deploy/ingress-nginx-controller --timeout=180s || true
kubectl --context "$CTX" -n ingress-nginx wait --for=condition=Ready pod \
  --selector=app.kubernetes.io/component=controller --timeout=180s

echo "== 3) PASS 게이트 — 호스트 localhost:80 진입 =="
CODE="$(curl -s -o /dev/null -w '%{http_code}' http://localhost/ 2>/dev/null || echo 000)"
echo "  curl http://localhost/  → HTTP ${CODE}"
if [ "$CODE" = "404" ]; then
  echo "✅ PASS — ingress-nginx 가 호스트 localhost:80 응답(404=룰없음=정상). 다음: 11-argocd-install.sh"
else
  echo "⚠️ HTTP ${CODE}(404 기대). reset=컨트롤러 worker 오스케줄(1.5 patch 확인) / refused=extraPortMappings 부재(재생성) 또는 80 점유."
  echo "   확인: kubectl --context $CTX -n ingress-nginx get pods -o wide (NODE=cicd-control-plane 이어야)"
fi
