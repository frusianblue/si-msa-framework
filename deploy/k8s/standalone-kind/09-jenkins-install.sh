#!/usr/bin/env bash
# deploy/k8s/standalone-kind/09-jenkins-install.sh
# ─────────────────────────────────────────────────────────────────────────────
# 인-클러스터 Jenkins 설치(Helm) — 【B안 (2026-06-07 세션4) — ingress(jenkins.local)】 + agent RBAC + Kaniko push 자격.
# 전제: 10-ingress-nginx + 08-harbor-install 완료(Harbor ingress 가 떠 있고 si-msa 프로젝트 존재, 좌표 확정).
# 실행: bash deploy/k8s/standalone-kind/09-jenkins-install.sh
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"; cd "$HERE"

CLUSTER="${CLUSTER:-sanity}"; CTX="kind-${CLUSTER}"
JENKINS_NS="jenkins"; HOSTNAME_JENKINS="jenkins.local"
HARBOR_USER="admin"; HARBOR_PASS="${HARBOR_PASS:-Harbor12345}"

echo "== 0) 전제 =="
for b in docker kind kubectl helm; do command -v "$b" >/dev/null 2>&1 || { echo "FAIL: '$b' 없음"; exit 1; }; done
kubectl --context "$CTX" -n ingress-nginx get deploy ingress-nginx-controller >/dev/null 2>&1 \
  || { echo "FAIL: ingress-nginx 미설치 → 먼저 10-ingress-nginx.sh"; exit 1; }

echo "== 1) Jenkins 설치(Helm, ingress) =="
helm repo add jenkins https://charts.jenkins.io >/dev/null 2>&1 || true
helm repo update >/dev/null
kubectl --context "$CTX" create namespace "$JENKINS_NS" >/dev/null 2>&1 || true
helm --kube-context "$CTX" upgrade --install jenkins jenkins/jenkins -n "$JENKINS_NS" \
  -f jenkins-values.yaml --wait --timeout 10m

echo "== 1.5) si-msa 네임스페이스 보장(jenkins-rbac 의 RoleBinding/Role 대상) =="
# jenkins-rbac.yaml 의 RoleBinding/Role 3개는 si-msa ns 에 들어간다. 앱 오버레이(overlays/dev,
# base/namespace.yaml)가 정식 소유자지만, 아직 apply 전이면 ns 부재 → "namespaces \"si-msa\" not found".
# 여기서 멱등 생성해 RBAC 적용을 앱 배포 순서와 분리한다(나중 apply -k 가 라벨 등 흡수, no-op).
kubectl --context "$CTX" create namespace si-msa --dry-run=client -o yaml | kubectl --context "$CTX" apply -f -

echo "== 2) agent 배포 RBAC =="
kubectl --context "$CTX" apply -f jenkins-rbac.yaml

echo "== 3) Kaniko push 자격(harbor.local) — jenkins ns 에 dockerconfigjson 시크릿 =="
# Kaniko 는 harbor.local 로 push(CoreDNS→INGRESS_CLUSTERIP). HTTP 이므로 파이프라인에서 --insecure.
kubectl --context "$CTX" -n "$JENKINS_NS" delete secret harbor-push-cred --ignore-not-found
kubectl --context "$CTX" -n "$JENKINS_NS" create secret docker-registry harbor-push-cred \
  --docker-server="harbor.local" \
  --docker-username="$HARBOR_USER" --docker-password="$HARBOR_PASS"

echo "== 4) Ingress 생성 확인 + 상태 =="
kubectl --context "$CTX" -n "$JENKINS_NS" get ingress
kubectl --context "$CTX" -n "$JENKINS_NS" get pods

echo
echo "──────────────────────────────────────────────────────────────"
echo "✅ Jenkins 설치 완료 (ingress, http://$HOSTNAME_JENKINS)."
echo "   호스트 접속 전제: hosts 에 '127.0.0.1 harbor.local jenkins.local'"
echo "   UI:    http://$HOSTNAME_JENKINS    로그인: admin / admin123"
echo
echo "   다음(UI 1회 설정):"
echo "     1) New Item → Pipeline → 'si-msa-cd'"
echo "     2) Pipeline > Definition = 'Pipeline script from SCM'"
echo "        SCM=Git, Repo=https://github.com/frusianblue/si-msa-framework.git, Branch=*/master"
echo "        Script Path = deploy/cicd/Jenkinsfile.kind"
echo "     3) Save → Build Now (Kaniko build→push harbor.local→dev overlay 배포→rollout)"
echo "   첫 빌드에서 노드 pull 경로(certs.d→/etc/hosts→CP:80→ingress)와 :<sha> 불변태그 배포가 실증됩니다."
