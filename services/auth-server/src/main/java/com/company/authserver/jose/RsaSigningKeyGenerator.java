package com.company.authserver.jose;

import java.time.Instant;

/**
 * {@link SigningKeyGenerator} 의 RSA 구현. 키 생성은 {@link JdbcRotatingJwkSource#generateRsaKey()}(RS256/2048, 같은 패키지라
 * package-private 재사용 — 가시성 승격 불필요)를, 개인키 보호는 {@link SigningKeyCipher} 를 사용한다.
 */
public final class RsaSigningKeyGenerator implements SigningKeyGenerator {

    private final SigningKeyCipher cipher;

    public RsaSigningKeyGenerator(SigningKeyCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public SigningKey generateActive() {
        var rsa = JdbcRotatingJwkSource.generateRsaKey();
        String stored = cipher.protect(rsa.toJSONString()); // 개인키 포함 → 보호 후 저장형
        return SigningKey.active(rsa.getKeyID(), stored, Instant.now());
    }
}
