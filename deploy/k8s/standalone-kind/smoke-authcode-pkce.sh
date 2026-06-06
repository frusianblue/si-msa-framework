#!/usr/bin/env bash
# =====================================================================
# smoke-authcode-pkce.sh
#   running 클러스터에서 authorization_code + PKCE + DbAuthenticator 전체 흐름을
#   curl 로 끝까지 검증한다(= SmokeClientDbAuthFlowTest 의 실클러스터 등가물).
#
#   증명 대상(§S3' 이후 남은 "마지막 한 칸"):
#     1) SmokeClientSeeder 가 demo-web(public+PKCE)을 RegisteredClientRepository.save() 로 등록
#     2) 폼 로그인 사용자가 LocalDemo 의 demo 가 아니라 authdb app_user 의 tester
#        (= DbAuthenticator 가 {bcrypt} 해시로 검증)
#     3) openid 코드 교환이 access_token + id_token 을 발급하고 sub = tester
#     4) /oauth2/jwks(RS256) 가 200 으로 공개키를 제공(서명 검증 측과 맞물림)
#
#   ⚠️ oauth2_registered_client 에 SQL INSERT 수동 등록 금지 — 시더(repo.save())가 등록한다.
#   참고: 아카이브 docs/_internal/archive/NEXT_KIND_AUTH_TOKEN_FLOW.md, PITFALLS §5/§9
# ---------------------------------------------------------------------
# 사용법:
#   bash smoke-authcode-pkce.sh                 # 시더 이미 on 가정, 바로 검증
#   ENSURE_SEEDER=1 bash smoke-authcode-pkce.sh # 플래그 미설정 시 켜고 rollout 대기 후 검증
#   CTX=kind-sanity NS=si-msa bash smoke-authcode-pkce.sh
# 의존: kubectl, curl, openssl, python3  (WSL/Ubuntu 표준)
# =====================================================================
set -euo pipefail

CTX="${CTX:-$(kubectl config current-context)}"
NS="${NS:-si-msa}"
LPORT="${LPORT:-9000}"          # 로컬 port-forward 포트
CPORT="${CPORT:-9000}"          # auth-server 컨테이너 포트
CLIENT_ID="${CLIENT_ID:-demo-web}"
REDIRECT_URI="${REDIRECT_URI:-http://127.0.0.1:8081/login/oauth2/code/demo-web}"  # 시더 등록값과 정확히 일치
LOGIN_ID="${LOGIN_ID:-tester}"
PASSWORD="${PASSWORD:-Test1234!}"
BASE="http://localhost:${LPORT}"
CJ="$(mktemp)"                  # 쿠키 자
PF_PID=""

cleanup() { [ -n "$PF_PID" ] && kill "$PF_PID" 2>/dev/null || true; rm -f "$CJ"; }
trap cleanup EXIT

note()  { printf '\033[36m== %s\033[0m\n' "$*"; }
ok()    { printf '\033[32m  ✅ %s\033[0m\n' "$*"; }
fail()  { printf '\033[31m  ❌ %s\033[0m\n' "$*"; exit 1; }

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }          # stdin → base64url(no pad)
jget()   { python3 -c 'import sys,json;print(json.load(sys.stdin).get(sys.argv[1],""))' "$1"; }

# ---------------------------------------------------------------------
note "0) (옵션) 시더 플래그 보장"
if [ "${ENSURE_SEEDER:-0}" = "1" ]; then
  CUR="$(kubectl --context "$CTX" -n "$NS" get deploy/auth-server \
        -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="FRAMEWORK_AUTH_SEED_SMOKE_CLIENT")].value}' 2>/dev/null || true)"
  if [ "$CUR" != "true" ]; then
    kubectl --context "$CTX" -n "$NS" set env deploy/auth-server FRAMEWORK_AUTH_SEED_SMOKE_CLIENT=true
    kubectl --context "$CTX" -n "$NS" rollout status deploy/auth-server --timeout=120s
    ok "시더 on + rollout 완료"
  else
    ok "시더 이미 on"
  fi
else
  ok "건너뜀(ENSURE_SEEDER=1 로 강제 가능) — 03-dev-overlay-up.sh 가 이미 켰다면 불필요"
fi

# ---------------------------------------------------------------------
note "1) port-forward ${LPORT} → svc/auth-server:${CPORT}"
kubectl --context "$CTX" -n "$NS" port-forward "svc/auth-server" "${LPORT}:${CPORT}" >/dev/null 2>&1 &
PF_PID=$!
for i in $(seq 1 30); do
  curl -fsS "${BASE}/.well-known/openid-configuration" >/dev/null 2>&1 && break
  sleep 0.5; [ "$i" = 30 ] && fail "discovery 미응답 — auth-server 파드 상태 확인"
done
ISSUER="$(curl -fsS "${BASE}/.well-known/openid-configuration" | jget issuer)"
ok "discovery 200 (issuer=${ISSUER})"

# ---------------------------------------------------------------------
note "2) PKCE 파라미터 생성(S256)"
CODE_VERIFIER="$(openssl rand 32 | b64url)"
CODE_CHALLENGE="$(printf '%s' "$CODE_VERIFIER" | openssl dgst -binary -sha256 | b64url)"
NONCE="n-$(openssl rand 8 | b64url)"
ok "code_verifier/challenge·nonce 준비"

