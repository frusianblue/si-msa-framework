#!/usr/bin/env bash
# deploy/k8s/standalone-kind/redeploy.sh
# ───────────────────────────────────────────────────────────────────────────────
# 단일 서비스 빠른 재배포 — 소스 "콘텐츠 다이제스트"를 이미지 태그로 박는다.
#   목적: :local/:dev 같은 고정 태그에서 매번 반복되던 "이게 반영된 빌드냐" 실랑이를 영구 제거.
#         코드가 1글자라도 바뀌면 태그가 달라져 노드가 무조건 새로 pull, 안 바뀌면 동일 태그(=no-op).
#         → 노드/호스트에 옛 이미지가 남아 있어도 무관(참조 태그가 다름).
#
# 사용:
#   bash deploy/k8s/standalone-kind/redeploy.sh auth-server          # 빌드→push→set image→rollout
#   bash deploy/k8s/standalone-kind/redeploy.sh auth-server --smoke  # + client_credentials 토큰 한 방
#
# 전제: 02/03 트랙으로 kind-sanity 클러스터 + harbor-auth-reg + dev overlay 가 이미 떠 있음.
# 환경변수 오버라이드: CLUSTER(=sanity) NS(=si-msa) PORT(=5443) RUSER/RPASS PORT_FWD(=9000)
# ───────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$HERE/../../.." && pwd)"
cd "$REPO_ROOT"

SVC="${1:?사용: redeploy.sh <gateway|auth-server|user-service|admin-service> [--smoke]}"
SMOKE=0; [ "${2:-}" = "--smoke" ] && SMOKE=1

CLUSTER="${CLUSTER:-sanity}"; CTX="kind-${CLUSTER}"
NS="${NS:-si-msa}"
PORT="${PORT:-5443}"; RUSER="${RUSER:-admin}"; RPASS="${RPASS:-Harbor12345}"
PORT_FWD="${PORT_FWD:-9000}"

case "$SVC" in
  gateway|auth-server|user-service|admin-service) ;;
  *) echo "FAIL: 알 수 없는 서비스 '$SVC' (gateway|auth-server|user-service|admin-service)"; exit 1;;
esac
for b in docker kubectl; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done
if docker compose version >/dev/null 2>&1; then DC="docker compose"; else DC="docker-compose"; fi

# ── 1) 소스 콘텐츠 다이제스트 → 태그 (워킹트리 실제 파일 기준; 커밋/uncommitted 무관) ──
#    이 서비스 + 전체 framework + 빌드 배선이 바뀌면 태그가 바뀐다(= 무조건 새 pull).
echo "== 1) 소스 다이제스트 산출 =="
PATHS=()
for p in "services/$SVC" framework gradle settings.gradle build.gradle deploy/docker/Dockerfile.build; do
  [ -e "$p" ] && PATHS+=("$p")
done
TAG="src-$(
  find "${PATHS[@]}" -type f \
       \( -name '*.java' -o -name '*.gradle' -o -name '*.kts' -o -name '*.toml' \
          -o -name '*.xml' -o -name '*.yml' -o -name '*.yaml' -o -name '*.properties' \
          -o -name '*.sql' -o -name 'Dockerfile.build' \) \
  | LC_ALL=C sort | xargs sha1sum | sha1sum | cut -c1-12
)"
echo "  TAG=$TAG  (코드가 바뀌면 이 값이 바뀐다)"

# ── 2) 이미지 빌드 (컨테이너 안 Gradle; .dockerignore 가 build/.gradle 누수 차단 → 클린 컴파일) ──
echo "== 2) 빌드: $DC build $SVC =="
$DC -f deploy/compose/docker-compose.yml build "$SVC"
echo "  built=$(docker image inspect "si-msa/$SVC:local" -f '{{.Created}}')"

# ── 3) 다이제스트 태그로 push (호스트 localhost:$PORT → 노드 harbor.local, 리포 경로 동일) ──
echo "== 3) push: localhost:$PORT/si-msa/$SVC:$TAG =="
printf '%s' "$RPASS" | docker login -u "$RUSER" --password-stdin "localhost:${PORT}" >/dev/null
docker tag  "si-msa/$SVC:local" "localhost:${PORT}/si-msa/$SVC:$TAG"
docker push "localhost:${PORT}/si-msa/$SVC:$TAG"

# ── 4) set image → 태그가 콘텐츠 기반이라 캐시가 끼어들 수 없음. 템플릿 변경 → 롤아웃 자동 ──
echo "== 4) set image + rollout =="
kubectl --context "$CTX" -n "$NS" set image "deploy/$SVC" "app=harbor.local/si-msa/$SVC:$TAG"
kubectl --context "$CTX" -n "$NS" rollout status "deploy/$SVC" --timeout=180s
echo "  배포 이미지: $(kubectl --context "$CTX" -n "$NS" get "deploy/$SVC" -o jsonpath='{.spec.template.spec.containers[0].image}')"
echo "  파드 이미지: $(kubectl --context "$CTX" -n "$NS" get pod -l "app.kubernetes.io/name=$SVC" -o jsonpath='{.items[0].status.containerStatuses[0].image}')"

# ── 5) (옵션) auth-server 토큰 스모크 ──
if [ "$SMOKE" = "1" ] && [ "$SVC" = "auth-server" ]; then
  echo "== 5) 토큰 스모크(client_credentials) =="
  kubectl --context "$CTX" -n "$NS" port-forward "svc/$SVC" "${PORT_FWD}:9000" >"/tmp/pf.$SVC.log" 2>&1 &
  PF=$!; trap 'kill $PF 2>/dev/null || true' EXIT; sleep 4
  echo "--- A. 토큰(정상 시크릿) — 1~12행 ---"
  curl -s -i -u demo-service:demo-secret -d grant_type=client_credentials -d scope=api.read \
    "http://localhost:${PORT_FWD}/oauth2/token" | sed -n '1,12p'
  echo "--- C. jwks ---"
  curl -s -o /dev/null -w '  jwks => %{http_code}\n' "http://localhost:${PORT_FWD}/oauth2/jwks"
  kill $PF 2>/dev/null || true; trap - EXIT
fi
echo "✔ done ($SVC:$TAG)"
