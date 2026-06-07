#!/usr/bin/env bash
# deploy/k8s/prod-kind/20-gitops-bootstrap.sh
# ─────────────────────────────────────────────────────────────────────────────
# 3단계: GitOps 자산 부트스트랩 — AppProject + app-of-apps 를 ArgoCD(hub)에 seed.
#   이후 ArgoCD 가 git(master)의 deploy/argocd/apps/ 에서 prod Application(si-msa-prod)을 생성·동기화.
#
# ★ 중요(pull-GitOps 전환): ArgoCD 는 **git(master)을 진실로 읽는다.** deploy/argocd/* 는
#    반드시 **commit + push** 되어 있어야 부트스트랩이 자식 앱을 찾는다(로컬 apply 와 다른 지점).
#
# 전제: 11/12 완료(ArgoCD + kind-svc 등록). deploy/argocd/ 가 master 에 푸시됨.
# 실행: bash deploy/k8s/prod-kind/20-gitops-bootstrap.sh
# 멱등: kubectl apply 재실행 무해.
# 다음: 92-verify-gitops.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"
ROOT="$(cd ../../.. && pwd)"
ARGOCD_DIR="$ROOT/deploy/argocd"
CTX="kind-cicd"; NS="argocd"

echo "== 0) 전제 =="
for b in kubectl git; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done
kubectl --context "$CTX" -n "$NS" get deploy argocd-server >/dev/null 2>&1 \
  || { echo "FAIL: ArgoCD 미설치 → 11-argocd-install.sh"; exit 1; }
for f in projects/si-msa.yaml apps/si-msa-prod.yaml bootstrap.yaml; do
  [ -f "$ARGOCD_DIR/$f" ] || { echo "FAIL: $ARGOCD_DIR/$f 없음"; exit 1; }
done

echo "== 0.5) git 커밋/푸시 점검 (ArgoCD 는 git 을 읽음) =="
if git -C "$ROOT" rev-parse >/dev/null 2>&1; then
  DIRTY="$(git -C "$ROOT" status --porcelain deploy/argocd deploy/k8s/overlays/prod 2>/dev/null || true)"
  if [ -n "$DIRTY" ]; then
    echo "  ⚠️ 미커밋 변경 발견(deploy/argocd 또는 overlays/prod):"
    echo "$DIRTY" | sed 's/^/      /'
    echo "  → ArgoCD 는 master 의 *푸시된* 상태만 본다. 아래 후 재실행 권장:"
    echo "      git add deploy/argocd && git commit -m 'argocd: prod GitOps 자산' && git push"
  else
    AHEAD="$(git -C "$ROOT" rev-list --count @{u}..HEAD 2>/dev/null || echo '?')"
    [ "$AHEAD" != "0" ] && echo "  ⚠️ 로컬이 origin 보다 ${AHEAD} 커밋 앞섬 — push 안 됐을 수 있음(git push)." \
                        || echo "  커밋·푸시 상태 OK(로컬=origin)."
  fi
else
  echo "  (git 레포 아님 — 점검 생략)"
fi

echo "== 1) AppProject(si-msa) seed =="
kubectl --context "$CTX" apply -f "$ARGOCD_DIR/projects/si-msa.yaml"

echo "== 2) app-of-apps(bootstrap) seed =="
kubectl --context "$CTX" apply -f "$ARGOCD_DIR/bootstrap.yaml"

echo "== 3) ArgoCD 가 자식 앱 생성하는지(~60s 폴링) =="
for i in $(seq 1 30); do
  kubectl --context "$CTX" -n "$NS" get application si-msa-prod >/dev/null 2>&1 && { echo "  si-msa-prod Application 생성됨."; break; }
  sleep 2; [ "$i" = 30 ] && echo "  ⚠️ si-msa-prod 아직 안 보임 → deploy/argocd 가 master 에 푸시됐는지 확인."
done

echo
echo "✅ GitOps 부트스트랩 적용. ArgoCD 가 master 에서 reconcile."
echo "   ⚠️ prod overlay 이미지 = sentinel __GITSHA__ → 파드 ImagePullBackOff 정상(fail-loud). 파드 green 은 4~5단계."
echo "   다음: bash 92-verify-gitops.sh"
