# NEXT_SSO.md — 다음 세션 작업 노트 (인증 로드맵 3) SSO)

> 이 문서는 **다음 세션에서 SSO 를 바로 시작**하기 위한 설계/선택지 정리다. 아직 구현 전이며, 세션 시작 시
> 아래 "결정 필요" 항목을 먼저 정하면 된다.

---

## 0. 지금까지 (인증 로드맵 진행)

4-layer 인증 모델(①신원확인 ②상태유지 ③보안강화 ④인프라통합) 중:

- ✅ **1) 소셜 로그인**(`framework-oauth-client`) — 외부 IdP 로 신원확인 → 자체 JWT 발급.
- ✅ **2) 게이트웨이 엣지 인증**(`services/gateway`) — 게이트웨이에서 JWT 1차 검증 + 신뢰 헤더 주입.
- ⏭️ **3) SSO** ← 다음.
- 4) Passwordless(패스키/WebAuthn 등) — 그 다음.

현재 자산: `JwtProvider`/`TokenStore`(memory|jdbc|redis)/`AuthenticatedUser`/`TokenResponse`, RBAC, MFA(`MfaGate`),
게이트웨이 엣지 검증(jjwt), `framework-context`(헤더 기반 신원 전파).

---

## 1. "SSO" 의 범위 — 먼저 어느 것인지 결정

SSO 는 한 단어지만 구현이 완전히 다른 세 갈래가 있다. **무엇을 원하는지부터 정해야 한다.**

### (A) 사내 다중 서비스 SSO — "우리 서비스끼리 한 번 로그인"
가장 흔한 SI 요구. user-service/admin-service/기타 내부 앱이 **하나의 로그인 세션을 공유**.
- 지금 구조(게이트웨이 엣지 인증 + 공유 JWT)로 이미 80% 달성됨. JWT 가 stateless 라 같은 secret 이면 모든 서비스가 같은 토큰을 인정.
- 남은 것: **중앙 로그아웃/세션 무효화**(한 곳에서 로그아웃하면 전체 무효), **토큰 갱신 일원화**, 브라우저 도메인 간 쿠키/리다이렉트 처리.
- 작업량: 중간. 기존 `TokenStore` 블랙리스트(jti) + 게이트웨이에서 블랙리스트 조회를 reactive Redis 로 추가하면 중앙 로그아웃이 된다.

### (B) 표준 프로토콜 SSO — OIDC/SAML IdP 연동 (사내 통합인증/타사 IdP)
공공·대기업 SI 에서 "그룹 통합 인증(SSO 서버)에 붙어라" 요구. Keycloak·이니텍·SAML 기반 사내 IdP 등.
- 사실상 **소셜 로그인의 일반화** — `framework-oauth-client` 가 이미 임의 OIDC 공급자를 지원하므로, OIDC 면 provider 설정만 추가하면 상당 부분 커버.
- 남은 것: **OIDC 표준 강화**(id_token 검증/JWKS 키 회전·nonce·discovery 문서 자동 적용), SAML 이면 별도(무거움, 보통 Spring Security SAML).
- 작업량: OIDC 강화=중간, SAML=큼.

### (C) 우리가 IdP 가 되기 — Authorization Server
"우리 서비스로 로그인" 을 **남에게 제공**. Spring Authorization Server 도입.
- 대부분의 SI/사내 시스템은 **불필요**. 플랫폼/오픈API 제공사만 해당.
- 작업량: 큼. 별도 서비스(`services/auth-server`)로 분리 권장.

---

## 2. 추천 출발점

대부분의 현실적 요구는 **(A) 사내 다중 서비스 SSO** 다. 그리고 그중 **중앙 로그아웃**이 유일하게 비어 있는 조각이다.
제안하는 1차 범위:

1. **공유 세션 레지스트리** — `TokenStore`(redis) 에 이미 jti 블랙리스트가 있다. 게이트웨이가 검증 시 **블랙리스트(jti) 를 reactive Redis 로 조회**해, 로그아웃된 토큰을 엣지에서 차단(현재는 서명/만료만 봄).
2. **중앙 로그아웃 전파** — 한 서비스의 `/logout` 이 jti 블랙리스트 + refresh 무효화를 공유 저장소에 반영(이미 `LoginService.logout` 이 함). 게이트웨이가 그걸 읽으면 즉시 전 서비스 무효.
3. (선택) **SSO 로그인 리다이렉트 흐름** — 미인증 브라우저 요청을 로그인 서비스로 보내고, 로그인 후 원래 위치로 복귀(`continue` 파라미터).

이렇게 하면 "한 번 로그인 → 전 서비스 사용 → 한 번 로그아웃 → 전 서비스 차단" 이 완성된다. 새 모듈 없이 기존
`framework-security`(TokenStore) + `services/gateway`(엣지 필터) 확장으로 가능성이 높다.

---

## 3. 세션 시작 시 결정 필요

- [ ] **범위 = (A) 사내 다중 서비스 SSO** 로 진행할지 (추천), 아니면 (B) OIDC IdP 연동 / (C) Authorization Server 인지
- [ ] (A) 라면: 게이트웨이 엣지에서 **jti 블랙리스트 조회**를 추가할지(중앙 로그아웃의 핵심)
- [ ] 토큰 저장소 운영 표준이 redis 인지 확인(현재 memory|jdbc|redis 선택형 — SSO 는 공유 저장소 필요 → redis 권장)

---

## 4. 확인된 통합 지점 (구현 시 참고)

- `LoginService.logout(access, refresh, ip)` 가 이미 jti 블랙리스트 + refresh 제거 + (선택) 동시세션 해제 수행.
- `TokenStore.isBlacklisted(jti)` / `blacklist(jti, ttl)` 존재 — 게이트웨이가 이걸 reactive 로 조회하려면 별도 reactive Redis 접근(게이트웨이는 이미 `spring-boot-starter-data-redis-reactive` 보유).
- 게이트웨이 `GatewayTokenVerifier` 는 jti 를 안 본다(현재 sub/roles/exp/typ 만). 블랙리스트 조회를 넣으려면 `Claims.getId()`(jti) 추출 + reactive Redis `hasKey` 추가.
- 키 규약: `framework-security` 의 블랙리스트 키 prefix 를 확인해 게이트웨이가 같은 키를 조회해야 함(다음 세션에서 `RedisTokenStore` 의 키 형식 확인).
