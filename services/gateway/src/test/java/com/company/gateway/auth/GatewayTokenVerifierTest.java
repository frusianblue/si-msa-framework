package com.company.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/** 엣지 JWT 검증기 단위 테스트. framework-security 와 동일한 HMAC-SHA 방식으로 토큰을 만들어 검증한다. */
class GatewayTokenVerifierTest {

    private static final String SECRET = "0123456789012345678901234567890123456789"; // 40 bytes
    private final SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    private final GatewayTokenVerifier verifier = new GatewayTokenVerifier(SECRET, "access");

    private String token(String typ, Instant exp, String subject, List<String> roles) {
        var builder = Jwts.builder().subject(subject).claim("roles", roles).expiration(Date.from(exp));
        if (typ != null) {
            builder.claim("typ", typ);
        }
        return builder.signWith(key).compact();
    }

    @Test
    void valid_access_token_yields_user_and_roles() {
        String t = token("access", Instant.now().plusSeconds(60), "user-1", List.of("USER", "ADMIN"));
        GatewayTokenVerifier.Verified v = verifier.verify(t);
        assertThat(v.userId()).isEqualTo("user-1");
        assertThat(v.roles()).containsExactly("USER", "ADMIN");
    }

    @Test
    void wrong_token_type_is_rejected() {
        String t = token("refresh", Instant.now().plusSeconds(60), "user-1", List.of("USER"));
        assertThatThrownBy(() -> verifier.verify(t)).isInstanceOf(JwtException.class);
    }

    @Test
    void expired_token_is_rejected() {
        String t = token("access", Instant.now().minusSeconds(10), "user-1", List.of("USER"));
        assertThatThrownBy(() -> verifier.verify(t)).isInstanceOf(JwtException.class);
    }

    @Test
    void tampered_signature_is_rejected() {
        SecretKey other = Keys.hmacShaKeyFor("99999999999999999999999999999999".getBytes(StandardCharsets.UTF_8));
        String t = Jwts.builder()
                .subject("user-1")
                .claim("roles", List.of("USER"))
                .claim("typ", "access")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(other)
                .compact();
        assertThatThrownBy(() -> verifier.verify(t)).isInstanceOf(JwtException.class);
    }

    @Test
    void missing_roles_defaults_to_empty() {
        String t = Jwts.builder()
                .subject("user-2")
                .claim("typ", "access")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(key)
                .compact();
        assertThat(verifier.verify(t).roles()).isEmpty();
    }

    @Test
    void jti_is_extracted_for_blacklist_lookup() {
        String t = Jwts.builder()
                .id("jti-123")
                .subject("user-1")
                .claim("typ", "access")
                .claim("roles", List.of("USER"))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(key)
                .compact();
        assertThat(verifier.verify(t).jti()).isEqualTo("jti-123");
    }
}
