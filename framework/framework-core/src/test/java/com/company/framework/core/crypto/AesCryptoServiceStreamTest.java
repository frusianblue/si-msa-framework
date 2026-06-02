package com.company.framework.core.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** AES-CBC 스트리밍 암복호화(파일 본문용) 단위 테스트. */
class AesCryptoServiceStreamTest {

    private final AesCryptoService aes = new AesCryptoService("unit-test-secret");

    @Test
    @DisplayName("스트림 암호화 → 평문과 다르고, 길이는 cbcEncryptedLength 와 일치하며, 복호화로 복원된다")
    void streamRoundTrip() throws Exception {
        byte[] plain = "대외비 문서 내용 — secret payload 123".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted;
        try (InputStream in = aes.encryptingInputStream(new ByteArrayInputStream(plain))) {
            encrypted = in.readAllBytes();
        }

        assertThat(encrypted).isNotEqualTo(plain); // 실제로 암호화됨
        assertThat((long) encrypted.length).isEqualTo(AesCryptoService.cbcEncryptedLength(plain.length));

        byte[] decrypted;
        try (InputStream in = aes.decryptingInputStream(new ByteArrayInputStream(encrypted))) {
            decrypted = in.readAllBytes();
        }
        assertThat(decrypted).isEqualTo(plain);
    }

    @Test
    @DisplayName("빈 입력도 라운드트립된다(패딩 한 블록 + IV)")
    void emptyRoundTrip() throws Exception {
        byte[] plain = new byte[0];
        byte[] encrypted;
        try (InputStream in = aes.encryptingInputStream(new ByteArrayInputStream(plain))) {
            encrypted = in.readAllBytes();
        }
        assertThat((long) encrypted.length).isEqualTo(AesCryptoService.cbcEncryptedLength(0));
        try (InputStream in = aes.decryptingInputStream(new ByteArrayInputStream(encrypted))) {
            assertThat(in.readAllBytes()).isEmpty();
        }
    }

    @Test
    @DisplayName("대용량(블록 경계 무관)도 정확한 길이로 라운드트립된다")
    void largeRoundTrip() throws Exception {
        byte[] plain = new byte[100_003]; // 16의 배수가 아닌 크기
        new Random(42).nextBytes(plain);

        byte[] encrypted;
        try (InputStream in = aes.encryptingInputStream(new ByteArrayInputStream(plain))) {
            encrypted = in.readAllBytes();
        }
        assertThat((long) encrypted.length).isEqualTo(AesCryptoService.cbcEncryptedLength(plain.length));

        try (InputStream in = aes.decryptingInputStream(new ByteArrayInputStream(encrypted))) {
            assertThat(in.readAllBytes()).isEqualTo(plain);
        }
    }
}
