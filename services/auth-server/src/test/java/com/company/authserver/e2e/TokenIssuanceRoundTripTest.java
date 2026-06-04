package com.company.authserver.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.framework.security.jwt.DownstreamTokenAuthenticator;
import com.company.framework.security.jwt.JwtProperties;
import com.company.framework.security.jwt.JwtProvider;
import com.company.framework.security.jwt.ResourceServerJwtVerifier;
import com.company.framework.security.jwt.TokenIssuerKind;
import com.jayway.jsonpath.JsonPath;
import io.jsonwebtoken.JwtException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
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
 * <b>토큰 발급 라운드트립 e2e</b>(다음 후보 §, HANDOFF_SUMMARY). 실제로 기동한 Authorization Server(OP)가
 * 두 그랜트로 발급한 <b>진짜 RS256 토큰</b>을, 실제 {@code /oauth2/jwks} 로 가져온 공개키로 <b>다운스트림 무신뢰
 * (zero-trust) 재검증</b>까지 통과시키는 전 구간 시험이다.
 *
 * <pre>
 *   [발급]  demo-service / client_credentials      ─┐
 *           demo-web / authorization_code + PKCE   ─┤→ AS(/oauth2/token, RS256)
 *   [전파]  /oauth2/jwks (실 HTTP, 랜덤 포트)         ─┘
 *   [검증]  ResourceServerJwtVerifier (= user-service zero-trust 검증기, framework-security)
 *           DownstreamTokenAuthenticator (= 다운스트림 진입점: iss 분기 + Authentication 생성)
 * </pre>
 *
 * <p><b>기존 단위테스트와의 차이</b>: {@code GatewayDualIssuerTest}/{@code DownstreamDualIssuerTest} 는 손수 만든
 * RSA 키 + mock JWKS 로 <i>검증기 로직</i>만 본다. 본 시험은 토큰을 <i>실제 발급 기계</i>(SAS + {@code
 * JdbcRotatingJwkSource} + {@code RoleClaimTokenCustomizer})에서 받고, JWKS 도 실제 엔드포인트에서 HTTP 로
 * 가져온다 → 발급 측 키/클레임 형식과 검증 측이 한 번도 손으로 맞춘 적 없는 상태에서 맞물리는지 본다.
 *
 * <p><b>게이트웨이 leg</b>: 엣지 정본 검증자 {@code GatewayJwksTokenVerifier}(WebFlux)는 {@link
 * ResourceServerJwtVerifier} 의 <b>동일 알고리즘 쌍둥이</b>(둘 다 jjwt + JWKS/kid 캐시 + iss/exp). 게이트웨이는
 * 별도 배포 서비스라 auth-server 테스트 클래스패스로 끌어오지 않는다(서비스 간 의존 금지 — archtest 경계). 게이트웨이
 * 측 leg 은 실 RSA/JWKS 로 {@code services/gateway} 의 {@code GatewayDualIssuerTest} 가 커버한다.
 *
 * <p>프로파일 {@code local}: H2 인메모리 + {@code LocalDemo} 가 demo 클라이언트 2종 + demo/demo 인증기를 등록한다.
 * 서명키 회전은 기본 off(부트스트랩 ACTIVE 키 1개로 충분).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class TokenIssuanceRoundTripTest {

    /** demo-web 에 등록된 redirect-uri 와 정확히 일치해야 한다(LocalDemo). */
    private static final String REDIRECT_URI = "http://127.0.0.1:8081/login/oauth2/code/demo-web";

    /** 자체 JWT(HMAC) 경로용 더미 비밀키(≥32바이트). AS 토큰엔 쓰이지 않지만 JwtProvider 생성에 필요. */
    private static final String INTERNAL_SECRET = "0123456789012345678901234567890123456789";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AuthorizationServerSettings authorizationServerSettings;

    /** 임베디드 서버가 실제로 바인딩한 포트(issuer 의 :9000 은 논리 식별자일 뿐 — 실 JWKS 는 이 포트). */
    @Value("${local.server.port}")
    private int port;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // @AutoConfigureMockMvc 슬라이스 대신 명시적으로 보안 필터 체인을 적용(버전 간 안정 + 발급 흐름에 보안 필수).
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // =====================================================================
    // leg 1 — client_credentials (demo-service, confidential)
    // =====================================================================

    @Test
    void clientCredentials_token_is_reverified_downstream_zero_trust() throws Exception {
        String accessToken = issueClientCredentialsToken();

        ResourceServerJwtVerifier verifier = downstreamVerifier(issuer());
        ResourceServerJwtVerifier.Verified verified = verifier.verify(accessToken);

        // client_credentials: sub = client_id. (사용자 위임이 아니므로 userId 자리에 클라이언트 식별자가 온다.)
        assertThat(verified.userId()).isEqualTo("demo-service");

        // user-service 진입점도 동일 결과 + AS 발급자로 분기.
        DownstreamTokenAuthenticator authenticator = new DownstreamTokenAuthenticator(internalProvider(), verifier);
        assertThat(authenticator.kindOf(accessToken)).isEqualTo(TokenIssuerKind.AUTHORIZATION_SERVER);

        DownstreamTokenAuthenticator.Authenticated authed = authenticator.tryAuthenticate(accessToken);
        assertThat(authed).isNotNull();
        assertThat(authed.kind()).isEqualTo(TokenIssuerKind.AUTHORIZATION_SERVER);
        assertThat(authed.authentication().getName()).isEqualTo("demo-service");
    }

    // =====================================================================
    // leg 2 — authorization_code + PKCE (demo-web, public)
    // =====================================================================

    @Test
    void authorizationCodePkce_token_is_reverified_downstream_zero_trust() throws Exception {
        Tokens tokens = issueAuthorizationCodePkceTokens();

        ResourceServerJwtVerifier verifier = downstreamVerifier(issuer());
        ResourceServerJwtVerifier.Verified verified = verifier.verify(tokens.accessToken());

        // 사용자 위임: sub = userId(demo), roles 클레임 = RoleClaimTokenCustomizer 가 권한을 그대로 실음.
        assertThat(verified.userId()).isEqualTo("demo");
        assertThat(verified.roles()).contains("ROLE_USER");

        DownstreamTokenAuthenticator authenticator = new DownstreamTokenAuthenticator(internalProvider(), verifier);
        DownstreamTokenAuthenticator.Authenticated authed = authenticator.tryAuthenticate(tokens.accessToken());
        assertThat(authed).isNotNull();
        assertThat(authed.authentication().getName()).isEqualTo("demo");
        assertThat(authed.authentication().getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_USER");

        // id_token 도 같은 서명키로 검증 가능(라운드트립 일관성 — aud 검증은 생략 설정이므로 sub 만 본다).
        assertThat(verifier.verify(tokens.idToken()).userId()).isEqualTo("demo");
    }

    // =====================================================================
    // 음성(negative) — zero-trust 가 실제로 막는가
    // =====================================================================

    @Test
    void genuine_token_with_wrong_expected_issuer_is_rejected() throws Exception {
        String accessToken = issueClientCredentialsToken();

        // issuer 핀이 다르면(= 다른 OP 를 신뢰하도록 설정) 진짜 토큰이라도 거부되어야 한다.
        ResourceServerJwtVerifier strict = downstreamVerifier("https://evil.example.com");
        assertThatThrownBy(() -> strict.verify(accessToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void tampered_token_is_rejected_downstream() throws Exception {
        String tampered = tamperSignature(issueClientCredentialsToken());

        ResourceServerJwtVerifier verifier = downstreamVerifier(issuer());
        assertThatThrownBy(() -> verifier.verify(tampered)).isInstanceOf(JwtException.class);

        // 진입점은 검증 실패를 null 로 관용 처리 → 인가 계층이 401 을 낸다(JwtAuthenticationFilter 규약).
        DownstreamTokenAuthenticator authenticator = new DownstreamTokenAuthenticator(internalProvider(), verifier);
        assertThat(authenticator.tryAuthenticate(tampered)).isNull();
    }

    // =====================================================================
    // helpers — 발급
    // =====================================================================

    /** demo-service 로 client_credentials 발급 → access_token(RS256 JWT) 반환. */
    private String issueClientCredentialsToken() throws Exception {
        String basic = Base64.getEncoder()
                .encodeToString("demo-service:demo-secret".getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                        .param("grant_type", "client_credentials")
                        .param("scope", "api.read"))
                .andExpect(status().isOk())
                .andReturn();
        return readJson(result, "$.access_token");
    }

    /** demo/demo 로그인(우리 Authenticator 경유) → authorize(PKCE) → 코드 교환 → access/id 토큰. */
    private Tokens issueAuthorizationCodePkceTokens() throws Exception {
        // PKCE(RFC 7636, S256): challenge = BASE64URL(SHA-256(ASCII(verifier))).
        byte[] verifierBytes = new byte[32];
        new SecureRandom().nextBytes(verifierBytes);
        String codeVerifier = base64Url(verifierBytes);
        String codeChallenge = base64Url(sha256Ascii(codeVerifier));

        // (1) demo/demo 폼 로그인 → FrameworkAuthenticationProvider(=demo 인증기) 경유 + 세션 확보.
        MvcResult login = mockMvc.perform(formLogin("/login").user("demo").password("demo"))
                .andExpect(authenticated().withUsername("demo"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).as("로그인 세션이 만들어져야 한다").isNotNull();

        // (2) /oauth2/authorize → demo-web 은 동의(consent) 미요구 → redirect_uri?code=... 로 즉시 리다이렉트.
        MvcResult authorize = mockMvc.perform(get("/oauth2/authorize")
                        .session(session)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "demo-web")
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", "openid profile")
                        .queryParam("code_challenge", codeChallenge)
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = authorize.getResponse().getRedirectedUrl();
        assertThat(location).as("코드와 함께 redirect_uri 로 리다이렉트되어야 한다").startsWith(REDIRECT_URI);
        String code = UriComponentsBuilder.fromUriString(location)
                .build()
                .getQueryParams()
                .getFirst("code");
        assertThat(code).as("authorization code").isNotBlank();

        // (3) 코드 교환 — public 클라이언트(secret 없음) + PKCE: client_id + code_verifier 제시.
        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("client_id", "demo-web")
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andReturn();
        return new Tokens(readJson(token, "$.access_token"), readJson(token, "$.id_token"));
    }

    // =====================================================================
    // helpers — 검증기 / 유틸
    // =====================================================================

    /**
     * user-service 가 쓰는 것과 동일한 zero-trust 검증기. issuer(iss 클레임)는 논리 식별자(:9000),
     * jwks-uri 는 임베디드 서버가 실제로 바인딩한 랜덤 포트 — 운영의 "iss 는 ID, jwks-uri 는 네트워크 위치" 분리를 그대로 재현.
     */
    private ResourceServerJwtVerifier downstreamVerifier(String expectedIssuer) {
        String jwksUri = "http://localhost:" + port + "/oauth2/jwks";
        return new ResourceServerJwtVerifier(
                RestClient.create(),
                expectedIssuer,
                jwksUri,
                "roles",
                null, // expectedAudience 비움 → aud 검증 생략(데모 클라이언트 aud 미고정)
                Duration.ofSeconds(60),
                Duration.ofMinutes(5));
    }

    private JwtProvider internalProvider() {
        return new JwtProvider(new JwtProperties(INTERNAL_SECRET, 1800, 1209600));
    }

    private String issuer() {
        return authorizationServerSettings.getIssuer();
    }

    private static String readJson(MvcResult result, String jsonPath) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), jsonPath);
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

    /**
     * 서명 세그먼트의 <b>중간</b> 문자 1글자를 바꿔 서명을 깨뜨린다(RS256 검증 실패 유도).
     *
     * <p>주의: 마지막 문자는 base64url 의 trailing-bit 특성상 일부 값 변경이 디코딩 바이트를 안 바꿔 변조가 무효가
     * 될 수 있다. 중간 문자는 온전한 바이트(6비트)를 인코딩하므로 변경이 반드시 서명 바이트를 바꾼다.
     */
    private static String tamperSignature(String jwt) {
        int lastDot = jwt.lastIndexOf('.');
        String head = jwt.substring(0, lastDot + 1);
        String sig = jwt.substring(lastDot + 1);
        int mid = sig.length() / 2;
        char c = sig.charAt(mid);
        char repl = (c == 'a') ? 'b' : 'a'; // 'a'·'b' 모두 base64url 유효 문자, c 와 반드시 다름
        return head + sig.substring(0, mid) + repl + sig.substring(mid + 1);
    }

    private record Tokens(String accessToken, String idToken) {}
}
