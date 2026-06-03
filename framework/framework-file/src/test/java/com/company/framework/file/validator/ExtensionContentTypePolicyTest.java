package com.company.framework.file.validator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExtensionContentTypePolicyTest {

    private final ExtensionContentTypePolicy policy = ExtensionContentTypePolicy.withDefaults();

    @Test
    @DisplayName("정상 정합: 확장자와 검출 MIME 계열 일치")
    void consistent() {
        assertThat(policy.isConsistent("png", "image/png")).isTrue();
        assertThat(policy.isConsistent("jpg", "image/jpeg")).isTrue();
        assertThat(policy.isConsistent("pdf", "application/pdf")).isTrue();
        assertThat(policy.isConsistent("csv", "text/plain")).isTrue();
        assertThat(policy.isConsistent("PNG", "image/png")).isTrue(); // 대소문자 정규화
    }

    @Test
    @DisplayName("명백한 위장 차단: png 인데 PDF/실행파일")
    void rejectsDisguise() {
        assertThat(policy.isConsistent("png", "application/pdf")).isFalse();
        assertThat(policy.isConsistent("png", "application/x-dosexec")).isFalse();
        assertThat(policy.isConsistent("pdf", "image/png")).isFalse();
    }

    @Test
    @DisplayName("컨테이너 계열은 통과: docx 가 zip/다른 OOXML 로 검출돼도 허용(오탐 방지)")
    void containerFamilyPasses() {
        assertThat(policy.isConsistent("docx", "application/zip")).isTrue();
        assertThat(policy.isConsistent("docx", "application/x-tika-ooxml")).isTrue();
        assertThat(policy.isConsistent("docx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .isTrue();
        assertThat(policy.isConsistent("hwp", "application/x-tika-msoffice")).isTrue();
    }

    @Test
    @DisplayName("규칙 없는 확장자는 통과(1차 화이트리스트가 이미 거름)")
    void noRulePasses() {
        assertThat(policy.hasRule("bin")).isFalse();
        assertThat(policy.isConsistent("bin", "application/octet-stream")).isTrue();
    }

    @Test
    @DisplayName("null 안전")
    void nullSafe() {
        assertThat(policy.isConsistent(null, "image/png")).isTrue();
        assertThat(policy.isConsistent("png", null)).isTrue();
    }
}
