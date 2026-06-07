#!/usr/bin/env bash
# deploy/k8s/standalone-kind/09-jenkins-install.sh
# ─────────────────────────────────────────────────────────────────────────────
# 인-클러스터 Jenkins 설치(Helm, NodePort 32000, 영속 PVC) + agent RBAC + Kaniko push 자격.
# 전제: 08-harbor-install.sh 완료(Harbor 가 떠 있고 si-msa 프로젝트 존재, CP_IP 좌표 확정).
# 실행: bash deploy/k8s/standalone-kind/09-jenkins-install.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

CLUSTER="${CLUSTER:-sanity}"; CTX="kind-${CLUSTER}"
CP_NODE="${CP_NODE:-${CLUSTER}-control-plane}"
JENKINS_NS="jenkins"; NODEPORT="${NODEPORT:-32000}"
HARBOR_PORT="${HARBOR_PORT:-30002}"; HARBOR_USER="admin"; HARBOR_PASS="${HARBOR_PASS:-Harbor12345}"

echo "== 0) 전제 =="
for b in docker kind kubectl helm; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done

echo "== 1) CP_IP(=Harbor externalURL 좌표) 산출 =="
CP_IP="$(docker inspect -f '{{(index .NetworkSettings.Networks "kind").IPAddress}}' "$CP_NODE")"
echo "  CP_IP=$CP_IP (Kaniko 가 harbor.local→CoreDNS→$CP_IP:$HARBOR_PORT 로 push)"

echo "== 2) Jenkins 설치(Helm) =="
helm repo add jenkins https://charts.jenkins.io >/dev/null 2>&1 || true
helm repo update >/dev/null
kubectl --context "$CTX" create namespace "$JENKINS_NS" >/dev/null 2>&1 || true
helm --kube-context "$CTX" upgrade --install jenkins jenkins/jenkins -n "$JENKINS_NS" \
  -f jenkins-values.yaml --wait --timeout 10m

echo "== 3) agent 배포 RBAC =="
kubectl --context "$CTX" apply -f jenkins-rbac.yaml

echo "== 4) Kaniko push 자격(harbor.local) — jenkins ns 에 dockerconfigjson 시크릿 =="
# Kaniko 는 harbor.local 로 push(CoreDNS→CP_IP). HTTP 이므로 파이프라인에서 --insecure.
kubectl --context "$CTX" -n "$JENKINS_NS" delete secret harbor-push-cred --ignore-not-found
kubectl --context "$CTX" -n "$JENKINS_NS" create secret docker-registry harbor-push-cred \
  --docker-server="harbor.local" \
  --docker-username="$HARBOR_USER" --docker-password="$HARBOR_PASS"

echo "== 5) 상태 =="
kubectl --context "$CTX" -n "$JENKINS_NS" get pods
ADMIN_PW="$(kubectl --context "$CTX" -n "$JENKINS_NS" get secret jenkins -o jsonpath='{.data.jenkins-admin-password}' 2>/dev/null | base64 -d 2>/dev/null || echo 'admin123')"

echo
echo "──────────────────────────────────────────────────────────────"
echo "✅ Jenkins 설치 완료."
echo "   UI(호스트):  kubectl -n jenkins port-forward svc/jenkins 8088:8080  → http://localhost:8088"
echo "   로그인:       admin / ${ADMIN_PW}"
echo
echo "   다음(UI 1회 설정):"
echo "     1) New Item → Pipeline → 'si-msa-cd'"
echo "     2) Pipeline > Definition = 'Pipeline script from SCM'"
echo "        SCM=Git, Repo=https://github.com/frusianblue/si-msa-framework.git, Branch=*/master"
echo "        Script Path = deploy/cicd/Jenkinsfile.kind"
echo "     3) Save → Build Now (Kaniko build→push harbor.local→dev overlay 배포→rollout)"
echo "   첫 빌드에서 노드 pull 경로(certs.d→$CP_IP:$HARBOR_PORT)와 :<sha> 불변태그 배포가 실증됩니다."
