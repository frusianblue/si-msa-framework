#!/usr/bin/env bash
# deploy/k8s/standalone-kind/10-ingress-nginx.sh
# ─────────────────────────────────────────────────────────────────────────────
# ingress-nginx 설치 (standalone kind, host 노출 B안의 진입 계층).
#
# 왜 kind 전용 매니페스트인가:
#   - LoadBalancer 가 아니라 *컨트롤러 파드가 hostPort 80/443* 를 잡는다.
#   - kind-config 의 extraPortMappings 80→80/443→443 가 그 hostPort 를 호스트 localhost 로 게시.
#   ⇒ 호스트 브라우저: http://localhost → ingress(404) / http://harbor.local → Harbor.
#
# ★ 함정 (2026-06-07 세션4, PITFALLS §9): controller-v1.13.0 의 kind provider 매니페스트는
#   더 이상 `nodeSelector: ingress-ready=true` 를 포함하지 않는다(예전 버전엔 있었음).
#   nodeSelector 가 `kubernetes.io/os: linux` 뿐이라 컨트롤러가 worker 로 떠서 hostPort 80 을
#   worker 에 잡으면, 호스트로 게시된 건 control-plane:80 이라 빈 포트 → curl localhost = reset.
#   → 그래서 apply 직후 1.5단계에서 Deployment 를 patch 해 control-plane 에 고정한다
#     (nodeSelector: ingress-ready=true + kubernetes.io/os=linux, control-plane taint toleration).
#   kind-config(B안) 가 control-plane 에 ingress-ready=true 라벨을 부여하는 것이 전제.
#
# 핀: ingress-nginx controller v1.13.0 (kind provider manifest). 재현성 위해 latest 금지·태그 고정.
# 전제: kind-config(B안) 로 생성된 kind-sanity. 01-pull-sanity.sh PASS.
# 실행: bash deploy/k8s/standalone-kind/10-ingress-nginx.sh
# 멱등: apply 재실행 무해. 컨트롤러 Ready 까지 대기.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

CLUSTER="${CLUSTER:-sanity}"; CTX="kind-${CLUSTER}"
ING_VER="${ING_VER:-controller-v1.13.0}"
MANIFEST="https://raw.githubusercontent.com/kubernetes/ingress-nginx/${ING_VER}/deploy/static/provider/kind/deploy.yaml"

echo "== 0) 전제 =="
for b in kubectl docker; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done
kind get clusters 2>/dev/null | grep -qx "$CLUSTER" || { echo "FAIL: kind-$CLUSTER 없음"; exit 1; }

echo "== 0.5) ingress-ready 라벨 확인(B안 kind-config 전제) =="
LABELED="$(kubectl --context "$CTX" get nodes -l ingress-ready=true -o name 2>/dev/null | wc -l | tr -d ' ')"
if [ "$LABELED" = "0" ]; then
  echo "  ⚠️ ingress-ready=true 노드 없음 → kind-config(B안) 로 재생성하지 않았을 수 있음."
  echo "     임시 라벨 부여 시도(control-plane):"
  kubectl --context "$CTX" label node "${CLUSTER}-control-plane" ingress-ready=true --overwrite || true
fi

echo "== 1) ingress-nginx(kind provider, ${ING_VER}) 설치 =="
kubectl --context "$CTX" apply -f "$MANIFEST"

echo "== 1.5) 컨트롤러를 control-plane 에 고정(★ v1.13.0 매니페스트 nodeSelector 누락 보정) =="
# v1.13.0 kind 매니페스트엔 ingress-ready nodeSelector 가 없어 컨트롤러가 worker 로 뜬다.
# → extraPortMappings 가 게시한 control-plane:80 과 어긋나 host curl 이 reset.
# Deployment 를 patch: nodeSelector(ingress-ready=true + linux) + control-plane taint toleration.
kubectl --context "$CTX" -n ingress-nginx patch deploy ingress-nginx-controller --type merge -p \
  '{"spec":{"template":{"spec":{"nodeSelector":{"ingress-ready":"true","kubernetes.io/os":"linux"},"tolerations":[{"key":"node-role.kubernetes.io/control-plane","operator":"Exists","effect":"NoSchedule"}]}}}}'

echo "== 2) 컨트롤러 Ready 대기(고정 후 재스케줄 포함) =="
kubectl --context "$CTX" -n ingress-nginx wait --for=condition=Available deploy/ingress-nginx-controller --timeout=180s || true
kubectl --context "$CTX" -n ingress-nginx wait --namespace ingress-nginx \
  --for=condition=Ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=180s

echo "== 3) PASS 게이트 — 호스트에서 ingress 진입 확인 =="
echo "  (extraPortMappings 로 컨트롤러 hostPort 80 이 호스트 localhost:80 에 게시됨)"
CODE="$(curl -s -o /dev/null -w '%{http_code}' http://localhost/ 2>/dev/null || echo 000)"
echo "  curl http://localhost/  → HTTP ${CODE}"
if [ "$CODE" = "404" ]; then
  echo
  echo "✅ PASS — ingress-nginx 가 호스트 localhost:80 에서 응답(404 = 매칭 룰 없음 = 컨트롤러 정상)."
  echo "→ 다음: 08-harbor-install.sh(Harbor ingress, harbor.local) → 09-jenkins-install.sh(jenkins.local)."
else
  echo
  echo "⚠️ HTTP ${CODE} (404 기대). 트리아지:"
  echo "  · connection refused → extraPortMappings 부재(구 kind-config 로 생성됨) 또는 host 80 점유."
  echo "       확인: docker inspect ${CLUSTER}-control-plane -f '{{json .NetworkSettings.Ports}}'  ('80/tcp' 게시 여부)"
  echo "  · connection reset → 컨트롤러가 control-plane 이 아닌 worker 에 떠서 hostPort 80 이 어긋남."
  echo "       확인: kubectl --context $CTX -n ingress-nginx get pods -o wide  (NODE = ${CLUSTER}-control-plane 이어야 함)"
  echo "       보정: 위 1.5단계 patch 가 적용됐는지 / control-plane 라벨(ingress-ready=true) 존재 확인."
  echo "  · 컨트롤러 Pending → ingress-ready 라벨 없음(0.5 단계) 또는 taint toleration 누락."
  echo "       확인: kubectl --context $CTX -n ingress-nginx describe pod -l app.kubernetes.io/component=controller | tail -20"
fi
