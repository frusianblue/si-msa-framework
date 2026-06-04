# NEXT_RP_IDTOKEN_LINK.md — 다음 세션 착수: RP 연계(OIDC 풀루프 마감)

> 상태: **착수 전(설계/조사 완료 — 바로 시작 가능)**.
> 목표 = **우리 AS(`services/auth-server`)가 발급한 id_token 을, 우리 RP 스택(`framework-oauth-client` 의
> `IdTokenVerifier`)이 그대로 검증**하는 경로를 e2e 로 닫는다. 발급(AS)·검증(RP) **양끝이 모두 우리 코드**임을
> 입증해 OIDC 전 구간(발급↔검증)을 완결한다.
>
> 직전 세션(2026-06-04)에 **id_token 발급**은 완료(✅ `OidcIdTokenIssuanceTest` 2/2 + 라운드트립 회귀 4/4 + spotless).
> 그때는 발급된 id_token 을 **AS 측 `ResourceServerJwtVerifier`** 로만 검증했다. 이번엔 **RP 측 `IdTokenVerifier`** 로 검증한다.

---

## 1. 현재 사실 (조사 완료 — 코드 대조)

### RP 측 (`framework-oauth-client`)
- **`IdTokenVerifier.verify(Provider provider, String idToken, String expectedNonce)` → `Map<String,Object>` claims.**
  - 서명: 헤더 alg 가 `HS*` 면 client-secret HMAC, 그 외(RS/ES/PS)는 **JWKS 에서 kid 로 공개키 해석**(`JwksKeyResolver`).
  - 검증: `iss`(== `oidc.issuer`) · `aud ⊇ clientId` · `exp/nbf`(clockSkew) · `nonce`(oidc.nonce=true 일 때 expectedNonce 와 일치) · `sub` 필수.
  - **실패 시 `BusinessException(ErrorCode.Common.UNAUTHORIZED)`** 를 던진다(⚠️ AS 측 `ResourceServerJwtVerifier` 의 `io.jsonwebtoken.JwtException` 과 **예외 타입이 다름** — 음성 테스트 단언 주의).
- **`JwksKeyResolver(RestClient restClient, Duration cacheTtl)`** + `resolve(jwksUri, kid)`. 실 JWKS 를 치려면 `RestClient` 주입(실 HTTP), 단위테스트는 `protected String fetchJwksJson(String)` 오버라이드(기존 `IdTokenVerifierTest` 방식).
- **`IdTokenVerifier(JwksKeyResolver)`**.
- Provider OIDC 설정 필드: `oidc.{enabled, issuer, jwksUri, discoveryUri, clockSkew(=60s 기본), nonce(=true 기본)}` + `clientId` + (HS 면)`clientSecret`.
- 전체 콜백 오케스트레이션은 `OAuthLoginService.callback()` 에 이미 있음(OIDC 면 토큰응답에서 `id_token` 추출 → `IdTokenVerifier.verify` → 클레임으로 신원 → 자체 JWT 발급).

### AS 측 (`services/auth-server`)
- id_token 발급은 ✅(직전 세션). 발급 클레임: `iss=http://localhost:9000`(=`AuthorizationServerSettings.getIssuer()`, env `AUTH_SERVER_ISSUER`) · `sub=demo` · `aud=<client_id>` · `auth_time` · `exp` · `nonce`(요청 시) · `sid` · `roles`(=`RoleClaimTokenCustomizer`, 인증 팩터 제외).
- JWKS: `/oauth2/jwks`(RS256, kid). discovery: `/.well-known/openid-configuration`.
- 데모 클라이언트(`LocalDemo`): `demo-web`(**public + PKCE**, authorization_code+refresh, scope openid/profile, redirect `http://127.0.0.1:8081/login/oauth2/code/demo-web`, consent 미요구) · `demo-service`(confidential client_credentials only — id_token 흐름 아님).

