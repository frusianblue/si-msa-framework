#!/usr/bin/env bash
# =====================================================================
# 06-grafana-jvm-dashboard.sh  (S5 — 관측 마감: JVM 대시보드 자동적재 + 4/4 타깃 정밀검증)
#   ① deploy/k8s/observability/grafana-dashboard-jvm.yaml(ConfigMap) 적용
#      → kube-prometheus-stack Grafana sidecar(label grafana_dashboard:"1", searchNamespace:ALL)가 자동 import.
#   ② Prometheus 타깃 정밀 검증: si-msa-services 의 4서비스(gateway/auth-server/user-service/admin-service)가
#      "전부" UP 인지 service 라벨 단위로 확인(05 는 UP>=1 에서 멈춤 — 여기선 4/4 를 요구).
#   ③ (옵션) Grafana 접속정보 출력.
#
#   전제: 05-prometheus-stack.sh 가 끝나(Operator/Grafana 설치 + SM si-msa-services 활성) 6파드 Running.
# ---------------------------------------------------------------------
# 사용법:
#   bash deploy/k8s/standalone-kind/06-grafana-jvm-dashboard.sh            # 대시보드 적용 + 4/4 타깃 검증
#   bash …/06-grafana-jvm-dashboard.sh --grafana                          # + Grafana port-forward 접속정보
#   CTX=kind-sanity NS=si-msa MON_NS=monitoring RELEASE=kube-prometheus-stack bash …/06-...sh
# 의존: kubectl, curl, python3
# =====================================================================
set -euo pipefail

CTX="${CTX:-kind-sanity}"
NS="${NS:-si-msa}"
MON_NS="${MON_NS:-monitoring}"
RELEASE="${RELEASE:-kube-prometheus-stack}"
DASH="${DASH:-deploy/k8s/observability/grafana-dashboard-jvm.yaml}"
EXPECTED="${EXPECTED:-gateway auth-server user-service admin-service}"   # 4서비스 service 라벨
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
note "0) 사전 점검 (ctx=${CTX}, ns=${NS}, mon=${MON_NS}, release=${RELEASE})"
K cluster-info >/dev/null 2>&1 || fail "컨텍스트 '${CTX}' 도달 불가"
[ -f "$DASH" ] || fail "대시보드 매니페스트 없음: ${DASH} (repo 루트에서 실행하세요)"
K -n "$MON_NS" get deploy "${RELEASE}-grafana" >/dev/null 2>&1 \
  || fail "${MON_NS}/${RELEASE}-grafana 없음 — 05-prometheus-stack.sh 먼저"
K -n "$NS" get servicemonitor si-msa-services >/dev/null 2>&1 \
  || fail "si-msa-services ServiceMonitor 미적용 — 05-prometheus-stack.sh 먼저"
ok "Grafana/Operator/ServiceMonitor 존재 확인"

# 1) JVM 대시보드 ConfigMap 적용 -------------------------------------------
note "1) JVM 대시보드 ConfigMap 적용 (sidecar 자동 import)"
K apply -f "$DASH"
ok "grafana-dashboard-jvm 적용됨 — sidecar 가 ~30~60s 내 import (수동 import 불필요)"

# 2) Prometheus 4/4 타깃 정밀 검증 -----------------------------------------
note "2) Prometheus 타깃 4/4 UP 검증 (port-forward 9090, 최대 ~2분)"
K -n "$MON_NS" port-forward "svc/${RELEASE}-prometheus" 9090:9090 >/dev/null 2>&1 &
PF_PID=$!
for i in $(seq 1 20); do curl -fsS http://localhost:9090/-/ready >/dev/null 2>&1 && break; sleep 3; [ "$i" = 20 ] && fail "Prometheus 미응답"; done

UP_LIST=""
for i in $(seq 1 24); do
  UP_LIST="$(curl -fsS 'http://localhost:9090/api/v1/targets?state=active' 2>/dev/null | python3 -c '
import sys, json
ns="'"$NS"'"
try:
    d=json.load(sys.stdin)
except Exception:
    sys.exit(0)
up=set()
for t in d.get("data",{}).get("activeTargets",[]):
    lb=t.get("labels",{})
    if lb.get("namespace")!=ns: continue
    if "si-msa-services" not in t.get("scrapePool",""): continue
    if t.get("health")=="up":
        svc=lb.get("service") or lb.get("job") or ""
        if svc: up.add(svc)
print(" ".join(sorted(up)))
' || true)"
  # 기대 4서비스 중 UP 인 것만 교집합으로 집계(잉여 타깃 무시 — SM 은 component=service 만 잡으므로 보통 4개뿐)
  FOUND=""; MISSING=""
  for e in $EXPECTED; do
    if printf ' %s ' "$UP_LIST" | grep -q " $e "; then FOUND="$FOUND $e"; else MISSING="$MISSING $e"; fi
  done
  N="$(printf '%s' "$FOUND" | wc -w | tr -d ' ')"
  printf '    t=%ds  UP=%s/4 [%s ]%s\n' "$((i*5))" "${N:-0}" "${FOUND}" \
    "$( [ -n "$MISSING" ] && printf '  missing:%s' "$MISSING" || true )"
  [ -z "$MISSING" ] && break
  sleep 5
done

MISSING=""
for e in $EXPECTED; do printf ' %s ' "$UP_LIST" | grep -q " $e " || MISSING="$MISSING $e"; done
[ -z "$MISSING" ] || fail "4/4 미달 — 누락:${MISSING}
   점검: 누락 서비스의 /actuator/prometheus 노출(management.endpoints.web.exposure)·Service 포트명 'http'·파드 Ready.
   디버그: curl -s localhost:9090/api/v1/targets | python3 -m json.tool | grep -A3 -i '${MISSING# }'"
ok "si-msa 4서비스 전부 UP: ${UP_LIST}"

# 3) (옵션) Grafana 접속정보 ------------------------------------------------
if [ "$DO_GRAFANA" = 1 ]; then
  note "3) Grafana 접속 정보"
  GP="$(K -n "$MON_NS" get secret "${RELEASE}-grafana" -o jsonpath='{.data.admin-password}' 2>/dev/null | base64 -d || true)"
  echo "    port-forward:  kubectl -n ${MON_NS} port-forward svc/${RELEASE}-grafana 3000:80"
  echo "    URL: http://localhost:3000   user: admin   pass: ${GP:-<secret ${RELEASE}-grafana / admin-password>}"
  echo "    대시보드: 'si-msa' 폴더 > 'si-msa · JVM / Micrometer' (uid=si-msa-jvm). 상단 Service 드롭다운으로 서비스 선택."
fi

printf '\033[32m\n🟢 S5 관측 마감 — JVM 대시보드 자동적재 + Prometheus 4/4 타깃 UP 실측\033[0m\n'
echo "   다음 = (연기됨) 실부하 HPA 스케일업은 최종 수용시험. → S6 상위흐름 실클러스터(OIDC RP·이중발급기) → S7 Jenkins."
