package com.company.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.gateway.config.GatewayAuthProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 이중 발급기(AUTH_SERVER.md §4) 단위 테스트. AS(OP) 발급 RS256 토큰을 JWKS 로 검증하고, iss 로 자체 JWT 와 분기하며,
 * AS 토큰은 자체 jti 블랙리스트를 타지 않음을 확인한다. JWKS 는 네트워크 대신 fetch 오버라이드로 주입한다.
 */
class GatewayDualIssuerTest {

    private static final String ISSUER = "https://auth.example.com";
    private static final String SECRET = "0123456789012345678901234567890123456789"; // 40 bytes
    private final SecretKey hmac = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

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

    /** 네트워크 없이 JWKS 를 주입한 AS 검증기. */
    private GatewayJwksTokenVerifier asVerifier() {
        return new GatewayJwksTokenVerifier(
                null, ISSUER, ISSUER + "/oauth2/jwks", "roles", Duration.ofSeconds(60), Duration.ofHours(1)) {
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

    private String selfToken(String jti, String sub, List<String> roles) {
        return Jwts.builder()
                .id(jti)
                .subject(sub)
                .claim("roles", roles)
                .claim("typ", "access")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(hmac)
                .compact();
    }

    // ---- AS verifier ----------------------------------------------------

    @Test
    void as_token_is_verified_via_jwks() {
        GatewayTokenVerifier.Verified v = asVerifier().verify(asToken(ISSUER, "ext-user", List.of("PARTNER")));
        assertThat(v.userId()).isEqualTo("ext-user");
        assertThat(v.roles()).containsExactly("PARTNER");
    }

    @Test
    void as_token_with_wrong_issuer_is_rejected() {
        assertThatThrownBy(() -> asVerifier().verify(asToken("https://evil.example.com", "ext-user", List.of())))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void hmac_token_routed_to_as_path_is_rejected() {
        // HS 토큰이 AS 경로로 잘못 들어오면(이론상) 비대칭 전용 가드에 걸려 거부된다.
        assertThatThrownBy(() -> asVerifier().verify(selfToken("j", "u", List.of())))
                .isInstanceOf(JwtException.class);
    }

    // ---- routing --------------------------------------------------------

    @Test
    void routes_by_issuer() {
        GatewayTokenAuthenticator auth =
                new GatewayTokenAuthenticator(new GatewayTokenVerifier(SECRET, "access"), asVerifier());
        assertThat(auth.kindOf(asToken(ISSUER, "ext", List.of()))).isEqualTo(TokenIssuerKind.AUTHORIZATION_SERVER);
        assertThat(auth.kindOf(selfToken("j", "u", List.of("USER")))).isEqualTo(TokenIssuerKind.INTERNAL);
    }

    @Test
    void without_as_verifier_everything_routes_internal() {
        GatewayTokenAuthenticator auth =
                new GatewayTokenAuthenticator(new GatewayTokenVerifier(SECRET, "access"), null);
        assertThat(auth.kindOf(asToken(ISSUER, "ext", List.of()))).isEqualTo(TokenIssuerKind.INTERNAL);
    }

    // ---- filter: AS token skips self blacklist (§4 boundary) ------------

    @Test
    void as_token_skips_self_jti_blacklist_and_injects_headers() {
        GatewayAuthProperties props = new GatewayAuthProperties();
        GatewayTokenAuthenticator auth =
                new GatewayTokenAuthenticator(new GatewayTokenVerifier(SECRET, "access"), asVerifier());
        // 블랙리스트가 항상 true 를 반환해도, AS 토큰은 조회 자체를 건너뛰어 통과해야 한다(혼용 금지).
        AtomicBoolean blacklistConsulted = new AtomicBoolean(false);
        GatewayTokenBlacklist blacklist = jti -> {
            blacklistConsulted.set(true);
            return Mono.just(true);
        };
        GatewayAuthGlobalFilter filter = new GatewayAuthGlobalFilter(auth, blacklist, props);

        String token = asToken(ISSUER, "ext-user", List.of("PARTNER"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            captured.set(ex);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(chainCalled).isTrue();
        assertThat(blacklistConsulted).as("AS 토큰은 자체 jti 블랙리스트를 조회하지 않아야 한다").isFalse();
        assertThat(captured.get().getRequest().getHeaders().getFirst(props.getUserIdHeader()))
                .isEqualTo("ext-user");
        assertThat(captured.get().getRequest().getHeaders().getFirst(props.getRolesHeader()))
                .isEqualTo("PARTNER");
    }

    @Test
    void internal_token_still_consults_blacklist() {
        GatewayAuthProperties props = new GatewayAuthProperties();
        GatewayTokenAuthenticator auth =
                new GatewayTokenAuthenticator(new GatewayTokenVerifier(SECRET, "access"), asVerifier());
        AtomicBoolean blacklistConsulted = new AtomicBoolean(false);
        GatewayTokenBlacklist blacklist = jti -> {
            blacklistConsulted.set(true);
            return Mono.just(false);
        };
        GatewayAuthGlobalFilter filter = new GatewayAuthGlobalFilter(auth, blacklist, props);

        String token = selfToken("jti-1", "user-1", List.of("USER"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertThat(blacklistConsulted).as("자체 JWT 는 jti 블랙리스트를 조회해야 한다").isTrue();
    }
}
