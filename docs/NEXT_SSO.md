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
`framework-oauth-client`(OAuth2 **+ OIDC RP 강화**), `framework-saml-sp`(**SAML 2.0 SP**).

> **➡️ 다음 세션 후보(택1, 착수 설계 = §6)**: ~~**6.1** SAML redis `Saml2AuthenticationRequestRepository`(멀티 파드)~~ ✅**완료(2026-06-04)** · **6.2** SAML SLO(`saml2Logout`) ← **다음 권장** · **6.3** C) Authorization Server(별도 서비스, 명시 요구 시·후순위) · (그 다음) **6.4** 4) Passwordless. 권장 순서 = ~~6.1~~ → **6.2** → (필요 시) 6.3.

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
- [x] **AuthnRequest 저장소** — 세션(기본) + **redis 구현 완료(6.1, 2026-06-04)**. `request-repository: redis`(+HTTPS) 면 redis 공유 저장소로 스티키 세션 제거. 상관은 서버 발급 쿠키(`SameSite=None;Secure`), 직렬화는 고정형 코덱. starter 부재 시 fail-fast 유지.
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

### 5.9 후속(SAML 모듈 내 — 상세 착수 설계는 §6)
- ✅ **받는 쪽 BUILD SUCCESSFUL + 컴파일 정상 확인(2026-06-04)**: `:framework-saml-sp:test :framework-archtest:test spotlessApply` 통과, OpenSAML `5.1.6`(Shibboleth)·`spring-security-saml2-service-provider:7.0.5` 정상 해소.
- ✅ **`Saml2AuthenticatedPrincipal` deprecation 경고 처리(2026-06-04)**: SS7 이 assertion 세부를 principal 에서 분리하며 deprecated(후속=`Saml2AssertionAuthentication.getRelyingPartyRegistrationId()`+`Saml2ResponseAssertionAccessor`). 7.0.x 완전 동작·제거는 빨라야 SS8 → `SamlAuthenticationSuccessHandler.onAuthenticationSuccess` 에 **메서드 한정 `@SuppressWarnings("deprecation")`+마이그레이션 TODO**. 정식 교체는 §6.2(또는 별도)에서 IDE 컴파일로 접근자 메서드 확정 후.
- ✅ **redis 기반 `Saml2AuthenticationRequestRepository`(완료 2026-06-04)** — 설계·구현 **§6.1**(고정형 코덱 + 상관 쿠키 `SameSite=None;Secure`).
- ⏭️ **SLO(Single Logout, `saml2Logout`)** → 설계 **§6.2**.
- (선택) 메타데이터 없는 IdP(엔드포인트/인증서 수동 입력) 지원.

---

## 6. ⏭️ 다음 세션 작업 후보 (착수 설계)

> B-SAML 까지 완료. 다음 세션은 아래 셋 중 택1(또는 순서대로). 각 항목은 **결정 → 인터페이스 → 함정 → 테스트** 순으로 바로 착수 가능하게 정리.
> 공통 제약(되풀이): 작성 환경은 Maven Central/Shibboleth 차단 → SAML/SS 본체 컴파일 불가. 순수 로직만 JDK 검증하고 본체는 받는 쪽 gradle(sshd/SAML 패턴).

### 6.1 SAML redis `Saml2AuthenticationRequestRepository` (멀티 파드, 스티키 세션 제거) — ✅ 구현 완료(2026-06-04)

