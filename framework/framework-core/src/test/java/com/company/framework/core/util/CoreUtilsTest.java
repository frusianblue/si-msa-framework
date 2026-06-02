package com.company.framework.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * SI 공통 유틸 회귀 테스트. 체크섬/금액/한글 조합 등 "틀리면 조용히 잘못되는" 로직을 알려진 정답으로 고정한다.
 */
class CoreUtilsTest {

    @Test
    @DisplayName("등록번호 체크섬 검증")
    void regNo() {
        assertThat(KoreanRegNoUtils.isValidBusinessNo("124-81-00998")).isTrue();
        assertThat(KoreanRegNoUtils.isValidBusinessNo("124-81-00997")).isFalse();
        assertThat(KoreanRegNoUtils.isValidCorporateNo("130111-0006246")).isTrue();
        assertThat(KoreanRegNoUtils.isValidCorporateNo("130111-0006245")).isFalse();
        assertThat(KoreanRegNoUtils.isValidResidentNo("12345")).isFalse();
    }

    @Test
    @DisplayName("형식/Luhn 검증")
    void validation() {
        assertThat(ValidationUtils.isEmail("user@example.com")).isTrue();
        assertThat(ValidationUtils.isEmail("bad@@x")).isFalse();
        assertThat(ValidationUtils.isMobile("010-1234-5678")).isTrue();
        assertThat(ValidationUtils.isMobile("010123456789")).isFalse();
        assertThat(ValidationUtils.isValidCardNumber("4111-1111-1111-1111")).isTrue();
        assertThat(ValidationUtils.isValidCardNumber("1234-5678-1234-5678")).isFalse();
    }

    @Test
    @DisplayName("금액 한글/콤마")
    void money() {
        assertThat(MoneyUtils.toKoreanNumber(123456)).isEqualTo("십이만삼천사백오십육");
        assertThat(MoneyUtils.toKoreanNumber(10000)).isEqualTo("일만");
        assertThat(MoneyUtils.toKoreanNumber(0)).isEqualTo("영");
        assertThat(MoneyUtils.toKoreanAmount(50000)).isEqualTo("일금 오만원정");
        assertThat(MoneyUtils.comma(1234567)).isEqualTo("1,234,567");
    }

    @Test
    @DisplayName("한글 초성/조사/자모조합/한영변환")
    void hangul() {
        assertThat(HangulUtils.chosung("안녕하세요")).isEqualTo("ㅇㄴㅎㅅㅇ");
        assertThat(HangulUtils.matchesChosung("사과", "ㅅㄱ")).isTrue();
        assertThat(HangulUtils.josa("사과", "을", "를")).isEqualTo("사과를");
        assertThat(HangulUtils.josa("사람", "을", "를")).isEqualTo("사람을");
        assertThat(HangulUtils.decompose("값")).isEqualTo("ㄱㅏㅂㅅ");
        assertThat(HangulUtils.compose("ㄷㅏㄹㄱㅏ")).isEqualTo("달가");
        assertThat(HangulUtils.engToKor("dkssudgktpdy")).isEqualTo("안녕하세요");
    }

    @Test
    @DisplayName("해시/인코딩")
    void hash() {
        assertThat(HashUtils.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(HashUtils.base64Encode("test".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isEqualTo("dGVzdA==");
    }

    @Test
    @DisplayName("마스킹")
    void masking() {
        assertThat(MaskingUtils.maskResidentNo("9001011234567")).isEqualTo("900101-1******");
        assertThat(MaskingUtils.maskCard("1234567812345678")).isEqualTo("1234-****-****-5678");
        assertThat(MaskingUtils.maskAddress("서울시 강남구 테헤란로 123")).isEqualTo("서울시 강남구 ***");
    }

    @Test
    @DisplayName("영업일 계산 — 고정공휴일+주말+주입 휴일")
    void businessDays() {
        // 2025-01-01(수, 신정) 고정공휴일
        assertThat(HolidayUtils.isFixedSolarHoliday(LocalDate.of(2025, 1, 1))).isTrue();
        // 2025-01-04(토)·05(일) 주말
        assertThat(HolidayUtils.isWeekend(LocalDate.of(2025, 1, 4))).isTrue();
        // 주입 휴일(예: 음력 공휴일 자리)을 더해 영업일에서 제외
        Set<LocalDate> extra = Set.of(LocalDate.of(2025, 1, 2));
        // 1/1(공휴일)·1/2(주입)·1/4·1/5(주말) 제외 → 1/3(금)이 1/1 다음 영업일
        assertThat(HolidayUtils.nextBusinessDay(LocalDate.of(2025, 1, 1), extra))
                .isEqualTo(LocalDate.of(2025, 1, 3));
    }
}
