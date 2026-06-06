#!/usr/bin/env bash
# deploy/k8s/standalone-kind/03-dev-overlay-up.sh
# ───────────────────────────────────────────────────────────────────────────────
# §S3' 4단계 — 실 서비스 이미지 빌드 → harbor.local(harbor-auth-reg) push → dev overlay apply → 검증.
#   (B 결정: 02 의 인증 레지스트리로 충분 → 프레임워크 동작 검증으로 직행.)
# 전제: 02-auth-pull-sanity.sh 까지 PASS(= harbor-auth-reg 가 kind 네트워크에 떠 있고 인증 pull 실증).
# 호스트는 localhost:5443 로 push, 노드는 harbor.local 로 pull(certs.d→harbor-auth-reg:5000) — 리포지토리 경로 동일.
# overlay 핀이 harbor.local/si-msa/<svc>:dev 라 매니페스트 무수정.
#
# 실행:  bash deploy/k8s/standalone-kind/03-dev-overlay-up.sh          # 빌드+push+apply+(6파드/DB 검증)
#        bash deploy/k8s/standalone-kind/03-dev-overlay-up.sh --smoke  # + AS 토큰 스모크(시드 클라이언트 켜고 client_credentials)
#        REBUILD=1 ... 03-dev-overlay-up.sh                            # si-msa/<svc>:local 강제 재빌드
# ───────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$HERE/../../.." && pwd)"
cd "$REPO_ROOT"

CLUSTER="${CLUSTER:-sanity}"; CTX="kind-${CLUSTER}"
AUTH_REG="${AUTH_REG:-harbor-auth-reg}"; PORT="${PORT:-5443}"
RUSER="admin"; RPASS="Harbor12345"
NS="si-msa"
SERVICES="gateway auth-server user-service admin-service"
SMOKE=0; [ "${1:-}" = "--smoke" ] && SMOKE=1

echo "== 0) 전제 점검 =="
for b in docker kind kubectl; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done
kind get clusters 2>/dev/null | grep -qx "$CLUSTER" || { echo "FAIL: kind-$CLUSTER 없음 → 01/02 먼저"; exit 1; }
if [ "$(docker inspect -f '{{.State.Running}}' "$AUTH_REG" 2>/dev/null || true)" != 'true' ]; then
  echo "FAIL: $AUTH_REG 미실행 → 02-auth-pull-sanity.sh 먼저(레지스트리 + kind 네트워크 연결)"; exit 1
fi
if [ "$(docker inspect -f '{{json .NetworkSettings.Networks.kind}}' "$AUTH_REG" 2>/dev/null || echo null)" = 'null' ]; then
  echo "  $AUTH_REG kind 네트워크 미연결 → 연결"; docker network connect kind "$AUTH_REG"
fi
echo "  OK (cluster=$CTX, registry=$AUTH_REG:$PORT)"

# docker compose v2/v1 감지
if docker compose version >/dev/null 2>&1; then DC="docker compose"; else DC="docker-compose"; fi

echo "== 1) 이미지 빌드(si-msa/<svc>:local) =="
NEED_BUILD=0
for s in $SERVICES; do docker image inspect "si-msa/$s:local" >/dev/null 2>&1 || NEED_BUILD=1; done
if [ "${REBUILD:-0}" = "1" ] || [ "$NEED_BUILD" = "1" ]; then
  echo "  $DC build (Dockerfile.build = 컨테이너 안 Gradle, Maven Central 인터넷 필요) ..."
  $DC -f deploy/compose/docker-compose.yml build $SERVICES
else
  echo "  4개 si-msa/<svc>:local 존재 — 재빌드 생략(REBUILD=1 로 강제 가능)"
fi

echo "== 2) login + push(호스트 localhost:$PORT → si-msa/<svc>:dev) =="
printf '%s' "$RPASS" | docker login -u "$RUSER" --password-stdin "localhost:${PORT}"
for s in $SERVICES; do
  docker tag "si-msa/$s:local" "localhost:${PORT}/si-msa/$s:dev"
  docker push "localhost:${PORT}/si-msa/$s:dev"