> **구현 결과 요약**: `framework-saml-sp/store/` 에 `Saml2AuthnRequestCodec`(순수 JDK 고정형 코덱) + `RedisSaml2AuthenticationRequestRepository` 추가. `SamlSpAutoConfiguration` 의 fail-fast 가드를 redis 빈 등록으로 교체(+ starter 부재 시 별도 guard 빈으로 fail-fast 유지). `SamlSpProperties.Redis`(keyPrefix/ttl/cookie*) 신설. 코덱 라운드트립 JDK 단독 20케이스 통과(받는 쪽 JUnit + 풀와이어링 검증 예정).
>
> **설계 대비 확정된 차이 2건**:
> - **상관 키 = RELAY_STATE 가 아니라 서버 발급 UUID 쿠키**. 세션 없는 멀티 파드에서 save↔load 상관을 묶으려면 서버가 발급한 상관관계 쿠키(UUID)로 redis 키를 지정한다(RelayState 는 앱 의미값이라 키로 부적합).
> - **⚠️ 쿠키는 반드시 `SameSite=None; Secure`**: POST 바인딩 ACS 콜백은 IdP→SP 로의 **크로스사이트 top-level POST** 라 `SameSite=Lax/Strict` 쿠키가 전송되지 않는다. 따라서 상관 쿠키는 `SameSite=None`(+`Secure`=HTTPS 필수). `None`+비-Secure 조합은 시작 시 fail-fast. 로컬 평문 HTTP 개발은 `request-repository: session` 사용.
>
> **빌더 확정**: 하위타입 복원은 `Saml2RedirectAuthenticationRequest`/`Saml2PostAuthenticationRequest` 모두 public 팩토리가 `withRelyingPartyRegistration(RelyingPartyRegistration)` 뿐 → 복원에 `RelyingPartyRegistrationRepository.findByRegistrationId` 주입 필수(코덱이 보관한 registrationId 로 조회). `AbstractSaml2AuthenticationRequest` 자동 주입은 빈 등록만으로 SS7 의 `Saml2LoginConfigurer` 가 `getBeanOrNull` 로 감지(체인 DSL 수정 불필요).

- **왜**: SP-initiated 흐름은 AuthnRequest↔Response 상관(InResponseTo/RelayState)을 기본 HTTP 세션에 둔다 → 게이트웨이가 authorize 와 ACS 콜백을 다른 파드로 보내면 깨짐. 현재는 게이트웨이 스티키 세션으로 핸드셰이크(수초)를 묶고 있음. redis 공유로 스티키 의존 제거 = k8s 친화.
- **SS7 인터페이스(확인됨)**: `Saml2AuthenticationRequestRepository<T extends AbstractSaml2AuthenticationRequest>` — `T loadAuthenticationRequest(HttpServletRequest)` · `void saveAuthenticationRequest(T, HttpServletRequest, HttpServletResponse)` · `T removeAuthenticationRequest(HttpServletRequest, HttpServletResponse)`. 노출: `@Bean` 으로 등록하면 `Saml2WebSsoAuthenticationRequestFilter`(save)·`Saml2WebSsoAuthenticationFilter`/`Saml2AuthenticationTokenConverter`(load/remove)가 사용. 키 = 요청 파라미터 `Saml2ParameterNames.RELAY_STATE`(없으면 거부/로그). 기본 구현 `HttpSessionSaml2AuthenticationRequestRepository` 참고.
- **핵심 난점 = 직렬화**: `AbstractSaml2AuthenticationRequest` 는 추상(하위타입 `Saml2RedirectAuthenticationRequest`/`Saml2PostAuthenticationRequest`), 필드 = `samlRequest`(인코딩 본문)·`relayState`·`authenticationRequestUri`·`relyingPartyRegistrationId`(+Redirect 는 `sigAlg`/`signature`). **Java 직렬화 금지**(파드 버전·SS 버전 간 취약, OAuth state 와 같은 결로 회피). → **필드를 명시 추출해 수기 고정 셰이프로 (역)직렬화**(`SecureWebResponder`/OAuth state 수기 직렬화 선례) 후 redis `SET ... PX ttl` 1회용 저장, load 시 binding(`relayState`/`REDIRECT|POST`)으로 하위타입 복원. **빌더 = `AbstractSaml2AuthenticationRequest.Builder` 하위(`Saml2RedirectAuthenticationRequest.withRelayState(...)` 류) — IDE 에서 정확한 빌더/게터 확정 필수.**
- **결정 필요**: ① 키 prefix(`saml:authnreq:` 류) · ② TTL(AuthnRequest 수명, 기본 2~5m) · ③ 토글 = 이미 있는 `framework.saml-sp.request-repository: redis`(현재 fail-fast 가드 → 구현되면 가드를 redis 빈 등록으로 교체) · ④ redis 미가용 시 fail-fast vs 세션 폴백.
- **배선**: `SamlSpAutoConfiguration` 에서 `request-repository=redis` & `StringRedisTemplate` 존재 시 `RedisSaml2AuthenticationRequestRepository` 빈 등록(현 fail-fast 분기 대체), `@ConditionalOnClass(StringRedisTemplate)`+`compileOnly spring-boot-starter-data-redis`(+test 재선언). OAuth `RedisOAuthStateStore` 패턴 그대로.
- **테스트**: 직렬화 왕복(라운드트립) 순수 단위(redis/SS 없이 fake map 또는 직렬화 함수 직접) + 오토컨피그 토글(redis 빈 등록/세션 백오프). 본체는 받는 쪽.

