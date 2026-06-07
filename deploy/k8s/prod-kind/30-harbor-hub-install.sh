#!/usr/bin/env bash
# deploy/k8s/prod-kind/30-harbor-hub-install.sh
# ─────────────────────────────────────────────────────────────────────────────
# 4단계 (1/2): Harbor(hub) 설치 — standalone-kind/08-harbor-install.sh 의 멀티클러스터 적응판.
#   대상 = kind-cicd(hub). 서비스 클러스터(kind-svc)는 03-cross-trust 에서 이미 harbor.local→cicd CP IP 신뢰 배선됨.
#
#   ★ standalone(단일 클러스터)과의 차이:
#     standalone 08 은 "Harbor·빌더(Kaniko)·pull 워크로드"가 같은 클러스터라 CoreDNS 주입 + 노드 certs.d 를 다 깐다.
#     prod-kind 는 역할이 갈린다:
#       · push       = 호스트 docker → 127.0.0.1:80(cicd extraPortMappings) → ingress → harbor-core  (40-promote)
#       · pull       = kind-svc 노드 containerd → cicd CP IP:80 → ingress → harbor-core              (03 에서 신뢰 배선)
#     → kind-cicd 자신은 harbor.local 을 pull 하는 워크로드가 없으므로(서비스는 kind-svc), cicd 노드 certs.d/CoreDNS 불요.
#       이 스크립트는 Harbor 본체 설치 + si-msa 프로젝트 생성까지. (인-클러스터 Kaniko 빌드로 전환할 땐 08 의 §6/§7 를 다시 켠다.)
#
# 전제: 2~3단계 PASS(10-cicd-ingress 로 ingress-nginx Ready) + helm CLI.
#       호스트 push 사전조건(40-promote 에서 실증) — Docker Desktop daemon 에 insecure-registries: ["harbor.local"]
#       + Windows/WSL hosts 에 '127.0.0.1 harbor.local'. (HTTP 평문 레지스트리라 insecure 등록 필요.)
# 실행: bash deploy/k8s/prod-kind/30-harbor-hub-install.sh
# 멱등: helm upgrade --install / project 409 무해.
# 다음: 40-promote.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

CTX="kind-cicd"; CLUSTER="cicd"
HARBOR_NS="harbor"; HOSTNAME_HARBOR="harbor.local"
ADMIN="admin"; PASS="${HARBOR_PASS:-Harbor12345}"; PROJECT="si-msa"
# standalone 자산 단일 소스 재사용(values 동일 — ingress·HTTP 평문·proxy-body-size 0·local-path PVC).
VALUES="${VALUES:-../standalone-kind/harbor-values.yaml}"

echo "== 0) 전제 =="
for b in docker kind kubectl helm; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done
kind get clusters 2>/dev/null | grep -qx "$CLUSTER" || { echo "FAIL: kind-$CLUSTER 없음 → 00-up-clusters.sh"; exit 1; }
kubectl --context "$CTX" -n ingress-nginx get deploy ingress-nginx-controller >/dev/null 2>&1 \
  || { echo "FAIL: kind-cicd ingress-nginx 미설치 → 먼저 10-cicd-ingress.sh"; exit 1; }
[ -f "$VALUES" ] || { echo "FAIL: $VALUES 없음(standalone harbor-values 재사용)"; exit 1; }

echo "== 1) Harbor 설치(Helm, ingress, http://$HOSTNAME_HARBOR) =="
helm repo add harbor https://helm.goharbor.io >/dev/null 2>&1 || true
helm repo update >/dev/null
kubectl --context "$CTX" create namespace "$HARBOR_NS" >/dev/null 2>&1 || true
helm --kube-context "$CTX" upgrade --install harbor harbor/harbor -n "$HARBOR_NS" \
  -f "$VALUES" \
  --set externalURL="http://$HOSTNAME_HARBOR" \
  --wait --timeout 10m

echo "== 2) Harbor 컴포넌트 Ready + PVC Bound + Ingress 확인 =="
kubectl --context "$CTX" -n "$HARBOR_NS" get pods
kubectl --context "$CTX" -n "$HARBOR_NS" get pvc
kubectl --context "$CTX" -n "$HARBOR_NS" get ingress

echo "== 3) si-msa 프로젝트 생성(public — kind-svc 노드 pull 무인증 단순화) =="
kubectl --context "$CTX" -n "$HARBOR_NS" run harbor-init-$$ --rm -i --restart=Never \
  --image=curlimages/curl:latest --quiet -- \
  -s -u "$ADMIN:$PASS" -X POST "http://harbor-core.${HARBOR_NS}.svc.cluster.local/api/v2.0/projects" \
  -H "Content-Type: application/json" \
  -d "{\"project_name\":\"$PROJECT\",\"metadata\":{\"public\":\"true\"}}" \
  -w 'HTTP=%{http_code}\n' || echo "  (프로젝트 생성 응답 비정상 — 이미 존재 409 면 무해)"

echo "== 4) kind-svc 노드 Harbor 신뢰 재확인(03-cross-trust 산출이 유효한가) =="
# Harbor 가 방금 설치됐다 — cicd CP IP 는 클러스터 재생성 안 했으면 03 시점과 동일하므로 신뢰파일 그대로 유효.
# (클러스터를 재생성했다면 CP IP 변동 → 03-cross-trust.sh 재실행 후 이 스크립트로.)
CICD_CP_IP="$(docker inspect -f '{{(index .NetworkSettings.Networks "kind").IPAddress}}' cicd-control-plane 2>/dev/null || true)"
SVC_NODES="$(docker ps --format '{{.Names}}' | grep '^svc-' || true)"
if [ -n "$SVC_NODES" ] && [ -n "$CICD_CP_IP" ]; then
  for n in $SVC_NODES; do
    HOSTS_LINE="$(docker exec "$n" sh -c "grep '[[:space:]]$HOSTNAME_HARBOR\$' /etc/hosts" 2>/dev/null || true)"
    case "$HOSTS_LINE" in
      "$CICD_CP_IP "*) echo "  $n: /etc/hosts → $CICD_CP_IP $HOSTNAME_HARBOR (유효)";;
      "") echo "  ⚠️ $n: harbor.local /etc/hosts 없음 → 03-cross-trust.sh 재실행 필요";;
      *)  echo "  ⚠️ $n: harbor.local 이 $CICD_CP_IP 아님(클러스터 재생성?) → 03-cross-trust.sh 재실행 필요";;
    esac
  done
else
  echo "  (kind-svc 노드 또는 cicd CP IP 미산출 — 03-cross-trust 선행 확인)"
fi

echo
echo "──────────────────────────────────────────────────────────────"
echo "✅ Harbor(hub) 설치 완료 — http://$HOSTNAME_HARBOR  ($ADMIN/$PASS), 프로젝트 $PROJECT(public)."
echo "   호스트 push 사전조건: Docker Desktop > daemon insecure-registries 에 \"$HOSTNAME_HARBOR\""
echo "                       + Windows/WSL hosts 에 '127.0.0.1 $HOSTNAME_HARBOR'"
echo "   pull 경로(kind-svc): 노드 /etc/hosts → cicd CP IP:80 → ingress (03-cross-trust 배선)."
echo "   다음: bash 40-promote.sh  (호스트 docker 빌드 → push → overlay 핀 → commit/push → ArgoCD sync)"
