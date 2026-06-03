# NEXT_SSO.md — 인증 로드맵 3) SSO 작업 노트

> 이 문서는 **SSO 진행 상황 + 다음 세션(= B-SAML SP) 즉시 착수 설계**다. 세션 시작 시 §5(SAML SP)의
> "결정 필요"를 먼저 정하면 된다.

---

## 0. 진행 상황 (인증 로드맵 4-layer)

①신원확인 ②상태유지 ③보안강화 ④인프라통합 중:

- ✅ **1) 소셜 로그인**(`framework-oauth-client`) — 외부 IdP OAuth2 → 자체 JWT 발급.
- ✅ **2) 게이트웨이 엣지 인증**(`services/gateway`) — 게이트웨이 JWT 1차 검증 + 신뢰 헤더 주입.
- 🟡 **3) SSO** — 진행 중. 아래 A/B/C 갈래 중:
  - ✅ **A) 사내 다중 서비스 SSO — 중앙 로그아웃/logout-all**(2026-06-03 완료)
  - ✅ **B-OIDC) 표준 프로토콜 SSO — OIDC RP 강화**(2026-06-03 완료)
  - ✅ **B-SAML) 표준 프로토콜 SSO — SAML 2.0 SP**(2026-06-04 완료, `framework-saml-sp`) (§5)
  - ⬜ **C) 우리가 IdP — Authorization Server**(별도 `services/auth-server`, 후순위)
- ⬜ 4) Passwordless(패스키/WebAuthn) — 그 다음.

현재 자산: `JwtProvider`/`TokenStore`(memory|jdbc|redis)/`AuthenticatedUser`/`TokenResponse`, RBAC, MFA(`MfaGate`),
게이트웨이 엣지 검증(jjwt)+**jti 블랙리스트 reactive 조회**, `framework-context`(헤더 신원 전파),
`framework-oauth-client`(OAuth2 **+ OIDC RP 강화**).

---

## 1. SSO 세 갈래 (참고 — 무엇을 원하는지)

### (A) 사내 다중 서비스 SSO — "우리 서비스끼리 한 번 로그인" ✅ 완료
게이트웨이 엣지 인증 + 공유 JWT 로 대부분 달성돼 있었고, 비어 있던 **중앙 로그아웃**을 채웠다(§3).

### (B) 표준 프로토콜 SSO — OIDC/SAML IdP 연동
"그룹 통합 인증(SSO 서버)에 붙어라" (Keycloak·이니텍·사내 SAML IdP 등).
- **OIDC** = 소셜 로그인의 일반화 → `framework-oauth-client` 의 **OIDC 강화로 완료**(§4).
- **SAML** = 별도 모듈 필요(무거움, OpenSAML) → **다음 세션**(§5).

### (C) 우리가 IdP 가 되기 — Authorization Server
"우리로 로그인"을 남에게 제공(Spring Authorization Server). 플랫폼/오픈API 제공사만 해당. 별도 서비스 권장. 후순위.

---

## 2. (참고) A·B-OIDC 통합 지점

- `LoginService.logout(access, refresh, ip)` = jti 블랙리스트 + refresh 제거 + (선택) 동시세션 해제.
- `LoginService.logoutAll(access, ip)` = 그 사용자 **전 세션** 무효화(동시세션 레지스트리 순회) + 현재 토큰 안전망.
- 게이트웨이 `GatewayTokenVerifier` 가 jti 추출, `RedisGatewayTokenBlacklist` 가 `bl:{jti}` 조회(키 prefix 는 `RedisTokenStore` 와 동일).
- `framework-oauth-client` 의 `OAuthTokenIssuer`(`AuthenticatedUser`→`TokenResponse`)는 **프로토콜 무관** — SAML 도 동일 발급기로 자체 JWT 를 낼 수 있다(§5 결정 항목).

---

## 3. ✅ A) 중앙 로그아웃 / logout-all (완료, 2026-06-03)

- **게이트웨이 중앙 로그아웃**: `GatewayTokenVerifier` 가 jti 추출, `GatewayAuthGlobalFilter` 가 검증 후
  `RedisGatewayTokenBlacklist.isBlacklisted("bl:"+jti)`(reactive) 로 **로그아웃된 토큰을 엣지에서 401 차단**.
  토글 `gateway.auth.blacklist-check.enabled`(기본 off, redis 전용·fail-fast).
