package com.company.authserver.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.framework.security.jwt.ResourceServerJwtVerifier;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
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
 * <b>OIDC id_token 발급 e2e</b>(착수 문서 {@code docs/NEXT_OIDC_ID_TOKEN.md}). 라운드트립 e2e({@code
 * TokenIssuanceRoundTripTest})가 의도적으로 미룬 조각 — {@code authorization_code + PKCE} 흐름에서 {@code openid}
 * scope 를 포함해 <b>id_token 까지 발급</b>되고, 발급된 id_token 이 실 {@code /oauth2/jwks} 공개키로 검증되는지 본다.
 *
 * <pre>
 *   demo/demo 폼 로그인(우리 Authenticator 경유) → /oauth2/authorize(scope=openid profile, nonce)
 *     → 코드 교환 → { access_token, id_token } → id_token 을 AS JWKS(RS256)로 재검증 + 클레임 검증
 * </pre>
 *
 * <p><b>왜 MockMvc 로 충분한가(착수 문서 §1 정정)</b>: SS7 7.0 의 {@code JwtGenerator} 는 id_token 의 {@code
 * auth_time} 를 <i>SAS 1.x 처럼 {@code SessionInformation.getLastRequest()} 에서가 아니라</i> 인증 principal 의
 * {@code FactorGrantedAuthority#getIssuedAt()} 에서 산출한다(7.0.x {@code JwtGenerator#getAuthenticationTime}).
 * 과거 {@code openid} 코드 교환이 {@code "authenticationTime cannot be null"} 로 실패한 진짜 원인은 "MockMvc 가 세션을
 * 안 만들어 {@code SessionInformation} 이 null" 이 아니라(그 경우라면 {@code if (sessionInformation != null)} 가드에
 * 걸려 auth_time/sid 가 <i>조용히 생략</i>될 뿐 Assert 가 안 난다 → 즉 {@code SessionInformation} 은 실제로 non-null
 * 이었다), <b>커스텀 {@code FrameworkAuthenticationProvider} 가 인증 팩터를 안 붙여 principal 에 {@code
 * FactorGrantedAuthority} 가 없었던 것</b>이다. provider 가 {@code FACTOR_PASSWORD} 를 부착하도록 고쳐(표준 form-login
 * 과 동일 규약) 해결했고, 그 결과 같은 가드 블록의 {@code sid} 도 함께 채워진다(둘은 한 {@code if} 블록에서 동시 부여).
 *
 * <p>프로파일 {@code local}: H2 인메모리 + {@code LocalDemo}(demo-web public PKCE 클라이언트 = scope openid/profile,
 * consent 미요구). 서명키 회전 off.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class OidcIdTokenIssuanceTest {

    /** demo-web 에 등록된 redirect-uri 와 정확히 일치해야 한다(LocalDemo). */
    private static final String REDIRECT_URI = "http://127.0.0.1:8081/login/oauth2/code/demo-web";

    /** PathNotFound 를 null 로 관용 처리해 "클레임 부재"를 깔끔히 단언한다. */
    private static final Configuration LENIENT =
            Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);

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
    // id_token 발급 + 클레임 검증
    // =====================================================================

    @Test
    void openidCodeExchange_issues_id_token_with_valid_oidc_claims() throws Exception {
        String nonce = "n-" + base64Url(randomBytes(16));

        Issued issued = issueOpenidTokens(nonce);
        assertThat(issued.idToken()).as("openid 코드 교환은 id_token 을 포함해야 한다").isNotBlank();
        assertThat(issued.accessToken()).as("동일 교환에서 access_token 도 발급된다").isNotBlank();

        // (1) 서명(RS256) + iss + aud(=demo-web) + sub 를 실 JWKS 로 검증. id_token 의 aud 는 client_id 이므로
        //     검증기의 expectedAudience 를 켜서 aud 검증까지 통과시킨다.
        ResourceServerJwtVerifier verifier = idTokenVerifier(issuer(), "demo-web");
        ResourceServerJwtVerifier.Verified verified = verifier.verify(issued.idToken());
        assertThat(verified.userId()).as("sub = 우리 userId").isEqualTo("demo");
        assertThat(verified.roles()).as("roles 클레임(RoleClaimTokenCustomizer)").contains("ROLE_USER");

        // (2) OIDC 전용 클레임은 검증기가 노출하지 않으므로, 서명이 검증된 토큰의 payload 를 디코드해 확인.
        var payload = decodePayload(issued.idToken());

        assertThat(payload.<String>read("$.iss")).as("iss = AS issuer").isEqualTo(issuer());
        assertThat(payload.<String>read("$.sub")).isEqualTo("demo");

        // auth_time: FactorGrantedAuthority(FACTOR_PASSWORD).issuedAt 에서 산출 — null 이 아니어야 한다(핵심 수용 기준).
        Number authTime = payload.read("$.auth_time");
        assertThat(authTime).as("auth_time 은 null 이 아니어야 한다").isNotNull();
        assertThat(authTime.longValue()).as("auth_time 은 양의 epoch-second").isPositive();

        // exp: 만료 클레임 존재 + 미래.
        Number exp = payload.read("$.exp");
        assertThat(exp).as("exp 존재").isNotNull();
        assertThat(exp.longValue()).isPositive();

        // nonce: 요청 nonce 가 그대로 왕복.
        assertThat(payload.<String>read("$.nonce")).as("nonce 왕복").isEqualTo(nonce);

        // sid: auth_time 과 동일 가드 블록(if sessionInformation != null)에서 함께 부여되므로,
        //      auth_time 이 있으면 sid 도 반드시 존재한다.
        assertThat(payload.<String>read("$.sid")).as("세션 추적 sid").isNotBlank();
    }

    @Test
    void id_token_with_wrong_expected_audience_is_rejected() throws Exception {
        Issued issued = issueOpenidTokens("n-" + base64Url(randomBytes(16)));

        // 다른 client_id 를 기대 aud 로 핀하면 진짜 id_token 이라도 aud 불일치로 거부되어야 한다.
        ResourceServerJwtVerifier strictAud = idTokenVerifier(issuer(), "some-other-client");
        assertThatThrownBy(() -> strictAud.verify(issued.idToken())).isInstanceOf(JwtException.class);
    }

    // =====================================================================
    // helpers — 발급
    // =====================================================================

    private record Issued(String idToken, String accessToken) {}

    /** demo/demo 로그인 → authorize(scope=openid profile, nonce, PKCE S256) → 코드 교환 → id_token + access_token. */
    private Issued issueOpenidTokens(String nonce) throws Exception {
        String codeVerifier = base64Url(randomBytes(32));
        String codeChallenge = base64Url(sha256Ascii(codeVerifier));

        // (1) 폼 로그인 → FrameworkAuthenticationProvider(=demo 인증기) + FACTOR_PASSWORD 부착 + 세션 확보.
        MvcResult login = mockMvc.perform(formLogin("/login").user("demo").password("demo"))
                .andExpect(authenticated().withUsername("demo"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).as("로그인 세션").isNotNull();

        // (2) /oauth2/authorize — openid 포함 + nonce. demo-web 은 consent 미요구 → 즉시 code 리다이렉트.
        MvcResult authorize = mockMvc.perform(get("/oauth2/authorize")
                        .session(session)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "demo-web")
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
                        .param("client_id", "demo-web")
                        .param("code_verifier", codeVerifier))
                .andExpect(status().isOk())
                .andReturn();
        String body = token.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Issued(JsonPath.read(body, "$.id_token"), JsonPath.read(body, "$.access_token"));
    }

    // =====================================================================
    // helpers — 검증기 / 유틸
    // =====================================================================

    /** id_token 전용 검증기: aud 검증(expectedAudience = client_id)을 켠다. */
    private ResourceServerJwtVerifier idTokenVerifier(String expectedIssuer, String expectedAudience) {
        String jwksUri = "http://localhost:" + port + "/oauth2/jwks";
        return new ResourceServerJwtVerifier(
                RestClient.create(),
                expectedIssuer,
                jwksUri,
                "roles",
                expectedAudience, // ← aud 검증 활성(id_token aud = client_id)
                Duration.ofSeconds(60),
                Duration.ofMinutes(5));
    }

    private String issuer() {
        return authorizationServerSettings.getIssuer();
    }

    /** 서명이 이미 검증된 JWT 의 payload(JSON)를 디코드해 OIDC 클레임을 읽는다. */
    private static DocumentContext decodePayload(String jwt) {
        String[] parts = jwt.split("\\.");
        String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return JsonPath.using(LENIENT).parse(json);
    }

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
