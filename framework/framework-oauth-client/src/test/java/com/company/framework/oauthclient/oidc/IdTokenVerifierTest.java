package com.company.framework.oauthclient.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.core.error.BusinessException;
import com.company.framework.oauthclient.config.OAuthClientProperties;
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
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

/**
 * id_token 검증기 테스트. HS256(client-secret HMAC)으로 클레임 규칙 전반(iss/aud/nonce/exp/위변조)을 검증하고,
 * RSA(JWKS) 경로는 공개키로 손수 만든 JWKS 를 stub 으로 주입해 서명 검증까지 확인한다.
 */
class IdTokenVerifierTest {

    private static final String ISSUER = "https://idp.example.com";
    private static final String CLIENT_ID = "client-123";
    private static final String SECRET = "0123456789012345678901234567890123456789AB"; // >=32B(HS256)

    private OAuthClientProperties.Provider provider(String jwksUri) {
        OAuthClientProperties.Provider p = new OAuthClientProperties.Provider();
        p.setClientId(CLIENT_ID);
        p.setClientSecret(SECRET);
        p.getOidc().setEnabled(true);
        p.getOidc().setIssuer(ISSUER);
        p.getOidc().setJwksUri(jwksUri);
        p.getOidc().setClockSkew(Duration.ofSeconds(0));
        return p;
    }

    // HS256 검증에는 JWKS 가 필요 없으므로 resolve 가 호출되면 테스트 실패시키는 더미.
    private IdTokenVerifier hsVerifier() {
        JwksKeyResolver resolver = new JwksKeyResolver(null, null) {
            @Override
            protected String fetchJwksJson(String jwksUri) {
                throw new IllegalStateException("HS 경로에서 JWKS 를 조회하면 안 된다");
            }
        };
        return new IdTokenVerifier(resolver);
    }

    private String hs256(String iss, String aud, String nonce, Instant exp) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("user-1")
                .issuer(iss)
                .audience()
                .add(aud)
                .and()
                .claim("nonce", nonce)
                .claim("email", "u@example.com")
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    @Test
    void hs256_valid_token_passes_and_returns_claims() {
        String token = hs256(ISSUER, CLIENT_ID, "NONCE1", Instant.now().plusSeconds(300));
        Map<String, Object> claims = hsVerifier().verify(provider(null), token, "NONCE1");
        assertThat(claims.get("sub")).isEqualTo("user-1");
        assertThat(claims.get("email")).isEqualTo("u@example.com");
    }

    @Test
    void wrong_issuer_is_rejected() {
        String token = hs256(
                "https://evil.example.com", CLIENT_ID, "NONCE1", Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> hsVerifier().verify(provider(null), token, "NONCE1"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void wrong_audience_is_rejected() {
        String token = hs256(ISSUER, "other-client", "NONCE1", Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> hsVerifier().verify(provider(null), token, "NONCE1"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void wrong_nonce_is_rejected() {
        String token = hs256(ISSUER, CLIENT_ID, "NONCE1", Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> hsVerifier().verify(provider(null), token, "EXPECTED-OTHER"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void expired_token_is_rejected() {
        String token = hs256(ISSUER, CLIENT_ID, "NONCE1", Instant.now().minusSeconds(30));
        assertThatThrownBy(() -> hsVerifier().verify(provider(null), token, "NONCE1"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void tampered_signature_is_rejected() {
        String token = hs256(ISSUER, CLIENT_ID, "NONCE1", Instant.now().plusSeconds(300));
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "B" : "A");
        assertThatThrownBy(() -> hsVerifier().verify(provider(null), tampered, "NONCE1"))
                .isInstanceOf(BusinessException.class);
    }

    // ----------------------- RSA(JWKS) 경로 -----------------------

    private static String b64u(BigInteger v) {
        byte[] bytes = v.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String jwksJson(RSAPublicKey pub, String kid) {
        return "{\"keys\":[{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"" + kid + "\",\"n\":\""
                + b64u(pub.getModulus()) + "\",\"e\":\"" + b64u(pub.getPublicExponent()) + "\"}]}";
    }

    @Test
    void rs256_valid_token_with_jwks_passes() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String jwks = jwksJson((RSAPublicKey) kp.getPublic(), "k1");

        JwksKeyResolver resolver = new JwksKeyResolver(null, null) {
            @Override
            protected String fetchJwksJson(String jwksUri) {
                return jwks;
            }
        };
        IdTokenVerifier verifier = new IdTokenVerifier(resolver);

        String token = Jwts.builder()
                .header()
                .keyId("k1")
                .and()
                .subject("user-rsa")
                .issuer(ISSUER)
                .audience()
                .add(CLIENT_ID)
                .and()
                .claim("nonce", "NRSA")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(kp.getPrivate(), Jwts.SIG.RS256)
                .compact();

        Map<String, Object> claims = verifier.verify(provider("https://idp.example.com/jwks"), token, "NRSA");
        assertThat(claims.get("sub")).isEqualTo("user-rsa");
    }

    @Test
    void rs256_tampered_token_is_rejected() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String jwks = jwksJson((RSAPublicKey) kp.getPublic(), "k1");

        JwksKeyResolver resolver = new JwksKeyResolver(null, null) {
            @Override
            protected String fetchJwksJson(String jwksUri) {
                return jwks;
            }
        };
        IdTokenVerifier verifier = new IdTokenVerifier(resolver);

        String token = Jwts.builder()
                .header()
                .keyId("k1")
                .and()
                .subject("user-rsa")
                .issuer(ISSUER)
                .audience()
                .add(CLIENT_ID)
                .and()
                .claim("nonce", "NRSA")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(kp.getPrivate(), Jwts.SIG.RS256)
                .compact();
        String tampered = token.substring(0, token.length() - 3) + "xyz";

        assertThatThrownBy(() -> verifier.verify(provider("https://idp.example.com/jwks"), tampered, "NRSA"))
                .isInstanceOf(BusinessException.class);
    }
}