- **logout-all**: `LoginService.logoutAll(access, ip)` — 토큰→userId 검증 후 **항상 현재 토큰 블랙리스트**(안전망),
  `ConcurrentSessionService.activeSessions(userId)` 순회로 각 refresh 제거 + accessJti 블랙리스트 + unregister.
  `POST /api/v1/auth/logout-all`. 완전 커버는 `concurrent-session.enabled=true` + `token.store=redis` 전제.
- 문서 `docs/modules/SSO_CENTRAL_LOGOUT.md`, `docs/modules/GATEWAY_EDGE_AUTH.md`.

## 4. ✅ B-OIDC) OIDC RP 강화 (완료, 2026-06-03)

- `framework-oauth-client` 에 **per-provider `oidc` 블록**(기본 off — kakao/naver 등 비OIDC 흐름 보존).
- 켜면: **id_token 검증**(JWKS 의 RSA/EC 서명 또는 HS=client-secret HMAC · iss · aud⊇client-id · exp/nbf±skew · nonce · sub)
  + **discovery 자동적용**(issuer/discovery-uri → 엔드포인트 보충, 지연·1회·캐시) + **nonce 바인딩**(authorize↔callback, state 와 함께 저장).
- 신규 `oidc/` 패키지 4종: `OidcDiscoveryClient`·`JwksKeyResolver`(캐시+회전 재조회+쿨다운)·`IdTokenVerifier`·`OidcMetadataResolver`.
  수정: `OAuthClientProperties`(Oidc nested)·`OAuthClient`(`exchangeCodeForTokens`)·`ProviderRegistry`(require 완화 + nonce/openid URL)·
  state store(nonce 바인딩)·`OAuthLoginService`(OIDC 분기)·autoconfig(빈 4종)·build.gradle(jjwt-impl/jackson testRuntime).
- 검증: `ProviderRegistryOidcTest`(순수) + `IdTokenVerifierTest`(HS256 전 클레임 + RSA JWKS) + `JwksKeyResolverTest`(파싱/회전).
  **사용자 환경 컴파일 + 26 테스트 통과 확인.**
- 문서 `docs/modules/OIDC_HARDENING.md`, `docs/modules/OAUTH_CLIENT.md`(§8 포인터).

---

## 5. ✅ B-SAML) SAML 2.0 Service Provider — 완료(2026-06-04, `framework-saml-sp`)

> **완료 요약**: IdP 메타데이터 기반 RelyingParty 등록 → 전용 `SecurityFilterChain`(`/saml2/**`,`/login/saml2/**`) →
> ACS 성공 시 `SamlUserResolver`(앱) 매핑 → `SamlTokenIssuer`(security 재사용)로 **자체 JWT 즉시 발급**(수기 JSON, 무상태).
> framework-security 무수정(`@AutoConfiguration(after=SecurityAutoConfiguration)` + securityMatcher). 사용 가이드 `docs/modules/SAML_SP.md`.
> 아래 5.1~5.8 은 설계 원문(이력 보존). 실제 결정/변경은 5.6(결정 기록)·5.9(후속) 참조.

### 5.1 목표
SAML 2.0 만 말하는 외부/사내 IdP(공공 통합인증, 일부 대기업 그룹 SSO)에 **SP(Service Provider)로 연동**.
OAuth/OIDC 클라이언트와 **같은 결**: 외부 신원확인 → 앱 리졸버가 우리 사용자로 매핑 → **자체 JWT 발급**(stateless 유지).
즉 SAML 은 "신원확인 프로토콜"일 뿐, 이후 운영은 기존 JWT/게이트웨이/context 그대로.

### 5.2 구현 방식 — Spring Security SAML2 SP (권장)
직접 XML 서명 파싱은 위험·과대 → **`spring-security-saml2-service-provider`** 사용(유지보수·정합성).
- 제공: SP-initiated AuthnRequest(Redirect), ACS(assertion consumer, HTTP-POST), **IdP 메타데이터 기반 서명 검증**,
  NameID/Attribute 추출, SP 메타데이터 노출.
- 필터: `Saml2WebSsoAuthenticationRequestFilter`(로그인 시작)·`Saml2WebSsoAuthenticationFilter`(ACS) 를
  서블릿 `SecurityFilterChain` 에 기여.

