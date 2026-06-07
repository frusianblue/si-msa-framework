#!/usr/bin/env bash
# deploy/k8s/prod-kind/40-promote.sh
# ─────────────────────────────────────────────────────────────────────────────
# 4단계 (2/2): bash promote — GitOps 흐름 증명(빌드 → push → overlay 핀 → git commit/push → ArgoCD sync).
#
#   빌드 주체 = 호스트 docker(Kaniko 아님). "흐름부터 증명"이 목적이라 미해결 Kaniko 멀티빌드
#   이슈(NEXT_CI_KANIKO_MULTIBUILD)를 끌어들이지 않고 WSL 호스트 docker daemon 으로 4서비스 빌드.
#   Kaniko/Jenkins(Jenkinsfile.promote) 파이프라인화는 흐름 증명 후 후속.
#
#   ★ dev(push-CD)와의 결정적 차이(스펙 §2 · PITFALLS §9):
#     dev = 워크스페이스에서만 핀(되커밋 X). prod = ArgoCD 가 **git(master) 커밋 상태**를 진실로 reconcile
#     → 핀을 **git 에 커밋·push** 해야 한다(dev 의 "되커밋 X" 규칙을 *반전*). 이 스크립트가 그 커밋을 만든다.
#
#   흐름:
#     1) 빌드  호스트 docker + deploy/docker/Dockerfile.build, 4서비스 :<sha>  (builder 스테이지 SERVICE 무관 → 1회 컴파일 재사용)
#     2) push  docker login harbor.local → harbor.local/si-msa/<svc>:<sha>
#     3) 핀    overlays/prod 에 kustomize edit set image(placeholder name → harbor.local name + :<sha>, 멱등)
#     4) commit/push  overlays/prod/kustomization.yaml 를 master 에  (ArgoCD 트리거)
#     5) sync  ArgoCD refresh=hard 로 즉시 reconcile 유도(폴링 ~3분 대기 단축)
#
# 전제: 30-harbor-hub-install PASS + 호스트 push 사전조건(daemon insecure-registries: harbor.local, hosts)
#       + 워킹트리 clean(미커밋 변경이 있으면 :<sha> 가 실제 빌드 내용과 어긋남 — 0단계가 막음)
#       + kustomize CLI(GitOps 표준; 없으면 설치 안내 후 FAIL)
#       + Maven Central 도달(Dockerfile.build 가 컨테이너 안에서 Gradle 빌드).
# 실행: bash deploy/k8s/prod-kind/40-promote.sh
# 멱등: 같은 HEAD 면 같은 :<sha> 재빌드/재핀(no-op diff 면 commit 건너뜀).
# 다음: 41-verify-promote.sh (G11~G13)
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"
ROOT="$(cd ../../.. && pwd)"

CTX="${CTX:-kind-cicd}"; ARGO_NS="argocd"; APP="si-msa-prod"
REGISTRY="${REGISTRY:-harbor.local}"; PROJECT="${PROJECT:-si-msa}"
HARBOR_USER="${HARBOR_USER:-admin}"; HARBOR_PASS="${HARBOR_PASS:-Harbor12345}"
SERVICES="gateway auth-server user-service admin-service"
DOCKERFILE="$ROOT/deploy/docker/Dockerfile.build"
OVERLAY="$ROOT/deploy/k8s/overlays/prod"
export DOCKER_BUILDKIT=1

echo "== 0) 전제 =="
for b in docker git kustomize kubectl; do
  command -v "$b" >/dev/null 2>&1 || {
    echo "FAIL: '$b' 없음."
    [ "$b" = kustomize ] && echo "   설치: 'go install sigs.k8s.io/kustomize/kustomize/v5@latest' 또는 https://kustomize.io/"
    exit 1
  }
done
[ -f "$DOCKERFILE" ] || { echo "FAIL: $DOCKERFILE 없음"; exit 1; }
[ -f "$OVERLAY/kustomization.yaml" ] || { echo "FAIL: $OVERLAY/kustomization.yaml 없음"; exit 1; }
# 워킹트리 clean 점검(:<sha> 정확성 — 미커밋이면 빌드 내용과 sha 불일치).
DIRTY="$(git -C "$ROOT" status --porcelain | grep -v '^.. deploy/k8s/overlays/prod/kustomization.yaml$' || true)"
if [ -n "$DIRTY" ]; then
  echo "FAIL: 워킹트리에 미커밋 변경이 있음 — :<sha> 가 실제 빌드 내용과 어긋남. 먼저 커밋/스태시:"
  echo "$DIRTY" | sed 's/^/      /'
  exit 1
