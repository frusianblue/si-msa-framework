package com.company.framework.mfa.totp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RFC 4648 Base32 인코더/디코더 회귀 테스트. 인증기 앱 호환을 위해 패딩은 인코딩 시 생략·디코딩 시 허용한다는
 * 구현 계약을 알려진 정답 벡터로 고정한다(외부 의존성 0 — 순수 JDK 로직이라 받는 쪽 어디서나 결정적으로 통과).
 */
class Base32Test {

    @Test
    @DisplayName("RFC4648 표준 벡터(패딩 생략) — f/fo/foo/.../foobar")
    void rfc4648Vectors() {
        assertThat(Base32.encode("".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(Base32.encode("f".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MY");
        assertThat(Base32.encode("fo".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXQ");
        assertThat(Base32.encode("foo".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6");
        assertThat(Base32.encode("foob".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YQ");
        assertThat(Base32.encode("fooba".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTB");
        assertThat(Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
    }

    @Test
    @DisplayName("20바이트(=160비트, TOTP 기본 시크릿 길이) 라운드트립 무손실")
    void roundTrip20Bytes() {
        byte[] raw = new byte[20];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) i;
        }
        String encoded = Base32.encode(raw);
        assertThat(encoded).hasSize(32); // 160/5 = 32, 패딩 없이 정확히 떨어짐
        assertThat(Base32.decode(encoded)).isEqualTo(raw);
    }

    @Test
    @DisplayName("디코딩은 공백/하이픈/패딩/소문자를 허용")
    void decodeIsLenient() {
        assertThat(Base32.decode("mz xw-6yt boi=")).isEqualTo(Base32.decode("MZXW6YTBOI"));
    }

    @Test
    @DisplayName("알파벳 밖 문자는 예외")
    void decodeRejectsInvalidChar() {
        // '0','1','8','9' 는 RFC4648 Base32 알파벳에 없음
        assertThatThrownBy(() -> Base32.decode("MZXW0")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null/빈 입력은 빈 결과")
    void nullAndEmpty() {
        assertThat(Base32.encode(null)).isEmpty();
        assertThat(Base32.encode(new byte[0])).isEmpty();
        assertThat(Base32.decode(null)).isEmpty();
        assertThat(Base32.decode("")).isEmpty();
    }
}