### ⚠️ 핵심 갭 — public/PKCE vs confidential
- RP 의 `OAuthClient.exchangeCodeForTokens` 는 **`client_id` + `client_secret`(client_secret_post)** 로 코드 교환한다 — **PKCE(code_verifier) 미지원**.
- AS 의 `demo-web` 은 **public + PKCE(`ClientAuthenticationMethod.NONE`, requireProofKey)** — client_secret 을 안 받는다.
- → **전체 흐름(`OAuthLoginService` 콜백)으로 연계하려면** AS 에 **confidential authorization_code OIDC 클라이언트(예: `demo-rp`)** 를 새로 등록해야 한다(아래 B안).
- → **단, id_token 자체는 클라이언트 인증방식과 무관**하다. 검증기 수준 연계(아래 A안)는 `demo-web` 으로 발급한 id_token 을 그대로 써도 된다(권장).

---

## 2. 구현 계획 — A안(권장) 먼저, B안은 선택

### ✅ A안 (권장): 검증기 수준 풀루프 — `services/auth-server` 안에서 자기완결
직전 `OidcIdTokenIssuanceTest` 의 발급 하네스를 재사용해 **실 AS 가 발급한 id_token** 을 만들고, 그걸 **실 `IdTokenVerifier`**
(실 `JwksKeyResolver` → 라이브 `/oauth2/jwks`)로 검증한다. 양끝 모두 실제 컴포넌트, **라이브러리 의존만** 추가(서비스 간 의존 없음 —
라운드트립 테스트가 `framework-security` 검증기를 쓰는 것과 동일 패턴).

1. **Gradle**: `services/auth-server/build.gradle` 에
   ```gradle
   testImplementation project(':framework:framework-oauth-client')
   ```
   추가(현재 framework-core/security/mybatis/lock 만 implementation, oauth-client 없음). jjwt 는 두 모듈 모두 전이로 동일 버전.
2. **신규 테스트** `services/auth-server/src/test/.../e2e/OidcRpLinkageTest`(`@SpringBootTest` RANDOM_PORT, profile `local`):
   - 발급: `OidcIdTokenIssuanceTest` 의 `issueOpenidTokens(nonce)` 패턴 그대로(demo-web, PKCE S256, scope=openid profile) → `id_token` 확보.
   - 검증기 구성:
     ```java
     var resolver = new JwksKeyResolver(RestClient.create(), Duration.ofMinutes(5));
     var verifier = new IdTokenVerifier(resolver);
     var p = new OAuthClientProperties.Provider();
     p.setClientId("demo-web");                 // aud ⊇ client-id 통과(id_token aud = client_id)
     p.getOidc().setEnabled(true);
     p.getOidc().setIssuer(issuer());           // = authorizationServerSettings.getIssuer()
     p.getOidc().setJwksUri("http://localhost:" + port + "/oauth2/jwks");
     p.getOidc().setNonce(true);
     Map<String,Object> claims = verifier.verify(p, idToken, nonce);
     ```
   - 단언: `claims.get("sub")="demo"` · `iss=issuer()` · `roles` 에 `ROLE_USER` 포함 · `nonce` 왕복 · `auth_time` 존재.
   - 음성 3종(전부 `BusinessException`, **JwtException 아님**):
     - 잘못된 `issuer` 핀 → UNAUTHORIZED.
     - `clientId="some-other-client"`(aud 불일치) → UNAUTHORIZED.
     - `expectedNonce` 불일치 → UNAUTHORIZED.
3. (선택) `JwksKeyResolver` 캐시/회전 재조회 동작도 한 번 곁들이면 좋음(같은 id_token 2회 검증 → 두번째는 캐시 히트).

