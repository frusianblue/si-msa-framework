#!/usr/bin/env bash
# deploy/k8s/prod-kind/42-diagnose-admin-file.sh
# admin-service CrashLoop(file storage base-path) 진단 + 적용 후 검증.
#   원인 가설: framework-file 기본 local 저장소가 read-only 루트FS 의 base-path(./uploads=/application/uploads)에
#             createDirectories 하다 실패 → localFileStorage 빈 생성 실패 → fileService 미충족 → 기동 불가.
#             매니페스트가 주는 FRAMEWORK_FILE_STORAGE_BASE_PATH 가 relaxed binding 으로 base-path 에
#             안정적으로 바인딩되지 않으면 기본값(./uploads)이 그대로 쓰여 같은 증상이 재현된다.
#   수정: admin application.yml 에 명시 placeholder(${FRAMEWORK_FILE_STORAGE_BASE_PATH:./uploads})를 박아
#         매니페스트 env 명과 정확히 일치 → /tmp/uploads(hardening tmp emptyDir)로 확정 바인딩.
#
#   사용법:
#     1) 수정 적용 전 현재 상태 진단:        bash 42-diagnose-admin-file.sh
#     2) 코드 수정 → 이미지 재빌드/promote → ArgoCD sync 후 다시 실행하여 PASS 확인.
#   전제: kubectl context=kind-svc, namespace=si-msa.
set -u

CTX="${CTX:-kind-svc}"
NS="${NS:-si-msa}"
APP="admin-service"
K="kubectl --context ${CTX} -n ${NS}"

pass() { echo "  [PASS] $*"; }
fail() { echo "  [FAIL] $*"; }
note() { echo "  [NOTE] $*"; }

echo "======== 1) admin 파드 / ReplicaSet 이미지 ========"
${K} get pods -l app.kubernetes.io/name=${APP} -o wide 2>/dev/null
echo "--- ReplicaSet 별 이미지(여러 RS 공존 = rollout 미완) ---"
${K} get rs -l app.kubernetes.io/name=${APP} \
  -o custom-columns='RS:.metadata.name,DESIRED:.spec.replicas,READY:.status.readyReplicas,IMAGE:.spec.template.spec.containers[0].image' 2>/dev/null

echo ""
echo "======== 2) 실행 파드의 file 관련 env(실제 주입값) ========"
POD="$(${K} get pods -l app.kubernetes.io/name=${APP} -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)"
if [ -z "${POD}" ]; then
  fail "admin 파드를 찾지 못함"
else
  echo "  대상 파드: ${POD}"
  # env 는 컨테이너 spec 기준(런타임 주입 전 선언값). 매니페스트가 실제 무엇을 주는지 확인.
  ENVDUMP="$(${K} get pod "${POD}" -o jsonpath='{range .spec.containers[0].env[*]}{.name}={.value}{"\n"}{end}' 2>/dev/null)"
  echo "${ENVDUMP}" | grep -E 'FRAMEWORK_FILE|FILE_STORAGE|LOG_DIR' || note "file 관련 env 가 파드 spec 에 없음(= hardening 패치 미반영 가능)"
  if echo "${ENVDUMP}" | grep -q '^FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads$'; then
    pass "FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads 주입됨"
  else
    fail "FRAMEWORK_FILE_STORAGE_BASE_PATH 가 /tmp/uploads 로 주입되지 않음(hardening 패치/ArgoCD revision 확인)"
  fi
fi

echo ""
echo "======== 3) CrashLoop 직전 로그에서 base-path 경로 추출 ========"
# 핵심 판별: 로그 경로가 /application/uploads 면 env 미바인딩(기본값 사용), /tmp/uploads 면 바인딩됨.
for p in $(${K} get pods -l app.kubernetes.io/name=${APP} -o jsonpath='{.items[*].metadata.name}' 2>/dev/null); do
  echo "--- ${p} (previous, 최근 60줄에서 uploads/FileStorage 매칭) ---"
  ${K} logs "${p}" --previous --tail=200 2>/dev/null | grep -iE 'uploads|FileSystemFileStorage|FileStorage|Read-only|기본경로' | tail -10 \
    || ${K} logs "${p}" --tail=200 2>/dev/null | grep -iE 'uploads|FileSystemFileStorage|FileStorage|Read-only|기본경로' | tail -10 \
    || note "${p}: 매칭 로그 없음(이미 정상 기동했거나 ImagePullBackOff 로 로그 미생성)"
done

echo ""
echo "======== 4) 종합 판정 ========"
RUNNING="$(${K} get pods -l app.kubernetes.io/name=${APP} \
  -o jsonpath='{range .items[*]}{.status.phase}{":"}{.status.containerStatuses[0].ready}{"\n"}{end}' 2>/dev/null \
  | grep -c '^Running:true$')"
TOTAL="$(${K} get pods -l app.kubernetes.io/name=${APP} --no-headers 2>/dev/null | wc -l)"
echo "  Running&Ready / 전체 = ${RUNNING} / ${TOTAL}"
if [ "${RUNNING}" -gt 0 ] 2>/dev/null; then
  pass "admin-service 기동 파드 존재 — file storage 경로 문제 해소로 판단"
else
  fail "admin-service Ready 파드 0 — 위 2)/3) 의 env·로그 경로로 원인 확정 후 재적용"
  note "경로가 /application/uploads 면: 코드 수정(명시 placeholder) 미반영 또는 이미지 미재빌드."
  note "경로가 /tmp/uploads 인데도 실패면: /tmp 마운트(emptyDir) 누락 또는 다른 startup 원인(admindb Flyway/redis TokenStore) 점검."
fi
echo "========================================================"
