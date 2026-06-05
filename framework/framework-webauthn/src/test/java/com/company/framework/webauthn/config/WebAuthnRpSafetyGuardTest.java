package com.company.framework.webauthn.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link WebAuthnRpSafetyGuard#diagnose} 단위 테스트. rpId/origin 일원화 정책의 정합/위반 판정을 검증한다
 * (실제 부팅·프로파일은 통합 테스트 영역, 여기서는 순수 진단 로직만).
 */
class WebAuthnRpSafetyGuardTest {

    @Test
    void prod_정상_상위도메인_rpId와_서브도메인_origin() {
        assertThat(WebAuthnRpSafetyGuard.diagnose(
                        "example.com", List.of("https://app.example.com", "https://admin.example.com"), true))
                .isNull();
    }

    @Test
    void prod_정상_rpId와_동일_origin() {
        assertThat(WebAuthnRpSafetyGuard.diagnose("example.com", List.of("https://example.com"), true))
                .isNull();
    }

    @Test
    void 로컬_정상_localhost_rpId와_http_localhost_origin() {
        assertThat(WebAuthnRpSafetyGuard.diagnose("localhost", List.of("http://localhost:5173"), false))
                .isNull();
    }

    @Test
    void 비prod_origins_비어있으면_허용() {
        assertThat(WebAuthnRpSafetyGuard.diagnose("localhost", List.of(), false))
                .isNull();
    }

    @Test
    void rpId_비어있으면_거부() {
        assertThat(WebAuthnRpSafetyGuard.diagnose("  ", List.of("https://app.example.com"), false))
                .isNotNull();
    }

    @Test
    void prod_localhost_rpId_거부() {
        assertThat(WebAuthnRpSafetyGuard.diagnose("localhost", List.of("https://localhost"), true))
                .contains("localhost");
    }

    @Test
    void prod_origins_비어있으면_거부() {
        assertThat(WebAuthnRpSafetyGuard.diagnose("example.com", List.of(), true))
                .contains("allowed-origins");
    }

    @Test
    void prod_http_origin은_거부_localhost_제외() {
        assertThat(WebAuthnRpSafetyGuard.diagnose("example.com", List.of("http://app.example.com"), true))
                .contains("https");
    }

    @Test
    void origin_host가_rpId와_무관하면_거부() {
        assertThat(WebAuthnRpSafetyGuard.diagnose("example.com", List.of("https://app.other.com"), true))
                .contains("등록가능 도메인");
    }

    @Test
    void origin_형식오류_거부() {
        assertThat(WebAuthnRpSafetyGuard.diagnose("example.com", List.of("app.example.com"), true))
                .isNotNull(); // scheme 없음
    }
}