done
echo "  push 완료(4개 :dev). 노드는 harbor.local/si-msa/<svc>:dev 로 pull."

echo "== 3) dev overlay apply =="
kubectl --context "$CTX" apply -k deploy/k8s/overlays/dev

echo "== 4) 워크로드 Ready 대기(postgres → redis → 앱) =="
kubectl --context "$CTX" -n "$NS" rollout status statefulset/postgres --timeout=300s
kubectl --context "$CTX" -n "$NS" rollout status deploy/redis --timeout=180s
for d in gateway auth-server user-service admin-service; do
  kubectl --context "$CTX" -n "$NS" rollout status "deploy/$d" --timeout=300s
done

echo "== 5) 상태/이미지 pull 확인 =="
kubectl --context "$CTX" -n "$NS" get pods -o wide
PULLED=$(kubectl --context "$CTX" -n "$NS" get events --field-selector reason=Pulled \
          -o jsonpath='{range .items[*]}{.involvedObject.name}{"\n"}{end}' 2>/dev/null | grep -cE 'gateway|auth-server|user-service|admin-service' || true)
echo "  앱 파드 Pulled 이벤트=${PULLED}건 (노드가 harbor.local 에서 실제 pull)"

echo "== 6) DB 검증(authdb/sidb/admindb 생성 = initdb 정상) =="
DBS=$(kubectl --context "$CTX" -n "$NS" exec statefulset/postgres -- \
        psql -U postgres -tAc "SELECT datname FROM pg_database WHERE datname IN ('authdb','sidb','admindb') ORDER BY 1;" 2>/dev/null | tr '\n' ' ' || true)
echo "  존재 DB: ${DBS:-(조회 실패)}"
case "$DBS" in *authdb*sidb*) echo "  ✅ 3 DB 정상(admin=admindb 분리 → user↔admin Flyway 충돌 회피)";; *) echo "  ⚠️ DB 누락 — PITFALLS §9(initdb 1회성) 트리아지";; esac

if [ "$SMOKE" = "1" ]; then
  echo "== 7) AS 토큰 스모크(--smoke): 시드 클라이언트 켜고 client_credentials =="
  kubectl --context "$CTX" -n "$NS" set env deploy/auth-server FRAMEWORK_AUTH_SEED_SMOKE_CLIENT=true
  kubectl --context "$CTX" -n "$NS" rollout restart deploy/auth-server
  kubectl --context "$CTX" -n "$NS" rollout status deploy/auth-server --timeout=180s
  echo "  port-forward 9000 → demo-service(client_credentials) 토큰 요청"
  kubectl --context "$CTX" -n "$NS" port-forward deploy/auth-server 9000:9000 >/dev/null 2>&1 &
  PF=$!; trap 'kill $PF 2>/dev/null || true' EXIT; sleep 4
  RESP=$(curl -s -u demo-service:demo-secret \
           -d grant_type=client_credentials -d scope=api.read \
           http://localhost:9000/oauth2/token || true)
  kill $PF 2>/dev/null || true; trap - EXIT
  if printf '%s' "$RESP" | grep -q 'access_token'; then
    echo "  ✅ access_token 발급 성공(이중 발급기 self-issued 경로 그린)."
  else
    echo "  ⚠️ 토큰 미발급. 응답: $RESP"
    echo "     (시드 반영까지 재시도 필요할 수 있음; demo-service/demo-secret, scope api.read 확인.)"
  fi
fi

echo
echo "──────────────────────────────────────────────────────────────"
echo "그린 기준: 6파드 Ready + 앱 Pulled>0 + authdb/sidb/admindb + (--smoke 시) access_token."
echo "다음: S4 애드온(metrics-server/HPA → kube-prometheus-stack) → S5 prod-rehearsal → S6 상위흐름 → S7 Jenkins(sha 핀 자동)."
echo "정리: bash deploy/k8s/standalone-kind/00-cleanup.sh --teardown-sanity"
