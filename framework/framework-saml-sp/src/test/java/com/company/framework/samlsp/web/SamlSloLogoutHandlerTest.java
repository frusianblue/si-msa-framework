package com.company.framework.samlsp.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * SLO 핸들러의 순수 경로 파싱 검증(servlet/SS 무의존). registrationId 는 {@code /logout/saml2/slo/{id}} 끝 세그먼트에서.
 */
class SamlSloLogoutHandlerTest {

    @Test
    void extractsRegistrationIdFromSloPath() {
        assertThat(SamlSloLogoutHandler.registrationIdFromUri("/logout/saml2/slo/corp"))
                .isEqualTo("corp");
        assertThat(SamlSloLogoutHandler.registrationIdFromUri("/app/logout/saml2/slo/idp-1"))
                .isEqualTo("idp-1");
    }

    @Test
    void trailingSlashIgnored() {
        assertThat(SamlSloLogoutHandler.registrationIdFromUri("/logout/saml2/slo/corp/"))
                .isEqualTo("corp");
        assertThat(SamlSloLogoutHandler.registrationIdFromUri("/logout/saml2/slo/corp///"))
                .isEqualTo("corp");
    }

    @Test
    void sloWithoutRegistrationIdIsNull() {
        assertThat(SamlSloLogoutHandler.registrationIdFromUri("/logout/saml2/slo"))
                .isNull();
        assertThat(SamlSloLogoutHandler.registrationIdFromUri("/logout/saml2/slo/"))
                .isNull();
    }

    @Test
    void nullAndEdgeUris() {
        assertThat(SamlSloLogoutHandler.registrationIdFromUri(null)).isNull();
        assertThat(SamlSloLogoutHandler.registrationIdFromUri("")).isNull();
        assertThat(SamlSloLogoutHandler.registrationIdFromUri("/")).isNull();
        assertThat(SamlSloLogoutHandler.registrationIdFromUri("corp")).isNull(); // 슬래시 없으면 경로 세그먼트 없음 → null
    }
}
