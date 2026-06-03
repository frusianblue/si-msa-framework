package com.company.framework.oauthclient.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.core.error.BusinessException;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** JWKS 파싱/캐시/회전 검증. 네트워크 대신 fetchJwksJson 을 오버라이드해 JWKS JSON 을 주입한다. */
class JwksKeyResolverTest {

    private static String b64u(BigInteger v) {
        byte[] bytes = v.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String jwk(RSAPublicKey pub, String kid) {
        return "{\"kty\":\"RSA\",\"use\":\"sig\",\"alg\":\"RS256\",\"kid\":\"" + kid + "\",\"n\":\""
                + b64u(pub.getModulus()) + "\",\"e\":\"" + b64u(pub.getPublicExponent()) + "\"}";
    }

    private static RSAPublicKey rsa() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        return (RSAPublicKey) kp.getPublic();
    }

    @Test
    void resolves_key_by_kid() throws Exception {
        RSAPublicKey a = rsa();
        RSAPublicKey b = rsa();
        String json = "{\"keys\":[" + jwk(a, "k1") + "," + jwk(b, "k2") + "]}";
        JwksKeyResolver resolver = new JwksKeyResolver(null, null) {
            @Override
            protected String fetchJwksJson(String jwksUri) {
                return json;
            }
        };
        Key k1 = resolver.resolve("https://idp/jwks", "k1");
        assertThat(k1).isInstanceOf(RSAPublicKey.class);
        assertThat(((RSAPublicKey) k1).getModulus()).isEqualTo(a.getModulus());
    }

    @Test
    void single_key_with_null_kid_is_used() throws Exception {
        RSAPublicKey a = rsa();
        String json = "{\"keys\":[" + jwk(a, "only") + "]}";
        JwksKeyResolver resolver = new JwksKeyResolver(null, null) {
            @Override
            protected String fetchJwksJson(String jwksUri) {
                return json;
            }
        };
        Key k = resolver.resolve("https://idp/jwks", null);
        assertThat(((RSAPublicKey) k).getModulus()).isEqualTo(a.getModulus());
    }

    @Test
    void unknown_kid_triggers_one_refetch_then_fails() throws Exception {
        RSAPublicKey a = rsa();
        String json = "{\"keys\":[" + jwk(a, "k1") + "]}";
        AtomicInteger fetches = new AtomicInteger();
        JwksKeyResolver resolver = new JwksKeyResolver(null, null) {
            @Override
            protected String fetchJwksJson(String jwksUri) {
                fetches.incrementAndGet();
                return json; // 회전됐다고 가정하지만 stub 은 같은 키만 반환 → 결국 미발견
            }
        };
        assertThatThrownBy(() -> resolver.resolve("https://idp/jwks", "rotated-kid"))
                .isInstanceOf(BusinessException.class);
        // 최초 조회 1 + 미발견 후 강제 재조회 1 = 2
        assertThat(fetches.get()).isEqualTo(2);
    }

    @Test
    void blank_jwks_uri_is_rejected() {
        JwksKeyResolver resolver = new JwksKeyResolver(null, null) {
            @Override
            protected String fetchJwksJson(String jwksUri) {
                return "{\"keys\":[]}";
            }
        };
        assertThatThrownBy(() -> resolver.resolve("  ", "k1")).isInstanceOf(BusinessException.class);
    }
}
