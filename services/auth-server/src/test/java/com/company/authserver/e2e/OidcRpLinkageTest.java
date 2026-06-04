package com.company.authserver.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.framework.core.error.BusinessException;
import com.company.framework.oauthclient.config.OAuthClientProperties;
import com.company.framework.oauthclient.oidc.IdTokenVerifier;
import com.company.framework.oauthclient.oidc.JwksKeyResolver;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * <b>OIDC 풀루프 연계 e2e</b>(착수 문서 {@code docs/NEXT_RP_IDTOKEN_LINK.md}, A안). 우리 AS({@code
 * services/auth-server})가 발급한 id_token 을, 우리 <b>RP 스택</b>({@code framework-oauth-client} 의 {@link
 * IdTokenVerifier} + {@link JwksKeyResolver})으로 그대로 검증해 <b>발급(AS)↔검증(RP) 양끝이 모두 우리 코드</b>임을
 * 입증한다. OIDC 전 구간(발급↔검증)을 완결하는 마지막 조각이다.
 *
 * <pre>
 *   demo/demo 폼 로그인 → /oauth2/authorize(scope=openid profile, nonce, PKCE S256)
 *     → 코드 교환 → id_token
 *     → RP IdTokenVerifier(실 JwksKeyResolver → 라이브 /oauth2/jwks)로 검증 + 클레임 단언
 * </pre>
 *
 * <p><b>직전 발급 e2e({@code OidcIdTokenIssuanceTest})와의 차이</b>: 발급 하네스는 동일하나, 검증을 <b>AS 측 {@code
 * ResourceServerJwtVerifier}</b>(예외 {@code io.jsonwebtoken.JwtException})가 아니라 <b>RP 측 {@link
 * IdTokenVerifier}</b>(예외 {@link BusinessException}{@code (UNAUTHORIZED)})로 수행한다. 즉 이 테스트는 발급기와
 * 검증기가 <b>서로 다른 우리 모듈</b>임에도 호환됨을 보장한다.
 *
 * <p><b>왜 A안(검증기 수준)인가</b>: id_token 자체는 클라이언트 인증방식과 무관하다. RP {@code
 * OAuthClient.exchangeCodeForTokens} 는 client_secret(PKCE 미지원)이라 AS {@code demo-web}(public+PKCE)과
 * 전체 흐름이 어긋나지만(B안=confidential {@code demo-rp} 등록 필요), 검증기 수준 연계는 {@code demo-web} 으로 발급한
 * id_token 을 그대로 검증하면 되므로 서비스 간 의존 없이 자기완결로 양끝을 닫는다.
 *
 * <p>프로파일 {@code local}: H2 인메모리 + {@code LocalDemo}(demo-web public PKCE = scope openid/profile, consent
 * 미요구). 서명키 회전 off.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class OidcRpLinkageTest {

    /** demo-web 에 등록된 redirect-uri 와 정확히 일치해야 한다(LocalDemo). */
    private static final String REDIRECT_URI = "http://127.0.0.1:8081/login/oauth2/code/demo-web";

    /** id_token 의 aud = client_id 이므로 RP Provider.clientId 는 발급에 쓴 client 와 동일해야 한다. */
    private static final String CLIENT_ID = "demo-web";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AuthorizationServerSettings authorizationServerSettings;

    @Value("${local.server.port}")
    private int port;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // =====================================================================
    // 양성: AS 발급 id_token 이 RP 검증기로 통과 + 클레임 왕복
    // =====================================================================

    @Test
    void as_issued_id_token_is_verified_by_rp_id_token_verifier() throws Exception {
        String nonce = "n-" + base64Url(randomBytes(16));
        String idToken = issueIdToken(nonce);
        assertThat(idToken).as("openid 코드 교환은 id_token 을 포함해야 한다").isNotBlank();

        IdTokenVerifier verifier = rpVerifier();
        OAuthClientProperties.Provider provider = rpProvider(CLIENT_ID, issuer());

        Map<String, Object> claims = verifier.verify(provider, idToken, nonce);

        assertThat(claims.get("sub")).as("sub = 우리 userId").isEqualTo("demo");
        assertThat(claims.get("iss")).as("iss = AS issuer").isEqualTo(issuer());

        // roles: RoleClaimTokenCustomizer 가 부여(인증 팩터 제외) — RP 검증기는 raw 클레임을 그대로 노출하므로
        //        JSON 배열은 Iterable 로 역직렬화된다. asInstanceOf 로 원소 타입을 String 으로 좁혀 단언한다
        //        (와일드카드 캐스팅 시 AssertJ contains 의 element 타입 추론이 막혀 컴파일 안 됨).
        assertThat(claims.get("roles"))
                .as("roles 에 ROLE_USER 포함")
                .asInstanceOf(InstanceOfAssertFactories.iterable(String.class))
                .contains("ROLE_USER");

        // nonce: authorize 때 보낸 값이 그대로 왕복(RP 가 재생/위조 차단에 사용).
        assertThat(claims.get("nonce")).as("nonce 왕복").isEqualTo(nonce);

        // auth_time: FactorGrantedAuthority(FACTOR_PASSWORD).issuedAt 산출 — null 이 아니어야 한다.
        Object authTime = claims.get("auth_time");
        assertThat(authTime).as("auth_time 은 null 이 아니어야 한다").isNotNull();
        assertThat(((Number) authTime).longValue())
                .as("auth_time 은 양의 epoch-second")
                .isPositive();
    }

    /** 같은 id_token 을 같은 검증기로 2회 검증 — 두번째는 JWKS 캐시 히트(JwksKeyResolver TTL 캐시 동작 확인). */
    @Test
    void second_verification_reuses_cached_jwks() throws Exception {
        String nonce = "n-" + base64Url(randomBytes(16));
        String idToken = issueIdToken(nonce);

        IdTokenVerifier verifier = rpVerifier();
        OAuthClientProperties.Provider provider = rpProvider(CLIENT_ID, issuer());

        Map<String, Object> first = verifier.verify(provider, idToken, nonce);
        Map<String, Object> second = verifier.verify(provider, idToken, nonce); // 캐시 히트 경로

        assertThat(first.get("sub")).isEqualTo("demo");
        assertThat(second.get("sub")).isEqualTo("demo");
    }

    // =====================================================================
    // 음성: 전부 BusinessException(UNAUTHORIZED) — AS 측 JwtException 과 예외 타입 다름(주의)
    // =====================================================================

    @Test
    void wrong_issuer_pin_is_rejected_as_business_exception() throws Exception {
        String nonce = "n-" + base64Url(randomBytes(16));
        String idToken = issueIdToken(nonce);

        // 서명은 실 JWKS 로 통과하지만 issuer 핀이 어긋나 거부되어야 한다.
        OAuthClientProperties.Provider wrongIssuer = rpProvider(CLIENT_ID, "https://wrong-issuer.example.com");

        assertThatThrownBy(() -> rpVerifier().verify(wrongIssuer, idToken, nonce))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void wrong_audience_clientId_is_rejected_as_business_exception() throws Exception {
        String nonce = "n-" + base64Url(randomBytes(16));
        String idToken = issueIdToken(nonce);

        // id_token aud(=demo-web)에 없는 client-id 를 핀하면 aud 불일치로 거부.
        OAuthClientProperties.Provider wrongAud = rpProvider("some-other-client", issuer());

        assertThatThrownBy(() -> rpVerifier().verify(wrongAud, idToken, nonce)).isInstanceOf(BusinessException.class);
    }

    @Test
    void wrong_nonce_is_rejected_as_business_exception() throws Exception {
        String nonce = "n-" + base64Url(randomBytes(16));
        String idToken = issueIdToken(nonce);

        OAuthClientProperties.Provider provider = rpProvider(CLIENT_ID, issuer());

        // expectedNonce 가 토큰의 nonce 와 다르면 거부(재생/위조 차단).
        assertThatThrownBy(() -> rpVerifier().verify(provider, idToken, "n-some-other-nonce"))
                .isInstanceOf(BusinessException.class);
    }

    // =====================================================================
    // helpers — RP 검증기/설정
    // =====================================================================

    /** 실 JwksKeyResolver(라이브 /oauth2/jwks) + IdTokenVerifier. RestClient 는 실 HTTP(임베디드 서버 호출). */
    private IdTokenVerifier rpVerifier() {
        JwksKeyResolver resolver = new JwksKeyResolver(RestClient.create(), Duration.ofMinutes(5));
        return new IdTokenVerifier(resolver);
    }

    /** RP Provider 설정: clientId(=aud 검증 대상) · OIDC on · issuer/jwksUri/nonce. */
    private OAuthClientProperties.Provider rpProvider(String clientId, String expectedIssuer) {
        OAuthClientProperties.Provider p = new OAuthClientProperties.Provider();
        p.setClientId(clientId); // aud ⊇ client-id 통과(id_token aud = client_id)
        p.getOidc().setEnabled(true);
        p.getOidc().setIssuer(expectedIssuer); // 런타임 issuer 핀(하드코딩 금지)
        p.getOidc().setJwksUri("http://localhost:" + port + "/oauth2/jwks");
        p.getOidc().setNonce(true);
        return p;
    }

    private String issuer() {
        return authorizationServerSettings.getIssuer();
    }

    // =====================================================================
    // helpers — 발급 (OidcIdTokenIssuanceTest 하네스 재사용)
    // =====================================================================

    /** demo/demo 로그인 → authorize(scope=openid profile, nonce, PKCE S256) → 코드 교환 → id_token. */
    private String issueIdToken(String nonce) throws Exception {
        String codeVerifier = base64Url(randomBytes(32));
        String codeChallenge = base64Url(sha256Ascii(codeVerifier));

        // (1) 폼 로그인 → FrameworkAuthenticationProvider + FACTOR_PASSWORD 부착 + 세션 확보.
        MvcResult login = mockMvc.perform(formLogin("/login").user("demo").password("demo"))
                .andExpect(authenticated().withUsername("demo"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).as("로그인 세션").isNotNull();

        // (2) /oauth2/authorize — openid + nonce. demo-web 은 consent 미요구 → 즉시 code 리다이렉트.
        MvcResult authorize = mockMvc.perform(get("/oauth2/authorize")
                        .session(session)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", "openid profile")
                        .queryParam("nonce", nonce)
                        .queryParam("code_challenge", codeChallenge)
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = authorize.getResponse().getRedirectedUrl();
        assertThat(location).as("code 와 함께 redirect_uri 로 리다이렉트").startsWith(REDIRECT_URI);
        String code = UriComponentsBuilder.fromUriString(location)
                .build()
                .getQueryParams()
                .getFirst("code");
        assertThat(code).as("authorization code").isNotBlank();

        // (3) 코드 교환 — public + PKCE: client_id + code_verifier.
        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andReturn();
        String body = token.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.id_token");
    }

    // =====================================================================
    // helpers — 유틸
    // =====================================================================

    private static byte[] randomBytes(int len) {
        byte[] b = new byte[len];
        new SecureRandom().nextBytes(b);
        return b;
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] sha256Ascii(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
