package com.company.framework.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 개인정보 마스킹 회귀 테스트(개인정보보호 대응). 정상 케이스의 정확한 출력과 경계(짧은 입력/널/형식불일치)를
 * 알려진 정답으로 고정한다. "틀려도 조용히 흘러가는" 마스킹 누락을 회귀로 잡기 위함.
 */
class MaskingUtilsTest {

    @Test
    @DisplayName("이름 — 가운데 마스킹, 2글자/긴 이름/경계")
    void name() {
        assertThat(MaskingUtils.maskName("홍길동")).isEqualTo("홍*동");
        assertThat(MaskingUtils.maskName("남궁민수")).isEqualTo("남**수");
        assertThat(MaskingUtils.maskName("김철")).isEqualTo("김*");
        assertThat(MaskingUtils.maskName("이")).isEqualTo("이"); // 1글자는 그대로
        assertThat(MaskingUtils.maskName(null)).isNull();
    }

    @Test
    @DisplayName("이메일 — 아이디 앞 2자리만 노출")
    void email() {
        assertThat(MaskingUtils.maskEmail("gildong@x.com")).isEqualTo("gi*****@x.com");
        assertThat(MaskingUtils.maskEmail("ab@x.com")).isEqualTo("**@x.com");
        assertThat(MaskingUtils.maskEmail("a@x.com")).isEqualTo("*@x.com");
        assertThat(MaskingUtils.maskEmail("no-at-sign")).isEqualTo("no-at-sign"); // @ 없으면 그대로
        assertThat(MaskingUtils.maskEmail(null)).isNull();
    }

    @Test
    @DisplayName("휴대폰 — 가운데 4자리 마스킹(하이픈 유무 무관)")
    void phone() {
        assertThat(MaskingUtils.maskPhone("010-1234-5678")).isEqualTo("010-****-5678");
        assertThat(MaskingUtils.maskPhone("01012345678")).isEqualTo("010-****-5678");
        assertThat(MaskingUtils.maskPhone(null)).isNull();
    }

    @Test
    @DisplayName("주민등록번호 — 생년월일+성별 1자리만, 하이픈 유무 무관")
    void residentNo() {
        assertThat(MaskingUtils.maskResidentNo("9001011234567")).isEqualTo("900101-1******");
        assertThat(MaskingUtils.maskResidentNo("900101-1234567")).isEqualTo("900101-1******");
        assertThat(MaskingUtils.maskResidentNo("12345")).isEqualTo("12345"); // 13자리 아니면 그대로
        assertThat(MaskingUtils.maskResidentNo(null)).isNull();
    }

    @Test
    @DisplayName("카드번호 — 앞4·뒤4만 노출")
    void card() {
        assertThat(MaskingUtils.maskCard("1234567812345678")).isEqualTo("1234-****-****-5678");
        assertThat(MaskingUtils.maskCard("1234-5678-9012-3456")).isEqualTo("1234-****-****-3456");
        assertThat(MaskingUtils.maskCard("12345")).isEqualTo("12345"); // 길이 범위 밖이면 그대로
        assertThat(MaskingUtils.maskCard(null)).isNull();
    }

    @Test
    @DisplayName("계좌번호 — 앞3·뒤3만 노출, 가운데 마스킹")
    void account() {
        assertThat(MaskingUtils.maskAccount("110-1234-567890")).isEqualTo("110*******890");
        assertThat(MaskingUtils.maskAccount("123456")).isEqualTo("******"); // 6자리 이하 전체 마스킹
        assertThat(MaskingUtils.maskAccount(null)).isNull();
    }

    @Test
    @DisplayName("주소 — 앞 2토큰만 노출, 상세주소 마스킹")
    void address() {
        assertThat(MaskingUtils.maskAddress("서울시 강남구 테헤란로 123")).isEqualTo("서울시 강남구 ***");
        assertThat(MaskingUtils.maskAddress("서울시 강남구")).isEqualTo("서울시 강남구"); // 2토큰 이하면 그대로
        assertThat(MaskingUtils.maskAddress("  ")).isEqualTo("  "); // 공백은 그대로
        assertThat(MaskingUtils.maskAddress(null)).isNull();
    }
}
