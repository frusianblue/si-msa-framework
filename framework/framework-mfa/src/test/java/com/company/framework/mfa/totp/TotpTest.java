package com.company.framework.mfa.totp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RFC 6238 TOTP 코드 생성/검증 회귀 테스트.
 *
 * <p>공개 API({@code generate}/{@code verify})가 시스템 시계를 사용하므로 고정-시각 RFC 벡터를 직접 박지 않고,
 * 동일 인스턴스의 generate↔verify 라운드트립(같은 스텝·window 허용 범위 내에서 항상 성립)과
 * 형식 거부(자릿수/숫자/널)로 검증한다. 형식 거부 케이스는 시계와 무관하게 100% 결정적이다.
 */
class TotpTest {

    /** 20바이트 결정적 시크릿(테스트 고정). */
    private static byte[] secret() {
        byte[] s = new byte[20];
        for (int i = 0; i < s.length; i++) {
            s[i] = (byte) (i * 7 + 1);
        }
        return s;
    }

    @Test
    @DisplayName("SHA1/6자리 현재시각 코드는 자기검증(round-trip) 통과")
    void roundTripSha1() {
        Totp totp = new Totp("SHA1", 6, 30, 1);
        byte[] secret = secret();
        String code = totp.generate(secret);
        assertThat(code).hasSize(6).containsOnlyDigits();
        assertThat(totp.verify(Base32.encode(secret), code)).isTrue();
    }

    @Test
    @DisplayName("SHA256/SHA512/8자리 변형도 round-trip 통과")
    void roundTripVariants() {
        byte[] secret = secret();
        for (String alg : new String[] {"SHA256", "SHA512"}) {
            Totp totp = new Totp(alg, 6, 30, 1);
            assertThat(totp.verify(Base32.encode(secret), totp.generate(secret)))
                    .as("alg=%s", alg)
                    .isTrue();
        }
        Totp eightDigit = new Totp("SHA1", 8, 30, 1);
        String code8 = eightDigit.generate(secret);
        assertThat(code8).hasSize(8);
        assertThat(eightDigit.verify(Base32.encode(secret), code8)).isTrue();
    }

    @Test
    @DisplayName("입력 코드의 공백/하이픈은 정규화 후 검증")
    void normalizesSpacesAndHyphens() {
        Totp totp = new Totp("SHA1", 6, 30, 1);
        byte[] secret = secret();
        String code = totp.generate(secret);
        String spaced = code.substring(0, 3) + " " + code.substring(3);
        String hyphened = code.substring(0, 3) + "-" + code.substring(3);
        assertThat(totp.verify(Base32.encode(secret), spaced)).isTrue();
        assertThat(totp.verify(Base32.encode(secret), hyphened)).isTrue();
    }

    @Test
    @DisplayName("형식 위반(자릿수/숫자/널)은 시계와 무관하게 거부")
    void rejectsMalformed() {
        Totp totp = new Totp("SHA1", 6, 30, 1);
        String base32 = Base32.encode(secret());
        assertThat(totp.verify(base32, null)).isFalse();
        assertThat(totp.verify(null, "123456")).isFalse();
        assertThat(totp.verify(base32, "")).isFalse();
        assertThat(totp.verify(base32, "12345")).isFalse(); // 자릿수 부족
        assertThat(totp.verify(base32, "1234567")).isFalse(); // 자릿수 초과
        assertThat(totp.verify(base32, "12a456")).isFalse(); // 비숫자
    }

    @Test
    @DisplayName("자릿수 설정이 다르면 거부 — 8자리 검증기에 6자리 코드")
    void rejectsWrongDigitCount() {
        Totp eightDigit = new Totp("SHA1", 8, 30, 1);
        assertThat(eightDigit.verify(Base32.encode(secret()), "123456")).isFalse();
    }
}