### ⛳ B안 (선택, 스트레치): 전체 흐름 e2e — `OAuthLoginService` 콜백까지
RP 가 실제 authorize→callback 으로 AS 와 통신하는 충실한 e2e. **AS 에 confidential 클라이언트 추가가 전제.**
1. `LocalDemo` 에 `demo-rp` 등록: `clientSecret`(encoder.encode) · `CLIENT_SECRET_POST`(RP 의 교환 방식과 일치) · authorization_code(+refresh) · scope openid/profile · redirect = RP 콜백 URL · consent 미요구.
2. RP `Provider` 설정: `tokenUri=/oauth2/token` · `authorizationUri=/oauth2/authorize` · `userInfoUri`(선택, `/userinfo`) · `oidc.{enabled,issuer,jwksUri 또는 discoveryUri}` · clientId/clientSecret=demo-rp · oidc.nonce=true.
3. `OAuthLoginService.authorizeUrl` → (로그인 폼) → AS code 리다이렉트 → `OAuthLoginService.callback` → 자체 `TokenResponse`.
   - ⚠️ RP↔AS 가 서로 다른 부트 컨텍스트라 한 테스트 JVM 에서 양쪽을 올리기 번거로움. discovery(`discoveryUri`)로 엔드포인트 자동 보충 가능.
   - 이건 "연계 입증"엔 과할 수 있음 → **A안으로 충분**하면 B안은 백로그로.

---

## 3. 수용 기준 (Acceptance)
- [ ] `services/auth-server` 에 `testImplementation project(':framework:framework-oauth-client')` 추가, 컴파일 통과.
- [ ] 실 AS 발급 id_token 이 **RP `IdTokenVerifier`** 로 검증되어 `sub=demo`·`iss`·`roles(ROLE_USER)`·`nonce` 확인.
- [ ] 음성 3종(issuer/aud(clientId)/nonce 불일치)이 **`BusinessException(UNAUTHORIZED)`** 로 거부.
- [ ] 기존 `OidcIdTokenIssuanceTest`·`TokenIssuanceRoundTripTest` 회귀 유지.
- [ ] `spotlessApply` 적용(Palantir 포맷 게이트).
- [ ] (선택/B안) confidential `demo-rp` 등록 + `OAuthLoginService` 전체 흐름 e2e.

---

## 4. 관련 코드/문서
- RP 검증기: `framework/framework-oauth-client/.../oidc/{IdTokenVerifier,JwksKeyResolver,OidcMetadataResolver,OidcDiscoveryClient}.java` · 콜백 오케스트레이션 `web/OAuthLoginService.java` · 설정 `config/OAuthClientProperties.java`(Provider/Oidc).
- RP 테스트 하네스 참고: `framework/framework-oauth-client/src/test/.../oidc/IdTokenVerifierTest.java`(JWKS JSON 수기 생성·`fetchJwksJson` 오버라이드 패턴).
- AS 발급/하네스: `services/auth-server/.../e2e/OidcIdTokenIssuanceTest.java`(발급 helper 재사용 대상) · `config/LocalDemo.java`(클라이언트 등록) · `AuthorizationServerConfig.java`.
- 사용 가이드: `docs/modules/OIDC_HARDENING.md`(RP 측) · `docs/modules/AUTH_SERVER.md`(AS 측) · `docs/TOKEN_VERIFICATION_GUIDE.md`.

---

## 5. 함정 메모(착수 전 예측)
- **예외 타입 차이**: RP `IdTokenVerifier` 는 `BusinessException(UNAUTHORIZED)`, AS `ResourceServerJwtVerifier` 는 `io.jsonwebtoken.JwtException`. 음성 단언에서 혼동 금지.
- **aud = client_id**: id_token 의 aud 는 client_id 이므로 Provider.clientId 를 발급에 쓴 클라이언트와 **동일**(A안=demo-web)하게.
- **issuer 는 런타임에서**: `oidc.issuer` 를 하드코딩하지 말고 `authorizationServerSettings.getIssuer()` 로 읽어 핀(로컬/CI 동일).
- **public/PKCE vs confidential**: 전체 흐름(B안)만의 제약. 검증기 수준(A안)은 무관.
- **Jackson 3 전용**(`tools.jackson.*`) — `com.fasterxml.jackson.*` 금지(레포 불변 규약).
- **spotless(Palantir)** 는 테스트 통과와 별개 게이트 — 커밋/PR 전 `./gradlew :services:auth-server:spotlessApply`.
