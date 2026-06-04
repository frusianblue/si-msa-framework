package com.company.authserver.jose;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.core.crypto.AesCryptoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 개인키 보호 cipher 검증. 마커 라운드트립 / 평문 통과(롤백·데모 혼재) / 쓰기 토글 off 동작.
 */
class AesSigningKeyCipherTest {

    private static final String JWK = "{\"kty\":\"RSA\",\"kid\":\"abc\",\"d\":\"secret-private-part\"}";
    private final AesCryptoService aes = new AesCryptoService("unit-test-master-key-16bytes+");

    @Test
    @DisplayName("암호화 on: protect 는 enc: 마커+암호문, reveal 은 원문 복원")
    void protectReveal_roundtrip() {
        var cipher = new AesSigningKeyCipher(aes, true);
        String stored = cipher.protect(JWK);

        assertThat(stored).startsWith(AesSigningKeyCipher.MARKER);
        assertThat(stored).doesNotContain("secret-private-part"); // 평문 노출 안 됨
        assertThat(cipher.reveal(stored)).isEqualTo(JWK);
    }

    @Test
    @DisplayName("reveal 은 마커 없는 평문(레거시/데모)을 그대로 반환 → 혼재/롤백 안전")
    void reveal_passesThroughPlaintext() {
        var cipher = new AesSigningKeyCipher(aes, true);
        assertThat(cipher.reveal(JWK)).isEqualTo(JWK); // 마커 없음 = 평문
    }

    @Test
    @DisplayName("암호화 off: protect 는 평문 저장, 그래도 reveal 은 기존 암호문도 복호화")
    void encryptionOff_writesPlaintextButStillReadsCiphertext() {
        var on = new AesSigningKeyCipher(aes, true);
        String cipherText = on.protect(JWK); // 이전에 암호화된 행

        var off = new AesSigningKeyCipher(aes, false);
        assertThat(off.protect(JWK)).isEqualTo(JWK); // 쓰기는 평문
        assertThat(off.reveal(cipherText)).isEqualTo(JWK); // 읽기는 마커 인지 → 복호화
    }

    @Test
    @DisplayName("null 안전")
    void nullSafe() {
        var cipher = new AesSigningKeyCipher(aes, true);
        assertThat(cipher.protect(null)).isNull();
        assertThat(cipher.reveal(null)).isNull();
    }
}
