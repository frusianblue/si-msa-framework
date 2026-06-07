#!/usr/bin/env bash
# deploy/k8s/prod-kind/35-seed-secrets.sh
# ─────────────────────────────────────────────────────────────────────────────
# 4단계 보조: prod overlay 가 참조하는 Secret 4개를 kind-svc(si-msa ns)에 **리허설용**으로 주입.
#
#   ★ 왜 필요한가: prod overlay 는 시크릿을 git 에 넣지 않는다(설계 — ESO/SealedSecrets/운영자 사전주입).
#     base Deployment 의 envFrom.secretRef(gateway/auth-server/user-service/admin-service-secret)가
#     참조하는 Secret 이 ns 에 없으면 파드가 **CreateContainerConfigError**(이미지 pull 이전, config 단계 실패).
#     → promote(40) 로 이미지가 green sha 로 바뀌어도 시크릿이 없으면 파드가 못 뜬다.
#
#   ⚠️ 이건 **리허설 전용 고정 시드**다. 실 prod 는 절대 이렇게 안 한다:
#       - External Secrets Operator(ExternalSecret → Vault/AWS Secrets Manager) 또는
#       - Sealed Secrets(kubeseal 로 암호화한 SealedSecret 만 커밋).
#     DB 자격증명은 `initdb-prod.sql` 의 siuser/siuser_pw 와 **일치**해야 DB 연결이 된다.
#
#   ArgoCD 와의 관계: 이 Secret 들은 overlays/prod 가 렌더하지 않으므로 Application 관리 대상이 아님
#     → selfHeal/prune 무관(수동 Secret 안전). 파드는 Secret 생성 후 재기동으로 즉시 복구.
#
# 전제: 3단계 PASS(ArgoCD 가 si-msa ns + Deployment reconcile). ns 는 CreateNamespace=true 로 이미 존재.
# 실행: bash deploy/k8s/prod-kind/35-seed-secrets.sh
# 멱등: create --dry-run=client | apply (재실행 시 값 갱신).
# 위치: 30(Harbor) 다음, 40(promote) 전·후 무관(클러스터 사전조건). green 안 보이면 이거부터.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail
SVC_CTX="${SVC_CTX:-kind-svc}"; NS="${NS:-si-msa}"

# ── 리허설 고정값(운영 금지) ──
DB_USER="${DB_USER:-siuser}"
DB_PASSWORD="${DB_PASSWORD:-siuser_pw}"                                   # initdb-prod.sql 일치
JWT="${JWT:-rehearsal-hmac-jwt-secret-do-not-use-in-prod-0123456789abcdef}"  # HMAC 32바이트+ 넉넉히
AES="${AES:-0123456789abcdef0123456789abcdef}"                           # 정확히 32바이트(AES-256 raw)

echo "== 0) 전제 =="
command -v kubectl >/dev/null 2>&1 || { echo "FAIL: kubectl 없음"; exit 1; }
kubectl --context "$SVC_CTX" get ns "$NS" >/dev/null 2>&1 \
  || { echo "FAIL: kind-svc 에 ns $NS 없음 — 3단계(GitOps reconcile) 선행"; exit 1; }

seed() {
  local name="$1"; shift
  kubectl --context "$SVC_CTX" -n "$NS" create secret generic "$name" "$@" \
    --dry-run=client -o yaml | kubectl --context "$SVC_CTX" apply -f - >/dev/null
  echo "  seeded: $name"
}

echo "== 1) 시크릿 4개 주입(리허설 고정값) =="
seed gateway-secret \
  --from-literal=JWT_SECRET="$JWT"
seed auth-server-secret \
  --from-literal=DB_USER="$DB_USER" \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=FRAMEWORK_JWT_SECRET="$JWT" \
  --from-literal=AES_SECRET="$AES"
seed user-service-secret \
  --from-literal=DB_USER="$DB_USER" \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=JWT_SECRET="$JWT" \
  --from-literal=AES_SECRET="$AES"
seed admin-service-secret \
  --from-literal=DB_USER="$DB_USER" \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --from-literal=JWT_SECRET="$JWT" \
  --from-literal=AES_SECRET="$AES"

echo "== 2) CreateContainerConfigError 파드 즉시 복구(파드 재생성 — Secret watch 대기 단축) =="
# 파드만 삭제(RS 는 유지) → RS 가 새 파드 생성, 이번엔 Secret 이 있어 config 통과.
# (ArgoCD selfHeal 은 RS/Deployment 를 보지 파드 삭제와 무관 — 충돌 없음.)
kubectl --context "$SVC_CTX" -n "$NS" delete pod --all --wait=false >/dev/null 2>&1 || true
echo "  파드 재생성 트리거 — Secret 적재된 새 파드가 뜬다."

echo
echo "──────────────────────────────────────────────────────────────"
echo "✅ 리허설 시크릿 4개 주입 완료(si-msa ns)."
echo "   ⚠️ 리허설 전용 고정값 — 운영은 ESO/SealedSecrets. DB=siuser/siuser_pw(initdb 일치)."
echo "   파드 pull(:<sha>)+startup+DB 연결에 시간 필요. 충분히 대기 후:"
echo "   다음: bash 41-verify-promote.sh  (G13 파드 green 확인)"