# ---------------------------------------------------------------------
note "3) 폼 로그인(CSRF) — DbAuthenticator(authdb app_user.tester) 경유"
CSRF="$(curl -fsS -c "$CJ" "${BASE}/login" | grep -o 'name="_csrf"[^>]*value="[^"]*"' | sed -E 's/.*value="([^"]*)".*/\1/' | head -1)"
[ -n "$CSRF" ] || fail "_csrf 스크랩 실패 — /login 폼 구조 확인"
# 단일 POST 로 인증(세션 회전/CSRF 소비 회피). 성공=302(→ "/"), 실패=302(→ /login?error).
LOGIN_OUT="$(curl -s -b "$CJ" -c "$CJ" -o /dev/null -w '%{http_code} %{redirect_url}' \
  --data-urlencode "username=${LOGIN_ID}" \
  --data-urlencode "password=${PASSWORD}" \
  --data-urlencode "_csrf=${CSRF}" \
  "${BASE}/login")"
LOGIN_CODE="${LOGIN_OUT%% *}"
LOGIN_LOC="${LOGIN_OUT#* }"
case "$LOGIN_LOC" in
  *"/login?error"*) fail "폼 로그인 실패(자격증명/Authenticator) — DbAuthenticator·app_user.tester 확인" ;;
esac
ok "폼 로그인 성공(http=${LOGIN_CODE}) — 인증 세션 확보"

# ---------------------------------------------------------------------
note "4) /oauth2/authorize → code 수령(demo-web, consent 미요구)"
AUTH_LOC="$(curl -s -b "$CJ" -o /dev/null -w '%{redirect_url}' -G "${BASE}/oauth2/authorize" \
  --data-urlencode "response_type=code" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "scope=openid profile" \
  --data-urlencode "nonce=${NONCE}" \
  --data-urlencode "code_challenge=${CODE_CHALLENGE}" \
  --data-urlencode "code_challenge_method=S256")"
case "$AUTH_LOC" in
  *"error="*) fail "authorize 거부: ${AUTH_LOC} (invalid_client 이면 시더 미적용 — ENSURE_SEEDER=1)" ;;
  *"code="*)  : ;;
  *)          fail "authorize 가 code 리다이렉트가 아님: '${AUTH_LOC}' (로그인 세션/매처 확인)" ;;
esac
CODE="$(printf '%s' "$AUTH_LOC" | sed -E 's/.*[?&]code=([^&]*).*/\1/')"
ok "authorization code 수령"

# ---------------------------------------------------------------------
note "5) /oauth2/token — code + code_verifier 교환(public client)"
TOK_JSON="$(curl -fsS "${BASE}/oauth2/token" \
  --data-urlencode "grant_type=authorization_code" \
  --data-urlencode "code=${CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "client_id=${CLIENT_ID}" \
  --data-urlencode "code_verifier=${CODE_VERIFIER}")"
ACCESS="$(printf '%s' "$TOK_JSON" | jget access_token)"
IDTOK="$(printf '%s' "$TOK_JSON" | jget id_token)"
[ -n "$ACCESS" ] || fail "access_token 미발급: ${TOK_JSON}"
[ -n "$IDTOK" ]  || fail "id_token 미발급(openid scope 확인): ${TOK_JSON}"
ok "access_token + id_token 발급"

# ---------------------------------------------------------------------
note "6) id_token payload 디코드 + 클레임 단언"
PAYLOAD="$(printf '%s' "$IDTOK" | cut -d. -f2)"
PAD=$(( (4 - ${#PAYLOAD} % 4) % 4 )); PAYLOAD="${PAYLOAD}$(printf '=%.0s' $(seq 1 $PAD))"
CLAIMS="$(printf '%s' "$PAYLOAD" | tr '_-' '/+' | openssl base64 -d -A 2>/dev/null)"
SUB="$(printf '%s' "$CLAIMS"   | jget sub)"
ISS="$(printf '%s' "$CLAIMS"   | jget iss)"
GOT_NONCE="$(printf '%s' "$CLAIMS" | jget nonce)"
[ "$SUB" = "$LOGIN_ID" ]   || fail "sub='${SUB}' ≠ '${LOGIN_ID}' — DbAuthenticator 경로가 아님(LocalDemo demo?)"
[ "$GOT_NONCE" = "$NONCE" ] || fail "nonce 불일치(왕복 실패)"
ok "sub=${SUB} · iss=${ISS} · nonce 왕복 ✅ (DbAuthenticator 경로 증명)"

# ---------------------------------------------------------------------
note "7) /oauth2/jwks 200 (RS256 공개키)"
JWKS_CODE="$(curl -s -o /dev/null -w '%{http_code}' "${BASE}/oauth2/jwks")"
[ "$JWKS_CODE" = "200" ] || fail "jwks=${JWKS_CODE} (서명키 0개면 §S3' 함정 재발 — auth_signing_key 확인)"
ok "jwks=200"
# (전체 RS256 서명검증은 JUnit ResourceServerJwtVerifier 가 담당 — 여기선 디코드+클레임+jwks200 까지)

printf '\033[32m\n🟢 authorization_code + PKCE + DbAuthenticator 실클러스터 흐름 통과 — ② 검증 완료\033[0m\n'
