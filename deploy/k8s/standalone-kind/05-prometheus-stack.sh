#!/usr/bin/env bash
# =====================================================================
# 05-prometheus-stack.sh  (S4-2)
#   standalone kind-sanity 에 kube-prometheus-stack(Helm) 설치 → Prometheus Operator/CRD →
#   base ServiceMonitor(si-msa-services)를 si-msa ns 에 직접 적용 → Prometheus 가 6앱(/actuator/prometheus)
#   을 스크랩하는지(타깃 UP) 스모크 → (옵션) Grafana 접속 안내.
#
#   전제(확인됨): 4서비스 모두 actuator 'prometheus' 노출 + Service 포트명 'http' + base SM(si-msa-services,
#                 release=kube-prometheus-stack 라벨). dev overlay 는 SM 을 $patch:delete(코어 apply operator 비의존).
#   ▶ SM 의 단일 소유자 = 이 스크립트(operator 설치 후 base SM 파일 직접 apply). dev overlay 재적용에 의존하지 않음.
# ---------------------------------------------------------------------
# 사용법:
#   bash deploy/k8s/standalone-kind/05-prometheus-stack.sh           # 설치 + SM 적용 + 스크랩 스모크
#   bash …/05-prometheus-stack.sh --grafana                         # + Grafana port-forward 안내(접속정보 출력)
#   CTX=kind-sanity NS=si-msa bash …/05-prometheus-stack.sh
# 의존: helm, kubectl, curl, python3
# =====================================================================
set -euo pipefail

CTX="${CTX:-kind-sanity}"
NS="${NS:-si-msa}"
MON_NS="${MON_NS:-monitoring}"
RELEASE="${RELEASE:-kube-prometheus-stack}"     # base SM 의 release 라벨과 일치해야 함
OVERLAY="${OVERLAY:-deploy/k8s/overlays/dev}"
DO_GRAFANA=0; for a in "$@"; do [ "$a" = "--grafana" ] && DO_GRAFANA=1; done
K(){ kubectl --context "$CTX" "$@"; }
PF_PID=""

note(){ printf '\033[36m== %s\033[0m\n' "$*"; }
ok(){   printf '\033[32m  ✅ %s\033[0m\n' "$*"; }
warn(){ printf '\033[33m  ⚠ %s\033[0m\n' "$*"; }
fail(){ printf '\033[31m  ❌ %s\033[0m\n' "$*"; exit 1; }
cleanup(){ [ -n "$PF_PID" ] && kill "$PF_PID" 2>/dev/null || true; }
trap cleanup EXIT

# 0) 사전 점검 --------------------------------------------------------------
note "0) 사전 점검 (ctx=${CTX}, ns=${NS}, release=${RELEASE})"
command -v helm >/dev/null 2>&1 || fail "helm 미설치 — https://helm.sh/docs/intro/install (또는 K8S_ADDONS.md)"
K cluster-info >/dev/null 2>&1 || fail "컨텍스트 '${CTX}' 도달 불가"
K -n "$NS" get deploy -l app.kubernetes.io/component=service >/dev/null 2>&1 || fail "${NS} 앱 Deployment 없음 — 03-dev-overlay-up.sh 먼저"
if grep -q 'patch: delete' "${OVERLAY}/kustomization.yaml" 2>/dev/null && grep -q 'ServiceMonitor' "${OVERLAY}/kustomization.yaml"; then
  ok "dev overlay 에 SM \$patch:delete 존재(정상) — 코어 apply 는 operator 불요, SM 은 본 스크립트가 직접 건다"
else
  warn "dev overlay 에 SM \$patch:delete 가 없음 — 코어 apply 가 operator CRD 를 요구할 수 있다(현 설계는 delete 전제)"
fi

# 1) Helm 리포 + 설치 -------------------------------------------------------
note "1) kube-prometheus-stack 설치/업그레이드 (ns=${MON_NS})"
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts >/dev/null 2>&1 || true
helm repo update prometheus-community >/dev/null 2>&1 || helm repo update >/dev/null 2>&1
# kind 친화: alertmanager off·짧은 보존·SM 셀렉터 전체수용(라벨/네임스페이스 가정에 안 묶이게)·Grafana 유지.
helm upgrade --install "$RELEASE" prometheus-community/kube-prometheus-stack \
  --kube-context "$CTX" -n "$MON_NS" --create-namespace \
  --set alertmanager.enabled=false \
  --set prometheus.prometheusSpec.retention=2h \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
  --set prometheus.prometheusSpec.serviceMonitorNamespaceSelectorNilUsesHelmValues=false \
  --set grafana.enabled=true \
  --wait --timeout 10m
