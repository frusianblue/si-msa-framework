#!/usr/bin/env bash
# deploy/k8s/standalone-kind/01-pull-sanity.sh
# ───────────────────────────────────────────────────────────────────────────────
# 최소 pull sanity — "standalone kind 의 노드 containerd 가 레지스트리 한정 이름을 *직접* pull 한다"를 증명.
# = Docker Desktop kind 에선 미러 인터셉트로 불가능했던 동작(PITFALLS §9). 통과해야 Harbor/ingress 로 진행.
#
# 【B안 개정 (2026-06-07 세션4)】 kind-config 에서 extraMounts(./certs.d) 가 제거됨(재부팅 휘발 졸업).
#   → 노드 certs.d 가 더는 마운트로 안 깔리므로, 이 스크립트가 클러스터 생성 직후 reg.local certs.d 를
#     **docker exec 로 각 노드에 직접 시드**한다(DaemonSet 적용 전 sanity 단계라 자기완결로).
#
# 실행: bash deploy/k8s/standalone-kind/01-pull-sanity.sh
# 정리: bash deploy/k8s/standalone-kind/00-cleanup.sh --teardown-sanity
# ───────────────────────────────────────────────────────────────────────────────
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

CLUSTER="${CLUSTER:-sanity}"
CTX="kind-${CLUSTER}"
REG_NAME="${REG_NAME:-kind-registry}"
REG_PORT="${REG_PORT:-5001}"
REPO="sanity/busybox"
TAG="test"
PUSH_REF="localhost:${REG_PORT}/${REPO}:${TAG}"   # 호스트가 push 하는 이름(published 포트)
PULL_REF="reg.local/${REPO}:${TAG}"               # 노드가 pull 하는 이름(레지스트리 한정 = harbor.local 등가)

echo "== 0) 선행 도구 점검 =="
for bin in docker kind kubectl; do
  command -v "$bin" >/dev/null 2>&1 || { echo "FAIL: '$bin' 가 PATH 에 없음"; exit 1; }
done
echo "  docker/kind/kubectl OK"

echo "== 1) 로컬 레지스트리 컨테이너(${REG_NAME}) 기동 =="
if [ "$(docker inspect -f '{{.State.Running}}' "${REG_NAME}" 2>/dev/null || true)" = 'true' ]; then
  echo "  이미 실행 중 — 재사용"
else
  docker run -d --restart=always -p "127.0.0.1:${REG_PORT}:5000" --name "${REG_NAME}" registry:2 >/dev/null
  echo "  생성: 127.0.0.1:${REG_PORT} → registry:2"
fi

echo "== 2) standalone kind 클러스터(${CLUSTER}, 3노드 + extraPortMappings 80/443 + ingress-ready) 생성 =="
if kind get clusters 2>/dev/null | grep -qx "${CLUSTER}"; then
  echo "  이미 존재 — 재사용(노드 설정 갈아끼우려면 먼저 00-cleanup.sh --teardown-sanity)"
else
  kind create cluster --name "${CLUSTER}" --config kind-config.yaml
fi

echo "== 3) 레지스트리를 kind 네트워크에 연결(노드의 kind-registry 이름해소) =="
if [ "$(docker inspect -f '{{json .NetworkSettings.Networks.kind}}' "${REG_NAME}" 2>/dev/null || echo null)" = 'null' ]; then
  docker network connect kind "${REG_NAME}"
  echo "  연결됨: ${REG_NAME} → network 'kind'"
else
  echo "  이미 연결됨"
fi

echo "== 3.5) 노드 certs.d 직접 시드(reg.local) — extraMounts 졸업 대체 =="
# B안: extraMounts 가 없으므로 sanity 단계에서 reg.local certs.d 를 노드에 직접 써넣는다.
#   (Harbor 단계에선 registry-trust DaemonSet 이 이 역할을 영속·재부팅내성으로 인계.)
for n in $(docker ps --format '{{.Names}}' | grep "^${CLUSTER}-"); do
  docker exec "$n" mkdir -p /etc/containerd/certs.d/reg.local
  docker exec -i "$n" sh -c 'cat > /etc/containerd/certs.d/reg.local/hosts.toml' <<'TOML'
[host."http://kind-registry:5000"]
  capabilities = ["pull", "resolve"]
TOML
  echo "  $n: /etc/containerd/certs.d/reg.local/hosts.toml"
done

echo "== 4) 더미 이미지 push(호스트 → 레지스트리) =="
docker pull busybox:1.36 >/dev/null
docker tag busybox:1.36 "${PUSH_REF}"
docker push "${PUSH_REF}"
echo "  push: ${PUSH_REF}  (리포지토리=${REPO})"

echo "== 5) 파드 배포 — 노드가 ${PULL_REF} 를 *직접* pull(IfNotPresent 캐시 우회 위해 Always) =="
kubectl --context "${CTX}" delete pod pull-sanity --ignore-not-found >/dev/null 2>&1 || true
kubectl --context "${CTX}" run pull-sanity \
  --image="${PULL_REF}" --image-pull-policy=Always \
  --restart=Never --command -- sleep 600

echo "== 6) 검증: 노드 pull 성공 → 파드 Ready =="
if kubectl --context "${CTX}" wait --for=condition=Ready pod/pull-sanity --timeout=120s; then
  NODE=$(kubectl --context "${CTX}" get pod pull-sanity -o jsonpath='{.spec.nodeName}')
  IMGID=$(kubectl --context "${CTX}" get pod pull-sanity -o jsonpath='{.status.containerStatuses[0].imageID}')
  echo
  echo "✅ PASS — 노드 containerd 가 레지스트리 한정 이름(${PULL_REF})을 직접 pull 했다."
  echo "         node=${NODE}"
  echo "         imageID=${IMGID}"
  echo
  echo "→ 다음: 10-ingress-nginx.sh → 07-reboot-recover.sh(+apply -k overlays/dev) → 08-harbor-install.sh → 09-jenkins-install.sh → 11-host-access-verify.sh"
  echo "  (정리: bash 00-cleanup.sh --teardown-sanity)"
else
  echo
  echo "❌ FAIL — 파드가 Ready 안 됨. Events:"
  kubectl --context "${CTX}" describe pod pull-sanity | sed -n '/Events:/,$p' || true
  echo
  echo "트리아지:"
  echo "  · 'reg.local ... connection refused/timeout/500' → certs.d 미시드(3.5 단계 확인)."
  echo "       확인: docker exec ${CLUSTER}-worker cat /etc/containerd/certs.d/reg.local/hosts.toml"
  echo "  · 'docker.io/sanity/busybox' 로 향함 → 이미지명 레지스트리 부분에 점(.)이 없어 Docker Hub 폴백(이름 규칙)."
  echo "  · 'kind-registry' 이름해소 실패 → 3단계(network connect kind) 누락 — docker network inspect kind 로 확인."
  exit 1
fi
