package com.company.framework.logmask.mask;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 마스킹 엔진/규칙 단위 검증(Spring·Logback 무의존, 순수 JDK). 탐지 정규식과 core MaskingUtils 위임 형식,
 * 옵션(stripNewlines·maxLength), 커스텀 패턴, 경계(null/빈/비PII)를 확인한다.
 */
class SensitiveDataMaskerTest {

    private final SensitiveDataMasker masker = SensitiveDataMasker.withDefaults();

    @Test
    @DisplayName("주민등록번호를 뒤 6자리 마스킹한다")
    void masksResidentNumber() {
        assertThat(masker.mask("주민 900101-1234567 끝")).isEqualTo("주민 900101-1****** 끝");
    }

    @Test
    @DisplayName("휴대폰 가운데 4자리를 마스킹한다")
    void masksPhone() {
        assertThat(masker.mask("전화 010-1234-5678 입니다")).isEqualTo("전화 010-****-5678 입니다");
    }

    @Test
    @DisplayName("카드번호 가운데 8자리를 마스킹한다")
    void masksCard() {
        assertThat(masker.mask("카드 1234-5678-9012-3456 결제")).isEqualTo("카드 1234-****-****-3456 결제");
    }

    @Test
    @DisplayName("이메일 아이디 앞 2자리만 남긴다")
    void masksEmail() {
        assertThat(masker.mask("메일 gildong@example.com 로")).isEqualTo("메일 gi*****@example.com 로");
    }

    @Test
    @DisplayName("계좌번호는 기본 규칙에서 마스킹하지 않는다(오탐 방지로 기본 off)")
    void accountNotMaskedByDefault() {
        assertThat(masker.mask("계좌 110-1234-567890 임")).isEqualTo("계좌 110-1234-567890 임");
    }

    @Test
    @DisplayName("계좌 규칙을 켜면 가운데를 마스킹한다")
    void masksAccountWhenEnabled() {
        SensitiveDataMasker withAccount = new SensitiveDataMasker(List.of(KoreanPiiRules.account()), true, 0);
        assertThat(withAccount.mask("계좌 110-1234-567890 임")).isEqualTo("계좌 110*******890 임");
    }

    @Test
    @DisplayName("한 줄에 여러 종류가 섞여도 모두 마스킹한다")
    void masksMultipleInOneLine() {
        String in = "user gildong@example.com phone 010-1234-5678 rrn 900101-1234567";
        assertThat(masker.mask(in)).isEqualTo("user gi*****@example.com phone 010-****-5678 rrn 900101-1******");
    }

    @Test
    @DisplayName("stripNewlines=true 면 CR/LF 를 공백으로 바꿔 로그 인젝션을 막는다")
    void stripsNewlines() {
        SensitiveDataMasker m = new SensitiveDataMasker(List.of(), true, 0);
        assertThat(m.mask("line1\nline2\rline3")).isEqualTo("line1 line2 line3");
    }

    @Test
    @DisplayName("maxLength 양수면 그 길이로 자르고 표식을 붙인다")
    void truncatesWhenMaxLength() {
        SensitiveDataMasker m = new SensitiveDataMasker(List.of(), false, 5);
        assertThat(m.mask("abcdefghij")).isEqualTo("abcde...(truncated)");
    }

    @Test
    @DisplayName("커스텀 패턴은 매칭 전체를 별표로 가린다")
    void masksCustomPattern() {
        SensitiveDataMasker m = new SensitiveDataMasker(List.of(MaskingRule.fullMask("emp", "EMP\\d{6}")), true, 0);
        assertThat(m.mask("사번 EMP123456 확인")).isEqualTo("사번 ********* 확인");
    }

    @Test
    @DisplayName("null 은 null, 빈 문자열은 빈 문자열")
    void handlesNullAndEmpty() {
        assertThat(masker.mask(null)).isNull();
        assertThat(masker.mask("")).isEmpty();
    }

    @Test
    @DisplayName("개인정보가 없으면 원문을 그대로 둔다")
    void leavesNonPiiUnchanged() {
        assertThat(masker.mask("평범한 로그 메시지 코드 12345")).isEqualTo("평범한 로그 메시지 코드 12345");
    }

    @Test
    @DisplayName("ruleNames 는 적용 순서를 반영한다")
    void exposesRuleNamesInOrder() {
        assertThat(masker.ruleNames()).containsExactly("card", "rrn", "phone", "email");
    }
}