### 6.2 SAML SLO (Single Logout, `saml2Logout`)
- **왜**: 현재 중앙 로그아웃(SSO A)은 우리 토큰(jti 블랙리스트)만 무효화. SAML 로그인 사용자를 **IdP 세션까지** 끊으려면 SAML SLO(SP-initiated LogoutRequest → IdP → LogoutResponse) 필요. 규제/공공에서 요구되기도 함.
- **구현 방향**: SS7 DSL `http.saml2Logout(Customizer)` 를 SAML 체인에 추가. RP 메타데이터에 SLO 엔드포인트가 있어야 함(메타데이터 등록이면 자동). 우리 토큰 무효화(`LoginService.logout`/`logoutAll`)와 **순서/연계** 결정 필요 = SAML SLO 성공 핸들러에서 우리 JWT 블랙리스트도 함께 태움.
- **결정 필요**: ① 우리 로그아웃(JWT 블랙리스트)과 SAML SLO 의 트리거 순서·단일 진입점(`POST /api/v1/auth/logout` 확장 vs 별도 `/saml2/logout`) · ② SLO 서명 키(SP 서명 인증서 = RP 등록의 signing credential, 메타데이터/설정) · ③ 멀티 파드 LogoutRequest 상관도 §6.1 과 동일한 세션/redis 이슈 → §6.1 선행 권장 · ④ IdP 가 SLO 미지원 시 우리 토큰만 무효화(graceful).
- **함정**: SLO 는 IdP 별 상호운용성이 까다로움(바인딩·서명·NameID/SessionIndex 매칭). RP 등록에 SLO location + signing credential 필요. **§6.1(redis 상태) 이후**가 자연스러움.
- **테스트**: LogoutRequest 빌드/서명 검증은 본체(받는 쪽). 우리 토큰 연계(블랙리스트 호출)는 순수 단위.

### 6.3 C) Authorization Server — 우리가 IdP/OP 가 되기 (별도 `services/auth-server`)
- **언제**: 대부분 SI 는 **불필요**(우리는 지금까지 RP/SP = 소비자). 우리 서비스가 **다른 시스템에 토큰을 발급하는 OAuth2/OIDC Provider** 가 되어야 할 때만(예: 그룹사 공통 인증, 외부 파트너 OIDC). **명시 요구 시에만 착수.**
- **구현 방향**: **Spring Authorization Server**(별도 서비스 `services/auth-server`, 라이브러리 모듈 아님 — 프레임워크는 RP 자산만 제공). authorization_code+PKCE/client_credentials, JWKS 공개, OIDC discovery. 우리 `framework-security` 의 사용자/RBAC 를 사용자 소스로 연결.
- **결정 필요**: ① 정말 OP 가 되어야 하는가(아니면 기존 RP/SP 로 충분) · ② 서비스 경계(별도 배포·키 관리/회전·동의 화면) · ③ 우리 JWT(자체 발급) vs AS 발급 토큰의 관계(이중 발급기 정리) · ④ 클라이언트 등록 저장소(jdbc).
- **규모**: 별도 서비스 1개 신설 수준 = 가장 큰 작업. SAML 후속(6.1/6.2)보다 **후순위 권장**(명시 요구 전엔 보류).
- **참고**: Spring Authorization Server 는 Boot 4/SS7 정합 버전 사용(BOM 관리 여부 받는 쪽 확인). 새 외부 의존성 0 원칙의 또 다른 예외 가능성(AS 라이브러리) → STACK 갱신 대상.

### 6.4 (그 다음) 4) Passwordless — 패스키/WebAuthn
- 인증 로드맵 4번째. SSO 갈래 정리 후. 별도 설계 노트 신설 예정(`docs/NEXT_PASSWORDLESS.md`).
