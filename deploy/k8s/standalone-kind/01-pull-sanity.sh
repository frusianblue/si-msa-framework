#!/usr/bin/env bash
# deploy/k8s/standalone-kind/01-pull-sanity.sh
# ───────────────────────────────────────────────────────────────────────────────
# §S3' 2단계 — 최소 pull sanity (이론 맹신 금지: Harbor 풀 재구축 전에 *먼저* 검증).
# 목적(딱 하나만 증명): "standalone kind 의 노드 containerd 가 레지스트리 한정 이름을
#   *직접* pull 한다" — 이것이 Docker Desktop kind 에선 미러 인터셉트(registry-mirror:1273,
#   ?ns=harbor.local: 500)로 불가능했던 바로 그 동작이다(PITFALLS §9).
# 통과(PASS)해야 §S3' 3단계(Harbor/ingress/postgres 풀 재구축)로 진행한다.
#
# 무엇을 안 건드리나: 현 docker-desktop kind 클러스터(별도 컨텍스트)는 손대지 않는다.
#   여기서 만드는 건 이름이 다른 standalone 클러스터(kind-sanity)다.
#
# 실행: bash deploy/k8s/standalone-kind/01-pull-sanity.sh
# 정리: bash deploy/k8s/standalone-kind/00-cleanup.sh --teardown-sanity
# ───────────────────────────────────────────────────────────────────────────────
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"   # extraMounts 의 ./certs.d 상대경로가 여기 기준으로 해소되도록

CLUSTER="${CLUSTER:-sanity}"
CTX="kind-${CLUSTER}"
REG_NAME="${REG_NAME:-kind-registry}"
REG_PORT="${REG_PORT:-5001}"
REPO="sanity/busybox"          # 레지스트리에 저장되는 "리포지토리 경로"(호스트명 무관)
TAG="test"
PUSH_REF="localhost:${REG_PORT}/${REPO}:${TAG}"   # 호스트가 push 하는 이름(published 포트)
PULL_REF="reg.local/${REPO}:${TAG}"               # 노드가 pull 하는 이름(레지스트리 한정 = harbor.local 등가)
# ⚠️ PUSH_REF 와 PULL_REF 는 레지스트리 호스트명만 다르고 리포지토리 경로(sanity/busybox)는 동일.
#    레지스트리는 호스트명이 아니라 리포지토리 경로로 저장/서빙하므로 같은 블롭을 가리킨다.
#    (호스트: localhost:5001 published 포트로 push / 노드: reg.local→certs.d→kind-registry:5000 으로 pull)

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

echo "== 2) standalone kind 클러스터(${CLUSTER}, 3노드) 생성 =="
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
  PULLED=$(kubectl --context "${CTX}" get events --field-selector involvedObject.name=pull-sanity \
            -o jsonpath='{range .items[*]}{.reason}{" "}{end}' 2>/dev/null | tr ' ' '\n' | grep -c '^Pulled$' || true)
  echo
  echo "✅ PASS — 노드 containerd 가 레지스트리 한정 이름(${PULL_REF})을 직접 pull 했다."
  echo "         node=${NODE}"
  echo "         imageID=${IMGID}"
  echo "         Pulled 이벤트=${PULLED}건"
  echo
  echo "→ standalone kind 트랙 유효. 다음: §S3' 3) Harbor/ingress/postgres 풀 재구축 → 4) dev overlay apply."
  echo "  (정리: bash 00-cleanup.sh --teardown-sanity)"
else
  echo
  echo "❌ FAIL — 파드가 Ready 안 됨. Events:"
  kubectl --context "${CTX}" describe pod pull-sanity | sed -n '/Events:/,$p' || true
  echo
  echo "트리아지:"
  echo "  · 'reg.local ... connection refused/timeout/500' → certs.d 미적용."
  echo "       확인: docker exec ${CLUSTER}-worker cat /etc/containerd/certs.d/reg.local/hosts.toml"
  echo "       (비었거나 없음 = extraMounts 마운트 실패 → Windows 파일공유 경로/상대경로 cd 확인.)"
  echo "  · 'docker.io/sanity/busybox' 로 향함 → 이미지명 레지스트리 부분에 점(.)이 없어 Docker Hub 로 폴백된 것(이름 규칙)."
  echo "  · 'kind-registry' 이름해소 실패 → 3단계(network connect kind) 누락 — docker network inspect kind 로 확인."
  exit 1
fi
