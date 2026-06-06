package com.company.authserver.e2e;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * <b>스모크 시더 + DbAuthenticator 운영 인증 경로 e2e</b>(착수 문서 {@code
 * docs/_internal/planning/NEXT_KIND_AUTH_TOKEN_FLOW.md}). kind 의 마지막 한 칸 — "OAuth2 클라이언트 등록 → 토큰
 * 플로우" Done 정의를 <b>kubectl/curl 수동 절차의 테스트 등가물</b>로 닫는다.
 *
 * <p>기존 {@code OidcIdTokenIssuanceTest}/{@code TokenIssuanceRoundTripTest} 는 {@code @Profile("local")} 의
 * {@code LocalDemo}(demo/demo 데모 인증기 + 데모 클라이언트)에 기댄다. 본 시험은 그 두 가지를 모두 <b>비활성화</b>한
 * {@code !local} 프로파일(=prod 와 동일한 인증 배선)에서:
 *
 * <ol>
 *   <li>{@link com.company.authserver.config.SmokeClientSeeder}(옵트인 플래그 {@code framework.auth.seed-smoke-client})가
 *       {@code demo-web}/{@code demo-service} 를 {@code RegisteredClientRepository.save()} 로 등록했는지,
 *   <li>등록된 {@code demo-web} 으로 {@code authorization_code + PKCE} 흐름이 돌고, 폼 로그인 사용자가 {@code
 *       LocalDemo} 의 demo 가 아니라 <b>authdb {@code app_user} 의 {@code tester}</b>(= {@link
 *       com.company.authserver.user.DbAuthenticator} 가 {@code {bcrypt}} 해시로 검증)인지,
 *   <li>그 결과 {@code openid} 코드 교환이 {@code access_token}+{@code id_token} 을 발급하고 {@code sub = tester} 인지
 * </ol>
 *
 * 를 확인한다 = <b>prod 인증 경로(DbAuthenticator)가 토큰 발급까지 끝까지 도는 것</b>의 자동 증명.
 *
 * <p>프로파일 {@code smoketest}(테스트 클래스패스 {@code application-smoketest.yml}): H2 인메모리(Flyway V1~V7 →
 * {@code oauth2_*}·{@code framework_lock}·{@code app_user}+{@code tester} seed), 스모크 시더 on, 서명키 회전 off.
 * id_token {@code auth_time} 는 {@code FrameworkAuthenticationProvider} 가 부착하는 {@code
 * FactorGrantedAuthority(FACTOR_PASSWORD)} 에서 산출되므로(7.0.x {@code JwtGenerator}) MockMvc 폼 로그인으로도
 * {@code openid} 발급이 가능하다(OidcIdTokenIssuanceTest 와 동일 근거).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("smoketest")
class SmokeClientDbAuthFlowTest {

    /** demo-web 에 등록된 redirect-uri 와 정확히 일치해야 한다(SmokeClientSeeder). */
    private static final String REDIRECT_URI = "http://127.0.0.1:8081/login/oauth2/code/demo-web";

    /** authdb app_user(V7) seed 계정. */
    private static final String LOGIN_ID = "tester";

    private static final String PASSWORD = "Test1234!";

    /** PathNotFound 를 null 로 관용 처리해 "클레임 부재"를 깔끔히 단언한다. */
    private static final Configuration LENIENT =
            Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AuthorizationServerSettings authorizationServerSettings;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

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
    // (0) 시더가 클라이언트를 실제로 등록했는가 (repo.save() 경유 → JSON 정상)
    // =====================================================================

    @Test
    void smokeSeeder_registers_demo_clients_via_repository() {
        // SQL INSERT 가 아니라 repo.save() 로 등록되어야 settings JSON(@class 메타) 이 깨지지 않고 다시 읽힌다.
        // findByClientId 가 row mapper 로 역직렬화에 성공한다는 것 자체가 그 증거.
        assertThat(registeredClientRepository.findByClientId("demo-web"))
                .as("SmokeClientSeeder 가 demo-web(public+PKCE)을 등록")
                .isNotNull();
        assertThat(registeredClientRepository.findByClientId("demo-service"))
                .as("SmokeClientSeeder 가 demo-service(client_credentials)를 등록")
                .isNotNull();
    }

