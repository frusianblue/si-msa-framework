#!/usr/bin/env bash
# deploy/k8s/prod-kind/12-register-svc.sh
# ─────────────────────────────────────────────────────────────────────────────
# 2단계 (3/3): kind-svc 를 ArgoCD(hub) 의 원격 클러스터로 등록 — 선언형 cluster Secret(argocd CLI 불요).
#
# ★ kind 멀티클러스터 함정(스펙 §2): kind 가 주는 kubeconfig 의 server 는
#     - 기본:      https://127.0.0.1:<hostport>   ← ArgoCD 파드(cicd 안)가 호스트 루프백을 못 감
#     - --internal: https://svc-control-plane:6443 ← 파드는 그 이름을 CoreDNS 로 못 품
#   → --internal kubeconfig 의 CA/클라이언트 인증서는 *그대로* 쓰되, server 만
#     https://<svc-control-plane 컨테이너 IP>:6443 으로 바꿔 등록(같은 kind 도커망, G2 도달 증명됨).
#
# TLS: kind apiserver 인증서 SAN 에 노드 IP 가 보통 포함됨 → caData 검증(insecure=false) 기본.
#   만약 91-verify 에서 TLS SAN 오류가 나면 SVC_TLS_INSECURE=true 로 재실행(리허설 한정 skip-verify).
#   실 prod 는 apiserver certSANs 에 접속 IP/도메인을 명시 → 항상 CA 검증.
#
# 실행:  bash deploy/k8s/prod-kind/12-register-svc.sh
#        SVC_TLS_INSECURE=true bash deploy/k8s/prod-kind/12-register-svc.sh   # SAN 오류 시 폴백
# 멱등:  Secret apply(덮어쓰기).
# 다음:  91-verify-argocd.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

CICD_CTX="kind-cicd"; NS="argocd"
SVC_CLUSTER="svc"; SVC_CP_NODE="svc-control-plane"; CLUSTER_NAME="kind-svc"
INSECURE="${SVC_TLS_INSECURE:-false}"

echo "== 0) 전제 =="
for b in kubectl kind docker; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done
kubectl --context "$CICD_CTX" -n "$NS" get deploy argocd-server >/dev/null 2>&1 \
  || { echo "FAIL: ArgoCD 미설치 → 먼저 11-argocd-install.sh"; exit 1; }
kind get clusters 2>/dev/null | grep -qx "$SVC_CLUSTER" || { echo "FAIL: kind-$SVC_CLUSTER 없음"; exit 1; }

echo "== 1) svc CP 컨테이너 IP 산출(kind 도커망) =="
SVC_CP_IP="$(docker inspect -f '{{(index .NetworkSettings.Networks "kind").IPAddress}}' "$SVC_CP_NODE" 2>/dev/null || true)"
[ -n "$SVC_CP_IP" ] || { echo "FAIL: $SVC_CP_NODE 의 kind IP 산출 실패"; exit 1; }
SERVER="https://${SVC_CP_IP}:6443"
echo "  $CLUSTER_NAME server = $SERVER"

echo "== 2) kind-svc --internal kubeconfig 에서 CA/클라이언트 인증서 추출 =="
KCFG="$(mktemp)"; trap 'rm -f "$KCFG"' EXIT
kind get kubeconfig --name "$SVC_CLUSTER" --internal > "$KCFG"
CA="$(kubectl --kubeconfig "$KCFG" config view --raw -o jsonpath='{.clusters[0].cluster.certificate-authority-data}')"
CERT="$(kubectl --kubeconfig "$KCFG" config view --raw -o jsonpath='{.users[0].user.client-certificate-data}')"
KEY="$(kubectl --kubeconfig "$KCFG" config view --raw -o jsonpath='{.users[0].user.client-key-data}')"
[ -n "$CA" ] && [ -n "$CERT" ] && [ -n "$KEY" ] || { echo "FAIL: kubeconfig 인증서 추출 실패"; exit 1; }

echo "== 3) ArgoCD cluster Secret 생성 (insecure=$INSECURE) =="
if [ "$INSECURE" = "true" ]; then
  CONFIG_JSON="$(printf '{"tlsClientConfig":{"insecure":true,"certData":"%s","keyData":"%s"}}' "$CERT" "$KEY")"
else
  CONFIG_JSON="$(printf '{"tlsClientConfig":{"insecure":false,"caData":"%s","certData":"%s","keyData":"%s"}}' "$CA" "$CERT" "$KEY")"
fi
cat <<YAML | kubectl --context "$CICD_CTX" apply -f -
apiVersion: v1
kind: Secret
metadata:
  name: ${CLUSTER_NAME}
  namespace: ${NS}
  labels:
    argocd.argoproj.io/secret-type: cluster
type: Opaque
stringData:
  name: ${CLUSTER_NAME}
  server: ${SERVER}
  config: '${CONFIG_JSON}'
YAML

echo "== 4) application-controller 재기동(클러스터 캐시 갱신) =="
kubectl --context "$CICD_CTX" -n "$NS" rollout restart statefulset/argocd-application-controller 2>/dev/null \
  || kubectl --context "$CICD_CTX" -n "$NS" rollout restart deploy/argocd-application-controller 2>/dev/null || true

echo
echo "✅ kind-svc 등록 완료 (server=$SERVER, insecure=$INSECURE)."
echo "   확인: ArgoCD UI → Settings → Clusters 에 '$CLUSTER_NAME' 가 Successful 로."
echo "   다음: bash 91-verify-argocd.sh"
