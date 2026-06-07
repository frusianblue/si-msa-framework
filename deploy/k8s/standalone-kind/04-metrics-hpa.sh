#!/usr/bin/env bash
# =====================================================================
# 04-metrics-hpa.sh  (S4-1)
#   standalone kind-sanity 에 metrics-server 설치(kind 용 --kubelet-insecure-tls)
#   → `kubectl top` 동작 확인(메트릭 스모크) → 일회용 HPA 로 metrics→HPA 파이프라인 확인.
#
#   왜 일회용 HPA: running 클러스터는 dev overlay 라 HPA 가 없다(HPA 는 overlays/prod 전용).
#   여기선 "HPA 가 메트릭을 읽어 TARGETS 가 <unknown> 이 아닌가"까지만 본다(스케일 관찰은 --load 옵션).
# ---------------------------------------------------------------------
# 사용법:
#   bash deploy/k8s/standalone-kind/04-metrics-hpa.sh            # 설치 + top + HPA 스모크
#   SVC=auth-server bash …/04-metrics-hpa.sh                     # HPA 대상 서비스 지정(기본 gateway)
#   bash …/04-metrics-hpa.sh --load                             # + 부하 생성 후 스케일 관찰(옵션)
#   bash …/04-metrics-hpa.sh --keep                             # 스모크 HPA 정리 안 함
#   CTX=kind-sanity NS=si-msa bash …/04-metrics-hpa.sh
# 의존: kubectl, curl  (부하 생성 --load 는 추가 의존 없음 = 클러스터 내 busybox)
# =====================================================================
set -euo pipefail

CTX="${CTX:-kind-sanity}"
NS="${NS:-si-msa}"
SVC="${SVC:-gateway}"                 # HPA 스모크 대상 Deployment
MS_NS="kube-system"
MS_URL="${MS_URL:-https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml}"
HPA="hpa-smoke-${SVC}"
DO_LOAD=0; KEEP=0
for a in "$@"; do case "$a" in --load) DO_LOAD=1;; --keep) KEEP=1;; esac; done
K() { kubectl --context "$CTX" "$@"; }

note(){ printf '\033[36m== %s\033[0m\n' "$*"; }
ok(){   printf '\033[32m  ✅ %s\033[0m\n' "$*"; }
warn(){ printf '\033[33m  ⚠ %s\033[0m\n' "$*"; }
fail(){ printf '\033[31m  ❌ %s\033[0m\n' "$*"; exit 1; }

cleanup(){ [ "$KEEP" = 0 ] && K -n "$NS" delete hpa "$HPA" --ignore-not-found >/dev/null 2>&1 || true; }
trap cleanup EXIT

# 컨텍스트/대상 사전 점검 ------------------------------------------------------
note "0) 컨텍스트/대상 점검 (ctx=${CTX}, ns=${NS}, svc=${SVC})"
K cluster-info >/dev/null 2>&1 || fail "컨텍스트 '${CTX}' 도달 불가 — standalone kind-sanity 가 떠 있는지/CTX 확인"
K -n "$NS" get deploy "$SVC" >/dev/null 2>&1 || fail "deploy/${SVC} 없음 — 03-dev-overlay-up.sh 로 6파드 띄운 뒤 실행"
ok "클러스터/대상 Deployment 확인"

# 1) metrics-server 설치 + kind insecure-tls ---------------------------------
note "1) metrics-server 설치/보정 (kind: --kubelet-insecure-tls)"
if ! K -n "$MS_NS" get deploy metrics-server >/dev/null 2>&1; then
  K apply -f "$MS_URL"
  ok "metrics-server 매니페스트 apply"
else
  ok "metrics-server 이미 존재"
fi
# kind 의 kubelet 서빙 인증서는 self-signed → --kubelet-insecure-tls 필요. 멱등(이미 있으면 skip).
ARGS="$(K -n "$MS_NS" get deploy metrics-server -o jsonpath='{.spec.template.spec.containers[0].args}')"
if printf '%s' "$ARGS" | grep -q -- '--kubelet-insecure-tls'; then
  ok "--kubelet-insecure-tls 이미 설정됨"
else
  K -n "$MS_NS" patch deploy metrics-server --type=json \
    -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
  ok "--kubelet-insecure-tls 추가"
fi
K -n "$MS_NS" rollout status deploy/metrics-server --timeout=120s >/dev/null
ok "metrics-server Ready"