    // =====================================================================
    // (1) DbAuthenticator(tester) → authorization_code + PKCE → access/id_token
    // =====================================================================

    @Test
    void dbAuthenticator_tester_completes_authorization_code_pkce_and_issues_tokens() throws Exception {
        String nonce = "n-" + base64Url(randomBytes(16));
        Issued issued = issueOpenidTokens(nonce);

        assertThat(issued.accessToken()).as("access_token 발급").isNotBlank();
        assertThat(issued.idToken()).as("openid 코드 교환은 id_token 포함").isNotBlank();

        // 발급된 id_token 을 실 /oauth2/jwks(RS256)로 검증 — 발급 키/클레임이 검증 측과 손 안 대고 맞물리는지.
        ResourceServerJwtVerifier verifier = idTokenVerifier(issuer(), "demo-web");
        ResourceServerJwtVerifier.Verified verified = verifier.verify(issued.idToken());

        // 핵심: sub 가 LocalDemo 의 "demo" 가 아니라 authdb app_user 의 "tester" — DbAuthenticator 경로가 돌았다는 증거.
        assertThat(verified.userId())
                .as("sub = authdb app_user.login_id(tester)")
                .isEqualTo(LOGIN_ID);
        assertThat(verified.roles())
                .as("roles = app_user.role(USER) → ROLE_USER")
                .contains("ROLE_USER");

        // OIDC 클레임(서명 검증된 토큰의 payload 디코드).
        DocumentContext payload = decodePayload(issued.idToken());
        assertThat(payload.<String>read("$.iss")).as("iss = AS issuer").isEqualTo(issuer());
        assertThat(payload.<String>read("$.sub")).isEqualTo(LOGIN_ID);
        assertThat(payload.<String>read("$.nonce")).as("nonce 왕복").isEqualTo(nonce);

        Number authTime = payload.read("$.auth_time");
        assertThat(authTime)
                .as("auth_time(FACTOR_PASSWORD.issuedAt) 은 null 이 아님")
                .isNotNull();
        assertThat(authTime.longValue()).as("auth_time 은 양의 epoch-second").isPositive();
    }

    // =====================================================================
    // (2) demo-service(confidential) client_credentials — 시크릿 해시 검증 + 발급
    // =====================================================================

    @Test
    void demoService_client_credentials_issues_access_token() throws Exception {
        // 시더가 encoder.encode("demo-secret") 로 저장한 client_secret 이 client_secret_basic 으로 검증되어야 한다.
        String basic = Base64.getEncoder().encodeToString("demo-service:demo-secret".getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                        .param("grant_type", "client_credentials")
                        .param("scope", "api.read"))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken =
                JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.access_token");
        assertThat(accessToken).as("client_credentials access_token").isNotBlank();
    }

    // =====================================================================
    // helpers — 발급
    // =====================================================================

    private record Issued(String idToken, String accessToken) {}

    /** tester 폼 로그인(DbAuthenticator 경유) → authorize(scope=openid profile, nonce, PKCE S256) → 코드 교환. */
    private Issued issueOpenidTokens(String nonce) throws Exception {
        String codeVerifier = base64Url(randomBytes(32));
        String codeChallenge = base64Url(sha256Ascii(codeVerifier));

        // (1) 폼 로그인 — FrameworkAuthenticationProvider → DbAuthenticator(authdb app_user) + FACTOR_PASSWORD + 세션.
        MvcResult login = mockMvc.perform(formLogin("/login").user(LOGIN_ID).password(PASSWORD))
                .andExpect(authenticated().withUsername(LOGIN_ID))
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
                expectedAudience, // id_token aud = client_id
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
