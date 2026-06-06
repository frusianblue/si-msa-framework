package com.company.authserver.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.company.framework.core.error.BusinessException;
import com.company.framework.oauthclient.config.OAuthClientProperties;
import com.company.framework.oauthclient.core.OAuthClient;
import com.company.framework.oauthclient.core.OAuthUserInfo;
import com.company.framework.oauthclient.core.OAuthUserResolver;
import com.company.framework.oauthclient.core.ProviderRegistry;
import com.company.framework.oauthclient.oidc.IdTokenVerifier;
import com.company.framework.oauthclient.oidc.JwksKeyResolver;
import com.company.framework.oauthclient.oidc.OidcDiscoveryClient;
import com.company.framework.oauthclient.oidc.OidcMetadataResolver;
import com.company.framework.oauthclient.store.InMemoryOAuthStateStore;
import com.company.framework.oauthclient.store.OAuthStateStore;
import com.company.framework.oauthclient.token.OAuthTokenIssuer;
import com.company.framework.oauthclient.web.OAuthLoginService;
import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.TokenResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
 * <b>OIDC 전체 콜백 흐름 e2e</b>(착수 문서 {@code docs/_internal/planning/NEXT_RP_IDTOKEN_LINK.md}, <b>B안</b>).
 * 우리 RP 오케스트레이션({@code framework-oauth-client} 의 {@link OAuthLoginService})이 confidential 클라이언트({@code
 * demo-rp}, {@code client_secret_post})로 우리 AS({@code services/auth-server})와 <b>실제 authorize→code→토큰교환→
 * id_token 검증→userinfo→자체 토큰</b> 전 구간을 돌아 완결됨을 입증한다.
 *
 * <p><b>A안({@code OidcRpLinkageTest})과의 차이</b>: A안은 발급된 id_token 을 <b>검증기({@link IdTokenVerifier})
 * 수준</b>에서만 검증했다(public {@code demo-web} 사용). B안은 RP {@link OAuthClient#exchangeCodeForTokens} 가
 * {@code client_secret_post} 로 코드를 교환하는 <b>전체 콜백 흐름</b>을 탄다 — 그래서 PKCE 가 아닌 confidential
 * {@code demo-rp} 등록이 전제다({@link com.company.authserver.config.LocalDemo} / {@code SmokeClientSeeder}).
 *
 * <pre>
 *   loginService.authorizeUrl("demo-rp")   // state/nonce 발급·저장 + (discovery 보충) + 인가 URL
 *     → demo/demo 폼 로그인 → /oauth2/authorize(confidential, PKCE 없음) → code 리다이렉트
 *     → loginService.callback("demo-rp", code, state)
 *         → OAuthClient.exchangeCodeForTokens(client_secret_post)   ← 실 HTTP, 임베디드 AS
 *         → IdTokenVerifier.verify(iss/aud/nonce/exp/sub)           ← 실 JWKS
 *         → OAuthUserResolver.resolve → OAuthTokenIssuer.issue → TokenResponse
 * </pre>
 *
 * <p><b>discovery/userinfo 결합 차단(함정 회피)</b>: id_token 의 {@code iss} 는 설정 issuer({@code
 * AUTH_SERVER_ISSUER}, 기본 {@code http://localhost:9000})이고 테스트 서버는 RANDOM_PORT 로 뜬다. issuer 만 두면
 * {@link OidcMetadataResolver} 가 {@code http://localhost:9000/.well-known/...}(죽은 포트)로 discovery 를 치고,
 * 라이브로 돌려도 discovery 문서의 엔드포인트는 issuer-host(죽은 9000) 기준이라 빈 칸을 죽은 URL 로 back-fill 한다.
 * 그래서 authorization/token/jwks 를 <b>라이브 포트로 명시</b>하고 discovery 는 <b>no-op</b> 으로 끈다. 또한
 * {@code userInfoUri} 를 <b>설정하지 않아</b> 콜백이 검증된 id_token 클레임만으로 신원을 구성하게 한다(AS {@code
 * /userinfo} resource-server 결합 제거 — id_token 이 sub·roles·nonce·auth_time 을 모두 담는다). {@code oidc.issuer}
 * 는 런타임의 {@link AuthorizationServerSettings#getIssuer()} 로 핀(비우면 {@link IdTokenVerifier} 가 iss 검사 스킵).
 *
 * <p>프로파일 {@code local}: H2 인메모리 + {@code LocalDemo}(demo-rp confidential, consent 미요구). MockMvc 로 민
 * authorization code 는 임베디드 AS 와 동일 컨텍스트의 {@code JdbcOAuth2AuthorizationService}(공유 H2)에 저장되므로,
 * 실 HTTP 토큰 엔드포인트 호출(OAuthClient)에서 그대로 조회된다(전송 방식이 달라도 같은 컨텍스트/스토어).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
class OidcRpFullCallbackTest {

    /** demo-rp 에 등록된 redirect-uri 와 정확히 일치해야 한다(LocalDemo). RP Provider.redirectUri 와도 동일. */
    private static final String REDIRECT_URI = "http://127.0.0.1:8082/api/v1/auth/oauth/demo-rp/callback";

    private static final String PROVIDER_ID = "demo-rp";
    private static final String CLIENT_ID = "demo-rp";
    private static final String CLIENT_SECRET = "demo-rp-secret";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AuthorizationServerSettings authorizationServerSettings;

    @Value("${local.server.port}")
    private int port;

    private MockMvc mockMvc;

    /** 콜백이 OAuthUserResolver 로 넘긴 외부 신원(검증된 id_token 클레임 기반)을 포착해 단언에 쓴다. */
    private final AtomicReference<OAuthUserInfo> captured = new AtomicReference<>();

    private OAuthStateStore stateStore;
    private OAuthLoginService loginService;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        this.captured.set(null);
        this.stateStore = new InMemoryOAuthStateStore();
        this.loginService = newLoginService(rpProvider(CLIENT_SECRET), stateStore, capturingResolver());
    }

    // =====================================================================
    // 양성: 전체 콜백 흐름 완주 → 자체 TokenResponse + 검증된 id_token 클레임 왕복
    // =====================================================================

    @Test
    void full_callback_flow_with_confidential_demo_rp_issues_token() throws Exception {
        // (1) authorize URL 발급 — state/nonce 저장 + discovery 보충(라이브).
        String authorizeUrl = loginService.authorizeUrl(PROVIDER_ID);
        String state = queryParam(authorizeUrl, "state");
        String nonce = queryParam(authorizeUrl, "nonce");
        assertThat(state).as("authorize URL 에 state").isNotBlank();
        assertThat(nonce).as("OIDC 면 nonce 발급").isNotBlank();
        assertThat(authorizeUrl).as("인가 엔드포인트는 라이브 AS").startsWith("http://localhost:" + port + "/oauth2/authorize");

        // (2) demo/demo 폼 로그인 → /oauth2/authorize(confidential, PKCE 없음) → code.
        String code = obtainAuthorizationCode(state, nonce);

        // (3) 콜백 — 실 HTTP client_secret_post 토큰교환 + id_token 검증 + resolve + issue.
        TokenResponse response = loginService.callback(PROVIDER_ID, code, state);

        assertThat(response).as("콜백은 자체 토큰을 발급").isNotNull();
        assertThat(response.accessToken()).as("access token 발급").isNotBlank();
        assertThat(response.roles()).as("roles 매핑").contains("ROLE_USER");

        // 콜백이 넘긴 외부 신원 = 검증된 id_token 클레임. 양끝(AS 발급 ↔ RP 검증)이 모두 우리 코드임을 입증.
        OAuthUserInfo userInfo = captured.get();
        assertThat(userInfo).as("resolver 가 신원을 받음").isNotNull();
        assertThat(userInfo.provider()).isEqualTo(PROVIDER_ID);
        assertThat(userInfo.providerId()).as("sub = 우리 userId").isEqualTo("demo");

        Map<String, Object> claims = userInfo.attributes();
        assertThat(claims.get("iss")).as("iss = AS issuer(런타임 핀)").isEqualTo(issuer());
        assertThat(claims.get("nonce")).as("nonce 왕복(authorize↔id_token)").isEqualTo(nonce);
        assertThat(claims.get("roles"))
                .as("roles 클레임에 ROLE_USER 포함(RoleClaimTokenCustomizer, 인증 팩터 제외)")
                .asInstanceOf(InstanceOfAssertFactories.iterable(String.class))
                .contains("ROLE_USER");
        assertThat(claims.get("auth_time")).as("auth_time 존재").isNotNull();
    }

    // =====================================================================
    // 음성: 전부 BusinessException(UNAUTHORIZED)
    // =====================================================================

    /** 저장되지 않은 state → state 소비 실패(만료/위조/재사용). */
    @Test
    void callback_with_unknown_state_is_rejected() {
        assertThatThrownBy(() -> loginService.callback(PROVIDER_ID, "any-code", "not-a-saved-state"))
                .isInstanceOf(BusinessException.class);
    }

    /** state 에 묶인 공급자와 콜백 공급자 불일치 → 거부(code 도달 전 차단). */
    @Test
    void callback_with_provider_mismatch_is_rejected() {
        String authorizeUrl = loginService.authorizeUrl(PROVIDER_ID); // state 를 demo-rp 로 저장
        String state = queryParam(authorizeUrl, "state");

        assertThatThrownBy(() -> loginService.callback("some-other-provider", "any-code", state))
                .isInstanceOf(BusinessException.class);
    }

    /** 잘못된 client_secret → 토큰 엔드포인트가 invalid_client 거부 → 교환 실패. */
    @Test
    void callback_with_wrong_client_secret_is_rejected() throws Exception {
        OAuthStateStore store = new InMemoryOAuthStateStore();
        OAuthLoginService wrongSecret = newLoginService(rpProvider("wrong-secret"), store, capturingResolver());

        String authorizeUrl = wrongSecret.authorizeUrl(PROVIDER_ID);
        String state = queryParam(authorizeUrl, "state");
        String nonce = queryParam(authorizeUrl, "nonce");
        String code = obtainAuthorizationCode(state, nonce); // code 자체는 정상 발급

        assertThatThrownBy(() -> wrongSecret.callback(PROVIDER_ID, code, state)).isInstanceOf(BusinessException.class);
    }

    // =====================================================================
    // helpers — AS 측 브라우저 흐름(로그인 + authorize → code)
    // =====================================================================

    /** demo/demo 폼 로그인 후 /oauth2/authorize(confidential, PKCE 없음)로 authorization code 를 받는다. */
    private String obtainAuthorizationCode(String state, String nonce) throws Exception {
        MvcResult login = mockMvc.perform(formLogin("/login").user("demo").password("demo"))
                .andExpect(authenticated().withUsername("demo"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).as("로그인 세션").isNotNull();

        MvcResult authorize = mockMvc.perform(get("/oauth2/authorize")
                        .session(session)
                        .queryParam("response_type", "code")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("redirect_uri", REDIRECT_URI)
                        .queryParam("scope", "openid profile")
                        .queryParam("state", state)
                        .queryParam("nonce", nonce))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String location = authorize.getResponse().getRedirectedUrl();
        assertThat(location).as("code 와 함께 redirect_uri 로 리다이렉트").startsWith(REDIRECT_URI);
        String code = UriComponentsBuilder.fromUriString(location)
                .build()
                .getQueryParams()
                .getFirst("code");
        assertThat(code).as("authorization code").isNotBlank();
        return code;
    }

    // =====================================================================
    // helpers — RP 스택 조립
    // =====================================================================

    /** RP {@link OAuthLoginService} 를 실제 컴포넌트로 조립한다(라운드트립이 framework-security 검증기를 쓰는 것과 동일 패턴). */
    private OAuthLoginService newLoginService(
            OAuthClientProperties.Provider provider, OAuthStateStore store, OAuthUserResolver resolver) {
        OAuthClientProperties properties = new OAuthClientProperties();
        properties.getProviders().put(PROVIDER_ID, provider);

        ProviderRegistry registry = new ProviderRegistry(properties);
        OAuthClient client = new OAuthClient(RestClient.create());
        // 엔드포인트를 전부 명시했으므로 discovery 불필요. RANDOM_PORT 에서 issuer(고정 host:9000) 유도 discovery 가
        //   죽은 포트를 치는 것을 피하려고 no-op 으로 둔다(discovery 문서의 엔드포인트는 issuer-host 기준이라
        //   빈 칸을 죽은 URL 로 back-fill 하는 부작용도 함께 차단).
        OidcMetadataResolver metadataResolver = new OidcMetadataResolver(new OidcDiscoveryClient(RestClient.create())) {
            @Override
            public void ensureResolved(String providerId, OAuthClientProperties.Provider provider) {
                // no-op: 엔드포인트 명시 모드
            }
        };
        IdTokenVerifier idTokenVerifier =
                new IdTokenVerifier(new JwksKeyResolver(RestClient.create(), Duration.ofMinutes(5)));
        return new OAuthLoginService(
                registry, client, store, resolver, echoTokenIssuer(), metadataResolver, idTokenVerifier, properties);
    }

    /**
     * confidential demo-rp 공급자 설정. authorization/token/jwks 를 라이브 포트로 명시하고 discovery 는 끈다(no-op
     * resolver). issuer 는 런타임 핀(iss 검증 대상). <b>userInfoUri 는 설정하지 않는다</b> — 콜백이 검증된 id_token
     * 클레임만으로 신원을 구성하게 해 AS {@code /userinfo}(resource-server) 결합을 피한다(id_token 이 sub·roles·
     * nonce·auth_time 을 모두 담으므로 userinfo 가 단언에 더하는 값이 없다).
     */
    private OAuthClientProperties.Provider rpProvider(String clientSecret) {
        OAuthClientProperties.Provider p = new OAuthClientProperties.Provider();
        p.setClientId(CLIENT_ID);
        p.setClientSecret(clientSecret);
        p.setUserNameAttribute("sub"); // OIDC 라도 ProviderRegistry.require 가 user-name-attribute 를 요구
        p.setNameAttribute("name");
        p.setRedirectUri(REDIRECT_URI);
        p.setAuthorizationUri(live("/oauth2/authorize"));
        p.setTokenUri(live("/oauth2/token"));
        // userInfoUri 미설정 → 콜백은 id_token 클레임만으로 신원 구성(/userinfo 미호출).
        p.getOidc().setEnabled(true);
        p.getOidc().setIssuer(issuer()); // iss 핀 = AuthorizationServerSettings.getIssuer()
        p.getOidc().setJwksUri(live("/oauth2/jwks"));
        p.getOidc().setNonce(true);
        return p;
    }

    /** id_token 클레임을 포착하고 ROLE_USER 사용자로 매핑하는 테스트 resolver. */
    private OAuthUserResolver capturingResolver() {
        return userInfo -> {
            captured.set(userInfo);
            return new AuthenticatedUser(userInfo.providerId(), userInfo.name(), List.of("ROLE_USER"));
        };
    }

    /** 자체 토큰 발급기 stub — 사용자 신원을 그대로 토큰응답으로 반사(흐름 완주 확인용; 실 JWT 발급은 별도 테스트 담당). */
    private static OAuthTokenIssuer echoTokenIssuer() {
        return user -> new TokenResponse("acc-" + user.userId(), null, "Bearer", 3600L, user.roles());
    }

    private String live(String path) {
        return "http://localhost:" + port + path;
    }

    private String issuer() {
        return authorizationServerSettings.getIssuer();
    }

    private static String queryParam(String url, String name) {
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(name);
    }
}