fi
SHA="$(git -C "$ROOT" rev-parse --short HEAD)"
echo "  registry=$REGISTRY project=$PROJECT sha=$SHA  (빌드 대상 HEAD)"

echo "== 1) 빌드(호스트 docker, 4서비스 :$SHA) =="
# builder 스테이지는 ARG SERVICE 무관 → BuildKit 캐시로 첫 빌드만 Gradle 컴파일, 나머지 3은 캐시 재사용.
for s in $SERVICES; do
  echo "  -- build $REGISTRY/$PROJECT/$s:$SHA"
  docker build -f "$DOCKERFILE" --build-arg SERVICE="$s" \
    -t "$REGISTRY/$PROJECT/$s:$SHA" "$ROOT"
done

echo "== 2) push(harbor.local) =="
echo "$HARBOR_PASS" | docker login "$REGISTRY" -u "$HARBOR_USER" --password-stdin
for s in $SERVICES; do
  echo "  -- push $REGISTRY/$PROJECT/$s:$SHA"
  docker push "$REGISTRY/$PROJECT/$s:$SHA"
done

echo "== 3) overlay 핀(kustomize edit set image — placeholder → harbor name + :$SHA) =="
# prod overlay 의 images 엔트리는 placeholder name(registry.example.com/si-msa/<svc>) + newTag __GITSHA__.
# set image NAME=NEWNAME:TAG 가 newName + newTag 를 멱등 갱신(sentinel/이전 sha 무관하게 덮어씀).
( cd "$OVERLAY"
  for s in $SERVICES; do
    kustomize edit set image "registry.example.com/$PROJECT/$s=$REGISTRY/$PROJECT/$s:$SHA"
  done
)
echo "  핀 결과:"
grep -A1 'images:' "$OVERLAY/kustomization.yaml" | sed 's/^/    /' || true
( cd "$OVERLAY" && grep -E 'name:|newName:|newTag:' kustomization.yaml | sed 's/^/    /' ) || true

echo "== 4) commit/push (prod 반전 — ArgoCD 는 master 를 읽음) =="
if [ -n "$(git -C "$ROOT" status --porcelain deploy/k8s/overlays/prod/kustomization.yaml)" ]; then
  git -C "$ROOT" add deploy/k8s/overlays/prod/kustomization.yaml
  git -C "$ROOT" commit -m "promote(prod): pin images → $REGISTRY/$PROJECT/*:$SHA"
  git -C "$ROOT" push
  echo "  커밋·푸시 완료(promote → :$SHA)."
else
  echo "  (overlay 변경 없음 — 이미 :$SHA 로 핀됨, commit 건너뜀)"
fi

echo "== 5) ArgoCD 즉시 reconcile 유도(폴링 ~3분 단축) =="
if kubectl --context "$CTX" -n "$ARGO_NS" get application "$APP" >/dev/null 2>&1; then
  kubectl --context "$CTX" -n "$ARGO_NS" annotate application "$APP" \
    argocd.argoproj.io/refresh=hard --overwrite >/dev/null
  echo "  refresh=hard 주입 — ArgoCD 가 origin/master 재조회 후 sync."
else
  echo "  ⚠️ $APP 없음 — 3단계(20-gitops-bootstrap) 선행/푸시 확인."
fi

echo
echo "──────────────────────────────────────────────────────────────"
echo "✅ promote 완료 — :$SHA 빌드·push·핀·커밋·push → ArgoCD reconcile 유도."
echo "   ArgoCD 가 master 의 새 커밋을 sync → kind-svc 노드가 harbor.local 에서 :$SHA pull → 파드 green."
echo "   sync/pull/startup 에 시간 필요(이미지 pull + Gradle 빌드 캐시 없으면 초기 김). 충분히 대기 후:"
echo "   다음: bash 41-verify-promote.sh"