### 5.3 ⚠️ 의존성/저장소 함정 (착수 전 필독)
- **OpenSAML 전이 의존**: `spring-security-saml2-service-provider` 는 `org.opensaml:opensaml-*`(5.x)를 끌어온다.
  이것이 **이 프레임워크 최초의 "새 외부 의존성 0" 예외**(SAML 은 XML 서명 때문에 OpenSAML 불가피).
  - 버전: **Spring Security 가 OpenSAML 버전을 전이로 관리**한다("Security depends on OpenSAML by default"). → opensaml 을
    **명시 선언/핀하지 않는다**(SS 관리 버전을 그대로 받는다). ⚠️ 초안의 `${openSamlVersion}` 명시 핀은 (a) 루트 ext 미정의로
    **설정 단계 실패**를 유발하고 (b) SS 관리 버전과 어긋날 위험이 있어 **제거함**(실제 적용 = 전이 의존).
  - **리포지터리(확정)**: ⚠️ **OpenSAML 4+ 는 Maven Central 에 게시되지 않는다**(라이선스/면책 — 2026-02 확인). 즉 Shibboleth
    저장소는 **fallback 이 아니라 필수**. 루트 `allprojects.repositories` 에 `https://build.shibboleth.net/maven/releases/` 를
    **`org.opensaml`/`net.shibboleth` 그룹 한정**으로 추가함(그 외 의존성은 계속 Central 에서만 해소 → saml-sp 미사용 빌드 영향 0).
  - ⚠️ **작성 환경은 Maven Central/Shibboleth 차단** → 여기선 SAML 의존 컴파일 불가. 순수 로직(`SamlAttributeMapper`)만 JDK 검증(14케이스),
    SAML 본체는 받는 쪽 gradle 로 확인(sshd/MINA 패턴). 해소 출처 확인: `./gradlew :framework:framework-saml-sp:dependencies`.
- **다중 파드 in-flight 상태**: SP-initiated 흐름은 AuthnRequest↔Response 상관(RelayState/요청ID)을 보관해야 한다.
  Spring Security 기본 `Saml2AuthenticationRequestRepository` 는 **HTTP 세션** 기반 → 게이트웨이가 다른 파드로 보내면 깨짐.
  → OIDC state 를 redis 에 둔 것과 같은 결정: **redis(또는 쿠키) 기반 커스텀 `Saml2AuthenticationRequestRepository`** 필요(멀티 파드면 필수).
- **OpenSAML 부트스트랩**: `InitializationService.initialize()` 가 필요하나 Spring Security 가 SAML 클래스 로드시 자동 수행(`OpenSamlInitializationService`). 직접 호출 금지·중복 주의.

### 5.4 아키텍처(권장)
- **전용 `SecurityFilterChain`**(framework-security 체인과 분리, `@Order` 우선) — `/saml2/**` + ACS 경로만 매칭.
  성공 핸들러에서 **서버 세션을 만들지 않고** NameID/속성 → `SamlUserResolver`(앱 구현) → `AuthenticatedUser` →
  `OAuthTokenIssuer`(재사용·프로토콜 무관) 로 **자체 JWT 발급** 후 `continue` URL 로 리다이렉트(또는 JSON 반환).
  - framework-security 의 SecurityAutoConfiguration 무수정이 목표(별도 체인 기여).
- **다중 IdP**: `registrations.<id>` 맵(OAuth providers 맵과 대칭) → `RelyingPartyRegistrationRepository`.
- **신원 매핑 리졸버**: `SamlUserResolver`(= `OAuthUserResolver` 의 SAML 판). 속성 키 매핑(nameId/email/name)은 OAuth 의 `userNameAttribute` 류와 대칭.
- **발급기 공유 결정**: `OAuthTokenIssuer` 를 그대로 재사용할지(권장, 이름만 oauth) vs 중립 명칭으로 승격(`ExternalIdentityTokenIssuer`)할지 — §5.6 결정.

### 5.5 엔드포인트(초안)
- SP 메타데이터: `GET /api/v1/auth/saml/{registrationId}/metadata` (IdP 등록용)
- 로그인 시작(SP-initiated): `GET /api/v1/auth/saml/{registrationId}/login` → IdP SSO 로 302
- ACS(assertion 수신): `POST /login/saml2/sso/{registrationId}`(Spring Security 기본) 또는 커스텀
- 화이트리스트: 위 경로가 `permitAll` 이어야 함(현재 `/api/*/auth/**` 매처에 saml 경로/ACS 가 포함되는지 확인, 아니면 추가).

