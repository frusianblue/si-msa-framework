package com.company.framework.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.security.token.TokenStore;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 다운스트림 이중 발급기 재검증 + 신뢰 자세(zero-trust / gateway-headers) 단위 테스트.
 * AS(OP) 발급 RS256 토큰을 JWKS 로 검증하고, iss 로 자체 JWT 와 분기하며, 모드별 동작과 jti 블랙리스트 경계(§4)를 확인한다.
 */
class DownstreamDualIssuerTest {

    private static final String ISSUER = "https://auth.example.com";
    private static final String SECRET = "0123456789012345678901234567890123456789"; // 40 bytes
    private final JwtProvider jwtProvider = new JwtProvider(new JwtProperties(SECRET, 1800, 1209600));

    private final KeyPair asKeys = rsaPair();
    private final String asKid = "as-key-1";

    // ---- helpers --------------------------------------------------------

    private static KeyPair rsaPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64u(BigInteger v) {
        byte[] bytes = v.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String jwksJson() {
        RSAPublicKey pub = (RSAPublicKey) asKeys.getPublic();
        return "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"" + asKid + "\",\"n\":\""
                + b64u(pub.getModulus()) + "\",\"e\":\"" + b64u(pub.getPublicExponent()) + "\"}]}";
    }

    /** 네트워크 없이 JWKS 를 주입한 리소스 서버 검증기. */
    private ResourceServerJwtVerifier asVerifier() {
        return new ResourceServerJwtVerifier(
                null, ISSUER, ISSUER + "/oauth2/jwks", "roles", null, Duration.ofSeconds(60), Duration.ofHours(1)) {
            @Override
            protected String fetchJwksJson(String uri) {
                return jwksJson();
            }
        };
    }

    private String asToken(String iss, String sub, List<String> roles) {
        return Jwts.builder()
                .header()
                .keyId(asKid)
                .and()
                .issuer(iss)
                .subject(sub)
                .claim("roles", roles)
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(asKeys.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private String selfToken(String sub, List<String> roles) {
        return jwtProvider.createAccessToken(sub, roles);
    }

    /** 호출 추적이 되는 TokenStore 더블. */
    private static final class SpyTokenStore implements TokenStore {
        final AtomicBoolean consulted = new AtomicBoolean(false);
        volatile boolean blacklisted = false;

        @Override
        public void saveRefresh(String refreshToken, RefreshEntry entry, Duration ttl) {}

        @Override
        public Optional<RefreshEntry> findRefresh(String refreshToken) {
            return Optional.empty();
        }

        @Override
        public void removeRefresh(String refreshToken) {}

        @Override
        public void blacklist(String jti, Duration ttl) {}

        @Override
        public boolean isBlacklisted(String jti) {
            consulted.set(true);
            return blacklisted;
        }
    }

    // ---- verifier -------------------------------------------------------

    @Test
    void as_token_is_verified_via_jwks() {
        ResourceServerJwtVerifier.Verified v = asVerifier().verify(asToken(ISSUER, "ext-user", List.of("PARTNER")));
        assertThat(v.userId()).isEqualTo("ext-user");
        assertThat(v.roles()).containsExactly("PARTNER");
    }

    @Test
    void as_token_with_wrong_issuer_is_rejected_by_verifier() {
        assertThatThrownBy(() -> asVerifier().verify(asToken("https://evil.example.com", "ext-user", List.of())))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void hmac_token_on_as_path_is_rejected() {
        assertThatThrownBy(() -> asVerifier().verify(selfToken("u", List.of()))).isInstanceOf(JwtException.class);
    }

    // ---- authenticator routing -----------------------------------------

    @Test
    void routes_by_issuer() {
        DownstreamTokenAuthenticator auth = new DownstreamTokenAuthenticator(jwtProvider, asVerifier());
        assertThat(auth.kindOf(asToken(ISSUER, "ext", List.of()))).isEqualTo(TokenIssuerKind.AUTHORIZATION_SERVER);
        assertThat(auth.kindOf(selfToken("u", List.of("USER")))).isEqualTo(TokenIssuerKind.INTERNAL);
    }

    @Test
    void without_verifier_everything_routes_internal_and_as_token_fails() {
        DownstreamTokenAuthenticator auth = new DownstreamTokenAuthenticator(jwtProvider, null);
        assertThat(auth.kindOf(asToken(ISSUER, "ext", List.of()))).isEqualTo(TokenIssuerKind.INTERNAL);
        assertThat(auth.tryAuthenticate(asToken(ISSUER, "ext", List.of()))).isNull(); // HMAC 검증 실패
    }

    @Test
    void authenticate_builds_role_authorities_for_as_token() {
        DownstreamTokenAuthenticator auth = new DownstreamTokenAuthenticator(jwtProvider, asVerifier());
        DownstreamTokenAuthenticator.Authenticated a =
                auth.tryAuthenticate(asToken(ISSUER, "ext-user", List.of("PARTNER")));
        assertThat(a).isNotNull();
        assertThat(a.kind()).isEqualTo(TokenIssuerKind.AUTHORIZATION_SERVER);
        assertThat(a.authentication().getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_PARTNER");
    }

    // ---- filter: zero-trust --------------------------------------------

    private MockHttpServletRequest get(String bearer, String userIdHeader, String rolesHeader) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/orders");
        if (bearer != null) {
            req.addHeader("Authorization", "Bearer " + bearer);
        }
        if (userIdHeader != null) {
            req.addHeader("X-User-Id", userIdHeader);
        }
        if (rolesHeader != null) {
            req.addHeader("X-User-Roles", rolesHeader);
        }
        return req;
    }

    private Authentication runFilter(JwtAuthenticationFilter filter, MockHttpServletRequest req) throws Exception {
        SecurityContextHolder.clearContext();
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.clearContext();
        return a;
    }

    @Test
    void zero_trust_verifies_self_jwt_and_consults_blacklist() throws Exception {
        DownstreamTokenAuthenticator auth = new DownstreamTokenAuthenticator(jwtProvider, asVerifier());
        SpyTokenStore store = new SpyTokenStore();
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                auth, store, JwtAuthenticationFilter.Mode.ZERO_TRUST, "X-User-Id", "X-User-Roles");

        Authentication a = runFilter(filter, get(selfToken("user-1", List.of("USER")), null, null));
        assertThat(a).isNotNull();
        assertThat(a.getName()).isEqualTo("user-1");
        assertThat(store.consulted).as("자체 JWT 는 jti 블랙리스트를 조회해야 한다").isTrue();
    }

    @Test
    void zero_trust_rejects_blacklisted_self_jwt() throws Exception {
        DownstreamTokenAuthenticator auth = new DownstreamTokenAuthenticator(jwtProvider, asVerifier());
        SpyTokenStore store = new SpyTokenStore();
        store.blacklisted = true;
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                auth, store, JwtAuthenticationFilter.Mode.ZERO_TRUST, "X-User-Id", "X-User-Roles");

        assertThat(runFilter(filter, get(selfToken("user-1", List.of("USER")), null, null)))
                .as("로그아웃된 토큰은 인증되지 않아야 한다")
                .isNull();
    }

    @Test
    void zero_trust_verifies_as_token_and_skips_blacklist() throws Exception {
        DownstreamTokenAuthenticator auth = new DownstreamTokenAuthenticator(jwtProvider, asVerifier());
        SpyTokenStore store = new SpyTokenStore();
        store.blacklisted = true; // 블랙리스트가 true 여도 AS 토큰은 조회 자체를 건너뛰어 통과해야 한다(§4)
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                auth, store, JwtAuthenticationFilter.Mode.ZERO_TRUST, "X-User-Id", "X-User-Roles");

        Authentication a = runFilter(filter, get(asToken(ISSUER, "ext-user", List.of("PARTNER")), null, null));
        assertThat(a).isNotNull();
        assertThat(a.getName()).isEqualTo("ext-user");
        assertThat(store.consulted).as("AS 토큰은 자체 jti 블랙리스트를 조회하지 않아야 한다").isFalse();
    }

    @Test
    void zero_trust_ignores_gateway_headers_without_valid_bearer() throws Exception {
        DownstreamTokenAuthenticator auth = new DownstreamTokenAuthenticator(jwtProvider, asVerifier());
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                auth, new SpyTokenStore(), JwtAuthenticationFilter.Mode.ZERO_TRUST, "X-User-Id", "X-User-Roles");

        // Bearer 없이 위조 헤더만 → 인증 안 됨(헤더를 신원 근거로 쓰지 않음)
        assertThat(runFilter(filter, get(null, "spoofed-admin", "ADMIN"))).isNull();
    }

    // ---- filter: gateway-headers ---------------------------------------

    @Test
    void gateway_headers_trusts_injected_identity_without_bearer() throws Exception {
        DownstreamTokenAuthenticator auth = new DownstreamTokenAuthenticator(jwtProvider, asVerifier());
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                auth, new SpyTokenStore(), JwtAuthenticationFilter.Mode.GATEWAY_HEADERS, "X-User-Id", "X-User-Roles");

        Authentication a = runFilter(filter, get(null, "gw-user", "USER,ADMIN"));
        assertThat(a).isNotNull();
        assertThat(a.getName()).isEqualTo("gw-user");
        assertThat(a.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void gateway_headers_without_user_header_is_unauthenticated() throws Exception {
        DownstreamTokenAuthenticator auth = new DownstreamTokenAuthenticator(jwtProvider, asVerifier());
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                auth, new SpyTokenStore(), JwtAuthenticationFilter.Mode.GATEWAY_HEADERS, "X-User-Id", "X-User-Roles");

        assertThat(runFilter(filter, get(selfToken("ignored", List.of()), null, null)))
                .isNull();
    }
}