ok "helm 설치 완료"

# 2) Operator CRD 확립 ------------------------------------------------------
note "2) Prometheus Operator CRD 확인"
K wait --for=condition=established --timeout=60s crd/servicemonitors.monitoring.coreos.com >/dev/null
ok "servicemonitors.monitoring.coreos.com 확립"

# 3) base ServiceMonitor 직접 적용(operator CRD 확립 후) -------------------
note "3) base ServiceMonitor(si-msa-services) 직접 적용 → ns=${NS}"
# dev overlay 는 SM 을 $patch:delete 하므로(코어 apply 의 operator 비의존 보장), SM 의 단일 소유자는 이 스크립트다.
#   base SM 파일을 si-msa ns 에 직접 apply(operator serviceMonitorSelectorNilUsesHelmValues=false 라 라벨 무관 선택).
K -n "$NS" apply -f deploy/k8s/base/common/servicemonitor.yaml >/dev/null
K -n "$NS" get servicemonitor si-msa-services >/dev/null 2>&1 || fail "si-msa-services SM 미적용 — base/common/servicemonitor.yaml 확인"
ok "ServiceMonitor si-msa-services 적용됨"

# 4) 스크랩 스모크: Prometheus 타깃 UP ------------------------------------
note "4) Prometheus 타깃 UP 확인 (port-forward 9090, 최대 ~2분)"
K -n "$MON_NS" port-forward "svc/${RELEASE}-prometheus" 9090:9090 >/dev/null 2>&1 &
PF_PID=$!
for i in $(seq 1 20); do curl -fsS http://localhost:9090/-/ready >/dev/null 2>&1 && break; sleep 3; [ "$i" = 20 ] && fail "Prometheus 미응답"; done
UP=0
for i in $(seq 1 24); do
  UP="$(curl -fsS 'http://localhost:9090/api/v1/targets?state=active' 2>/dev/null | python3 -c '
import sys,json
d=json.load(sys.stdin)
t=[x for x in d["data"]["activeTargets"]
   if x["labels"].get("namespace")=="'"$NS"'" and "si-msa-services" in x.get("scrapePool","")]
up=[x for x in t if x["health"]=="up"]
print(len(up))' 2>/dev/null || echo 0)"
  [ "${UP:-0}" -ge 1 ] && break
  sleep 5
done
[ "${UP:-0}" -ge 1 ] || fail "si-msa 타깃이 UP 안 됨 — /actuator/prometheus 노출·SM selector·NetworkPolicy(이 트랙=비집행) 확인. 디버그: curl localhost:9090/api/v1/targets"
ok "si-msa-services 타깃 UP=${UP} (dev=서비스당 1레플리카면 보통 4; 1+ 면 스크랩 정상)"

# 5) (옵션) Grafana 접속 안내 -----------------------------------------------
if [ "$DO_GRAFANA" = 1 ]; then
  note "5) Grafana 접속 정보"
  GP="$(K -n "$MON_NS" get secret "${RELEASE}-grafana" -o jsonpath='{.data.admin-password}' 2>/dev/null | base64 -d || true)"
  echo "    port-forward:  kubectl -n ${MON_NS} port-forward svc/${RELEASE}-grafana 3000:80"
  echo "    URL: http://localhost:3000   user: admin   pass: ${GP:-<secret ${RELEASE}-grafana / admin-password>}"
  echo "    대시보드: 'Kubernetes / Compute Resources' · Prometheus 데이터소스 자동연결. JVM 은 micrometer 대시보드(id 4701) import."
fi

printf '\033[32m\n🟢 S4-2 완료 — kube-prometheus-stack 설치 · ServiceMonitor 활성 · si-msa 타깃 UP(%s)\033[0m\n' "${UP}"
echo "   다음 = S5 prod-rehearsal(실부하 HPA 스케일 관찰 포함) → S6 상위흐름 실클러스터 → S7 Jenkins."
