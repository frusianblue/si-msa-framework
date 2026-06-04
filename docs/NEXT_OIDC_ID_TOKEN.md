# NEXT_OIDC_ID_TOKEN.md — OIDC id_token 발급 (✅ 완료, 2026-06-04)

> 상태: **✅ 완료(2026-06-04)**. 토큰 발급 라운드트립 e2e(2026-06-04, ✅ 4/4)에서 **의도적으로 미룬** 조각을 마감.
> 신규 e2e `services/auth-server/src/test/.../e2e/OidcIdTokenIssuanceTest`(2 테스트)로 `authorization_code+PKCE`
> 흐름에서 **`openid` scope 포함 → id_token 발급 + 클레임/서명 검증**을 확인.
> **✅ 받는 쪽(로컬/CI)에서 신규 2테스트 통과 확인(2026-06-04).** (작성 환경은 Central 차단으로 미실행이었고, 정적 리뷰 근거가 실행으로 검증됨.)

---

## ⛳ 직전 진단 정정 (중요)

이 문서의 **이전 버전(착수 전)**은 근본 원인을 *"id_token 의 `auth_time` 이 `SessionInformation` 에서 오는데, MockMvc 폼
로그인은 세션 이벤트를 안 일으켜 `SessionRegistry` 가 비어 `SessionInformation` 이 null → Assert"* 로 진단했다.
**이 진단은 SS7 7.0 기준 오진**이었다. GitHub `7.0.x` 브랜치의 정본 `JwtGenerator` 를 대조해 바로잡는다:

- `JwtGenerator` 는 `auth_time`/`sid` 를 **`if (sessionInformation != null) { ... }` 가드 안에서 함께** 부여한다.
  즉 `SessionInformation` 이 null 이면 두 클레임이 **조용히 생략될 뿐 Assert 가 나지 않는다.**
- `"authenticationTime cannot be null"` Assert 는 **`getAuthenticationTime(Authentication)` 안**에 있고, 그 메서드는
  principal 의 `GrantedAuthority` 중 **`FactorGrantedAuthority`(SS7 신규)의 가장 늦은 `getIssuedAt()`** 으로 auth_time
  을 산출하며, 하나도 없을 때 Assert 한다. SAS 1.x 의 `SessionInformation.getLastRequest()` 방식이 **아니다.**
- **핵심 논리**: Assert 가 `getAuthenticationTime` 에서 났다는 것은 그 호출을 감싼 `if (sessionInformation != null)`
  가드를 **통과**했다는 뜻이다 → 따라서 **`SessionInformation` 은 (MockMvc 에서도) 실제로 non-null 이었다**
  (SAS `oidc(withDefaults())` 기본 OIDC 세션 추적이 채움). 세션 레지스트리 와이어링은 **불필요**했다.

**진짜 원인**: 커스텀 `FrameworkAuthenticationProvider` 가
`UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities)` 의 authorities 에 `ROLE_*` 만
싣고 **인증 팩터(`FactorGrantedAuthority`)를 안 붙였다.** 표준 `AbstractUserDetailsAuthenticationProvider`
.`createSuccessAuthentication` 은 `FactorGrantedAuthority.fromAuthority(FACTOR_PASSWORD)` 를 자동 부착하는데,
커스텀 provider 라 그 규약이 누락된 것. (SS7 7.0 이 auth_time 산출을 SAS 1.x 의 세션 기반에서 **인증 팩터 기반**으로 바꿈.)

---

## 1. 무엇을 고쳤나 (해법, 2건 — auth-server 내부, framework-security 무변경)

### ① `FrameworkAuthenticationProvider` — 인증 팩터 부착
- principal(`User`)은 **앱 역할(`ROLE_*`)만** 유지(표준 form-login 과 동일하게 팩터는 principal 이 아닌 인증 토큰 권한에).
- 반환하는 인증 토큰의 authorities = `역할 + FactorGrantedAuthority.fromAuthority(PASSWORD_AUTHORITY)`.
- `FactorGrantedAuthority.fromAuthority(...)` 의 `issuedAt` 기본값 = `Instant.now()` = 로그인 시각 = **auth_time**.
- 직렬화 안전: SS core Jackson **3** 모듈 `org.springframework.security.jackson.CoreJacksonModule`(`tools.jackson.*`)이
  `FactorGrantedAuthority` 를 `allowIfSubType` + mixin 으로 지원(표준 form-login 도 이 팩터를 SAS JDBC 에 저장 → 검증된 경로).

### ② `RoleClaimTokenCustomizer` — roles 클레임에서 팩터 제외
- `roles` 클레임 생성 시 `FactorGrantedAuthority` 를 **필터 제외**.
- 인증 팩터는 *인증 메커니즘 메타데이터*지 인가용 역할이 아니므로, `FACTOR_PASSWORD` 가 `roles` 클레임/다운스트림 권한으로 새 나가면 안 됨.

> 결과: `openid` scope 코드 교환이 200 + id_token 발급. `auth_time` 과 `sid` 는 같은 가드 블록이라 **함께** 채워진다.

---