# 2) 메트릭 스모크: kubectl top (메트릭 채워질 때까지 폴링) ---------------------
note "2) 메트릭 수집 확인 (kubectl top — 최대 ~90s 대기)"
TOP_OK=0
for i in $(seq 1 30); do
  if K top nodes >/dev/null 2>&1 && K -n "$NS" top pods >/dev/null 2>&1; then TOP_OK=1; break; fi
  sleep 3
done
[ "$TOP_OK" = 1 ] || fail "kubectl top 미응답 — metrics-server 로그 확인(K -n kube-system logs deploy/metrics-server). kind 면 insecure-tls 재확인"
ok "top nodes / top pods 응답"
K top nodes | sed 's/^/    /'
K -n "$NS" top pods -l app.kubernetes.io/component=service 2>/dev/null | sed 's/^/    /' || true

# 3) HPA 스모크: 일회용 HPA 의 TARGETS 가 <unknown> 이 아닌지 ------------------
note "3) HPA 파이프라인 확인 (일회용 ${HPA}, cpu 70%)"
K -n "$NS" apply -f - >/dev/null <<YAML
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: ${HPA}
  labels: { siframework.io/smoke: "true" }
spec:
  scaleTargetRef: { apiVersion: apps/v1, kind: Deployment, name: ${SVC} }
  minReplicas: 1
  maxReplicas: 3
  metrics:
    - type: Resource
      resource: { name: cpu, target: { type: Utilization, averageUtilization: 70 } }
YAML
ok "스모크 HPA 적용"
# HPA 가 metrics-server 에서 현재 CPU% 를 읽어올 때까지 대기(처음 한동안 <unknown>).
HPA_OK=0
for i in $(seq 1 30); do
  TARGETS="$(K -n "$NS" get hpa "$HPA" -o jsonpath='{.status.currentMetrics[0].resource.current.averageUtilization}' 2>/dev/null || true)"
  if [ -n "$TARGETS" ]; then HPA_OK=1; break; fi
  sleep 4
done
K -n "$NS" get hpa "$HPA" | sed 's/^/    /'
[ "$HPA_OK" = 1 ] || fail "HPA TARGETS 가 계속 <unknown> — metrics→HPA 연결 실패(requests.cpu 미설정?·metrics-server 미수집?)"
ok "HPA 가 CPU 메트릭 수신(현재 ${TARGETS}% / 70%) — metrics→HPA 파이프라인 정상"

# 4) (옵션) 부하 → 스케일 관찰 ------------------------------------------------
if [ "$DO_LOAD" = 1 ]; then
  note "4) (옵션) 부하 생성 → 스케일업 관찰 (~2분)"
  SVC_HOST="${SVC}.${NS}.svc.cluster.local"
  K -n "$NS" run "${HPA}-load" --image=busybox:1.36 --restart=Never --labels="siframework.io/smoke=true" \
    --command -- /bin/sh -c "while true; do wget -q -O- http://${SVC_HOST}:8080/actuator/health >/dev/null 2>&1 || true; done" >/dev/null 2>&1 || warn "부하 파드 생성 실패(무시 가능)"
  for i in $(seq 1 24); do
    R="$(K -n "$NS" get hpa "$HPA" -o jsonpath='{.status.currentReplicas}' 2>/dev/null || echo '?')"
    U="$(K -n "$NS" get hpa "$HPA" -o jsonpath='{.status.currentMetrics[0].resource.current.averageUtilization}' 2>/dev/null || echo '?')"
    printf '    t=%ds  replicas=%s  cpu=%s%%\n' "$((i*5))" "$R" "$U"; sleep 5
  done
  K -n "$NS" delete pod "${HPA}-load" --ignore-not-found >/dev/null 2>&1 || true
  warn "HTTP health 부하는 CPU 를 많이 안 올릴 수 있음 — 스케일 안 떠도 파이프라인(3단계 ✅)이면 S4-1 충족. 실부하 스케일은 S5 에서."
fi

printf '\033[32m\n🟢 S4-1 완료 — metrics-server(kind insecure-tls)=Ready · kubectl top OK · HPA metrics 수신 OK\033[0m\n'
[ "$KEEP" = 1 ] && printf '   (스모크 HPA %s 유지됨 — 정리: kubectl -n %s delete hpa %s)\n' "$HPA" "$NS" "$HPA" || printf '   (스모크 HPA 정리됨)\n'
echo "   다음 = kube-prometheus-stack 설치 → dev overlay ServiceMonitor \$patch:delete 해제(S4-2)."
