#!/usr/bin/env bash
# deploy/k8s/standalone-kind/02-auth-pull-sanity.sh
# ───────────────────────────────────────────────────────────────────────────────
# §S3' 3단계 첫 조각 — 비공개(인증) 레지스트리 → 노드 pull(imagePullSecrets) 실증.
# 왜: docker-desktop kind 에선 노드 *도달층*(미러 인터셉트)에서 막혀 *인증* 경로를 끝까지 못 봤다
#     (harbor-cred/SA 부착은 맞았지만 pull 자체가 노드에서 안 됨, PITFALLS §9). 01 이 도달층을 닫았으니
#     이제 "harbor.local(비공개, Basic auth) + harbor-cred(dockerconfigjson)" 경로를 끝까지 검증.
#     PASS 면 dev overlay 의 pull-secret-dev.yaml(harbor-cred + default SA) 경로가 standalone 에서 유효함이 증명되고,
#     이 레지스트리가 곧 4단계(실 si-msa/<svc>:dev push → kubectl apply -k overlays/dev)의 토대가 된다.
# 전제: 01-pull-sanity.sh 로 kind-sanity 가 떠 있음(없으면 생성 시도). certs.d/harbor.local 는 zip 에 포함(바인드마운트 라이브 반영).
#
# 실행: bash deploy/k8s/standalone-kind/02-auth-pull-sanity.sh
# ───────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

CLUSTER="${CLUSTER:-sanity}"; CTX="kind-${CLUSTER}"
REG="${REG:-harbor-auth-reg}"; PORT="${PORT:-5443}"
RUSER="admin"; RPASS="Harbor12345"     # dev overlay harbor-cred 와 동일(YWRtaW46SGFyYm9yMTIzNDU=)
REPO="si-msa/busybox"; TAG="authtest"  # 프로젝트 경로(si-msa/) 미러
PUSH_REF="localhost:${PORT}/${REPO}:${TAG}"
PULL_REF="harbor.local/${REPO}:${TAG}"

echo "== 0) 도구 + 클러스터 + certs.d =="
for b in docker kind kubectl; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done
if ! kind get clusters 2>/dev/null | grep -qx "${CLUSTER}"; then
  echo "  kind-sanity 없음 → 01-pull-sanity.sh 먼저 권장. 일단 생성:"
  kind create cluster --name "${CLUSTER}" --config kind-config.yaml
fi
mkdir -p certs.d/harbor.local
if [ ! -f certs.d/harbor.local/hosts.toml ]; then
  printf '[host."http://%s:5000"]\n  capabilities = ["pull", "resolve"]\n' "${REG}" > certs.d/harbor.local/hosts.toml
fi
echo "  certs.d/harbor.local/hosts.toml:"; sed 's/^/    /' certs.d/harbor.local/hosts.toml

echo "== 1) htpasswd 인증 레지스트리(${REG}) 기동 =="
mkdir -p .auth
docker run --rm --entrypoint htpasswd httpd:2 -Bbn "${RUSER}" "${RPASS}" > .auth/htpasswd
if [ "$(docker inspect -f '{{.State.Running}}' "${REG}" 2>/dev/null || true)" = 'true' ]; then
  echo "  이미 실행 중 — 재사용"
else
  docker run -d --restart=always --name "${REG}" -p "127.0.0.1:${PORT}:5000" \
    -v "${PWD}/.auth:/auth" \
    -e REGISTRY_AUTH=htpasswd \
    -e "REGISTRY_AUTH_HTPASSWD_REALM=Registry Realm" \
    -e REGISTRY_AUTH_HTPASSWD_PATH=/auth/htpasswd \
    registry:2 >/dev/null
  echo "  생성: 127.0.0.1:${PORT} (Basic auth ${RUSER}/${RPASS})"
fi

echo "== 2) 레지스트리를 kind 네트워크에 연결 =="
if [ "$(docker inspect -f '{{json .NetworkSettings.Networks.kind}}' "${REG}" 2>/dev/null || echo null)" = 'null' ]; then
  docker network connect kind "${REG}"; echo "  연결됨"
else echo "  이미 연결됨"; fi

echo "== 3) login + push(호스트 → 비공개 레지스트리) =="
docker pull busybox:1.36 >/dev/null
printf '%s' "${RPASS}" | docker login -u "${RUSER}" --password-stdin "localhost:${PORT}"
docker tag busybox:1.36 "${PUSH_REF}"
docker push "${PUSH_REF}"
echo "  push: ${PUSH_REF}"

echo "== 4) harbor-cred(dockerconfigjson) + imagePullSecrets 파드 =="
kubectl --context "${CTX}" delete pod auth-pull-sanity --ignore-not-found >/dev/null 2>&1 || true
kubectl --context "${CTX}" delete secret harbor-cred --ignore-not-found >/dev/null 2>&1 || true
# docker-server=harbor.local 는 PULL_REF 의 레지스트리명과 일치해야 kubelet 이 이 cred 를 고른다(= dev overlay 와 동일 키).
kubectl --context "${CTX}" create secret docker-registry harbor-cred \
  --docker-server=harbor.local --docker-username="${RUSER}" --docker-password="${RPASS}"
kubectl --context "${CTX}" run auth-pull-sanity \
  --image="${PULL_REF}" --image-pull-policy=Always --restart=Never \
  --overrides='{"spec":{"imagePullSecrets":[{"name":"harbor-cred"}]}}' \
  --command -- sleep 600

echo "== 5) 검증: 비공개 레지스트리 → 노드 pull(인증 경로) =="
if kubectl --context "${CTX}" wait --for=condition=Ready pod/auth-pull-sanity --timeout=120s; then
  NODE=$(kubectl --context "${CTX}" get pod auth-pull-sanity -o jsonpath='{.spec.nodeName}')
  echo
  echo "✅ PASS — 노드가 *비공개(Basic auth)* 레지스트리 한정 이름(${PULL_REF})을"
  echo "         imagePullSecrets(harbor-cred)로 pull 했다.  node=${NODE}"
  echo
  echo "→ dev overlay 의 pull-secret-dev.yaml(harbor-cred + default SA) 경로가 standalone 에서 유효."
  echo "  다음(4단계): 실 si-msa/<svc>:dev 를 이 레지스트리에 push → kubectl apply -k deploy/k8s/overlays/dev"
  echo "             (overlay 핀이 harbor.local 이고 이 레지스트리가 harbor.local 로 서빙 = 정합)."
  echo "  (참고 음성대조: --overrides 빼고 같은 파드 띄우면 401 로 ImagePullBackOff = 인증이 실제로 작동 중인 증거.)"
else
  echo
  echo "❌ FAIL — Events:"
  kubectl --context "${CTX}" describe pod auth-pull-sanity | sed -n '/Events:/,$p' || true
  echo
  echo "트리아지:"
  echo "  · '401 Unauthorized' → harbor-cred 의 docker-server(harbor.local) ≠ 이미지 레지스트리명, 또는 htpasswd 불일치."
  echo "  · 'connection refused/no route to host' → kind 네트워크 미연결(2단계) 또는 certs.d/harbor.local 미반영."
  echo "  · 'http: server gave HTTP response to HTTPS client' → hosts.toml 이 http:// 인지 확인."
  exit 1
fi