## 2. 검증 (e2e)

`services/auth-server/src/test/.../e2e/OidcIdTokenIssuanceTest`(`@SpringBootTest` RANDOM_PORT, profile `local`,
MockMvc = `webAppContextSetup + springSecurity()` — 라운드트립 e2e 와 동일 하네스):

- **test1 `openidCodeExchange_issues_id_token_with_valid_oidc_claims`**:
  formLogin demo/demo → `/oauth2/authorize`(scope=`openid profile`, nonce, PKCE S256) → 코드 교환 →
  `id_token` + `access_token` 발급 확인. id_token 을 실 `/oauth2/jwks`(RS256) + `ResourceServerJwtVerifier`
  (`expectedAudience=demo-web` 활성)로 검증(sig/iss/aud/sub) + payload 디코드로 OIDC 클레임 단언:
  `iss`(=issuer)·`sub`(=demo)·`auth_time`(non-null·양수)·`exp`(양수)·`nonce`(왕복)·`sid`(not-blank)·`roles`(contains ROLE_USER).
- **test2 `id_token_with_wrong_expected_audience_is_rejected`**: 검증기 `expectedAudience="some-other-client"` →
  진짜 id_token 이라도 aud 불일치로 `io.jsonwebtoken.JwtException` (음성).

> **MockMvc 로 충분**했다(이전 가설인 WebTestClient 실 흐름/SessionRegistry 시드 불필요). SAS 기본 OIDC 세션 추적이
> `SessionInformation` 을 채우고, 우리가 부착한 `FactorGrantedAuthority` 가 auth_time 을 만든다.

---

## 3. 수용 기준 (Acceptance) — ✅
- [x] `scope=openid profile` authorization_code 교환이 200 + `id_token` 포함.
- [x] id_token 의 `auth_time` 이 null 이 아니고, `iss`/`sub`/`aud(=demo-web)`/`exp` 정상.
- [x] `nonce` 왕복, `sid` 존재.
- [x] id_token 이 AS JWKS(RS256)로 검증되고 `aud` 검증(`expectedAudience=demo-web`) 통과 / 불일치는 거부.
- [x] framework-security 무변경(수정은 auth-server 내부 2파일).
- [x] **받는 쪽(로컬/CI) 실행 — 신규 2테스트 + 라운드트립 회귀 4종 모두 통과(2026-06-04).**
- [ ] **`spotlessCheck`(Palantir) 통과** — 테스트와 별개 CI 게이트. 커밋/PR 전 `./gradlew :services:auth-server:spotlessApply` 1회 권장(수정 3파일이 손수 작성됨).

---

## 4. 관련 코드/문서
- 수정: `services/auth-server/.../user/FrameworkAuthenticationProvider.java` · `.../user/RoleClaimTokenCustomizer.java`.
- 신규 테스트: `services/auth-server/src/test/.../e2e/OidcIdTokenIssuanceTest.java`.
- 발급 설정: `AuthorizationServerConfig`(체인/oidc) · `JdbcRotatingJwkSource`(RS256 키) · `LocalDemo`(demo-web public PKCE).
- 검증기: `framework-security` `ResourceServerJwtVerifier`(aud 검증 옵션) · `framework-oauth-client` `IdTokenVerifier`(RP 측, §5).
- 배경: `docs/modules/AUTH_SERVER.md` · `docs/modules/OIDC_HARDENING.md` · `docs/TOKEN_VERIFICATION_GUIDE.md` · HANDOFF §6 함정.

---

## 5. 다음 (선택) — OIDC 풀루프 마감
- RP 측 `framework-oauth-client` 의 `IdTokenVerifier` 와 정합 확인 — AS 가 발급한 id_token 을 RP 가 그대로 검증하는
  경로까지 e2e 로 닫으면 OIDC 전 구간(발급 AS ↔ 검증 RP) 완결.
- (devops) CI 게이트(archtest + 전 모듈 test PR 차단)·멀티모듈 jacoco 집계도 후보.

---

## 6. 함정 메모(정정 후 확정)
- **id_token `auth_time` ← `FactorGrantedAuthority`(SessionInformation 아님)**. SS7 7.0 `JwtGenerator.getAuthenticationTime`
  은 principal 권한의 `FactorGrantedAuthority` 최신 `issuedAt` 으로 산출, 없으면 Assert. SAS 와 함께 쓰는 **커스텀
  `AuthenticationProvider` 는 표준 provider 처럼 인증 팩터를 부착**해야 한다(이걸 빠뜨려서 났던 문제).
- `auth_time`/`sid` 는 `JwtGenerator` 의 **한 `if (sessionInformation != null)` 가드에서 동시 부여** — auth_time 이
  나오면 sid 도 함께 나온다.
- 인증 팩터는 인가 역할이 아니다 → `roles` 클레임/다운스트림 권한에서 **제외**(②).
- (테스트 헬퍼) JWT 서명 변조 음성테스트는 **서명 세그먼트 중간 문자**를 바꿔야 확실히 깨진다(마지막 base64url 문자는 trailing-bit 무효 가능).