### 5.6 결정 기록 (2026-06-04 확정)
- [x] **구현 = Spring Security SAML2 SP** ✓ (직접 XML 파싱 회피, 유지보수/정합성).
- [x] **OpenSAML 리포지터리/버전** ✓ — 버전=SS 전이 관리(명시 핀 X), 저장소=Shibboleth 그룹 한정 추가(Central 에 없으므로 **필수**).
- [x] **자체 JWT 발급** ✓ — `SamlTokenIssuer`(기본 `DirectSamlTokenIssuer`, security `JwtProvider`/`TokenStore` 재사용). 중립 발급기로 승격하지 않고 SAML 전용 인터페이스를 두되 형태는 OAuth 와 동일. LoginService 통합 시 `@Bean` 교체(`@ConditionalOnMissingBean`).
- [x] **세션 무상태** ✓ — ACS 성공 시 서버 세션 없이 즉시 자체 JWT 발급(`SamlAuthenticationSuccessHandler`, 수기 JSON). 세션은 SAML 핸드셰이크 동안만.
- [x] **AuthnRequest 저장소** — **세션(기본)으로 확정**. 멀티 파드는 현 단계 게이트웨이/인그레스 스티키 세션. redis `Saml2AuthenticationRequestRepository` 는 **5.9 후속**(복합 객체 직렬화 난점 → 검증 후). `request-repository: redis` 설정 시 오토컨피그 fail-fast(조용한 no-op 방지).
- [x] **모듈 위치** ✓ — `framework/framework-saml-sp`(선택형). §5.7 등록 절차 수행 완료(settings include + archtest testImplementation + .imports + 가드 테스트).

### 5.7 신규 모듈 등록 체크리스트 (framework-saml-sp) — ✅ 완료(2026-06-04)
- [x] `settings.gradle` 에 `include 'framework:framework-saml-sp'`
- [x] `framework-archtest/build.gradle` 에 `testImplementation project(':framework:framework-saml-sp')`
- [x] autoconfig `META-INF/spring/...AutoConfiguration.imports` 등록 + **레지스트레이션 가드 테스트**(`SamlSpAutoConfigurationTest`: disabled/백오프)
- [x] build.gradle: `api framework-security` + `api framework-core` + `implementation spring-security-saml2-service-provider`(opensaml=전이, **핀 안 함**) + web `compileOnly`(+test 재선언). (redis 의존은 이번 드롭에서 미사용 → 제거)
- [x] 3단 토글 `framework.saml-sp.enabled`(기본 off) + `@ConditionalOnClass`(SAML2 클래스) + `@ConditionalOnBean(SamlUserResolver)` + `@ConditionalOnMissingBean`
- [x] 사용 가이드 `docs/modules/SAML_SP.md` + `docs/FRAMEWORK_MODULES.md` 카탈로그 행 + STACK.md(OpenSAML = 첫 비-Central 저장소 명시) + 루트 build.gradle Shibboleth 저장소(그룹 한정)

### 5.8 테스트 전략
- 순수: 프로퍼티→`RelyingPartyRegistration` 빌더, 속성→`AuthenticatedUser` 매핑(`SamlUserResolver` 어댑터)은 JDK 검증.
- SAML 본체: Spring Security `Saml2` 테스트 유틸/OpenSAML 로 **서명된 assertion 을 in-test 생성** → 인증 provider 통과/위변조 실패. (OpenSAML init 필요 → 받는 쪽 gradle.)
- 오토컨피그 토글/백오프(`FilteredClassLoader` 로 SAML2 클래스 부재).

### 5.9 후속(다음 단계 — SAML 모듈 내)
- **redis 기반 `Saml2AuthenticationRequestRepository`**(멀티 파드, 스티키 세션 없이). SS7 인터페이스(확인됨):
  `T loadAuthenticationRequest(req)` · `void saveAuthenticationRequest(T, req, resp)` · `T removeAuthenticationRequest(req, resp)`
  (`T extends AbstractSaml2AuthenticationRequest`). 키는 `Saml2ParameterNames.RELAY_STATE`. **난점 = `AbstractSaml2AuthenticationRequest`
  (Redirect/Post 하위타입, samlRequest/relayState/authnRequestUri/relyingPartyRegistrationId/sigAlg/signature 필드) 직렬화** —
  Java 직렬화는 파드 버전 간 취약 → 빌더 기반 수동 (역)직렬화 권장. 작성 환경 컴파일 불가라 **검증 후 도입**(이번 드롭 미포함, redis 설정 시 fail-fast).
- (선택) **SLO**(`saml2Logout`) · **메타데이터 없는 IdP**(엔드포인트/인증서 수동 입력) 지원.

---

## 6. 이후(참고) — C) Authorization Server
별도 `services/auth-server`(Spring Authorization Server). 대부분 SI 는 불필요 → 명시 요구 시에만. B-SAML 이후로.
