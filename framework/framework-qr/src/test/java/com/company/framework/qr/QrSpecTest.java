package com.company.framework.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** QrSpec 불변/검증 — 순수(ZXing 무의존). */
class QrSpecTest {

    @Test
    @DisplayName("defaults: 256px · margin 4 · ECC M · UTF-8 · 검정/흰색")
    void defaults() {
        QrSpec s = QrSpec.defaults();
        assertThat(s.sizePx()).isEqualTo(256);
        assertThat(s.margin()).isEqualTo(4);
        assertThat(s.eccLevel()).isEqualTo(QrEccLevel.M);
        assertThat(s.charset()).isEqualTo("UTF-8");
        assertThat(s.darkColor()).isEqualTo(0x000000);
        assertThat(s.lightColor()).isEqualTo(0xFFFFFF);
    }

    @Test
    @DisplayName("builder 로 개별 값 오버라이드")
    void builderOverrides() {
        QrSpec s = QrSpec.builder()
                .sizePx(512)
                .margin(2)
                .eccLevel(QrEccLevel.H)
                .charset("ISO-8859-1")
                .darkColor(0x123456)
                .lightColor(0xFEDCBA)
                .build();
        assertThat(s.sizePx()).isEqualTo(512);
        assertThat(s.margin()).isEqualTo(2);
        assertThat(s.eccLevel()).isEqualTo(QrEccLevel.H);
        assertThat(s.charset()).isEqualTo("ISO-8859-1");
        assertThat(s.darkColor()).isEqualTo(0x123456);
        assertThat(s.lightColor()).isEqualTo(0xFEDCBA);
    }

    @Test
    @DisplayName("sizePx 0/음수/상한초과 거부")
    void rejectsBadSize() {
        assertThatThrownBy(() -> QrSpec.builder().sizePx(0).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QrSpec.builder().sizePx(-1).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QrSpec.builder().sizePx(QrSpec.MAX_SIZE_PX + 1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("margin 음수/상한초과 거부")
    void rejectsBadMargin() {
        assertThatThrownBy(() -> QrSpec.builder().margin(-1).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QrSpec.builder().margin(QrSpec.MAX_MARGIN + 1).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("eccLevel/charset null·blank 거부")
    void rejectsNulls() {
        assertThatThrownBy(() -> QrSpec.builder().eccLevel(null).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QrSpec.builder().charset(null).build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QrSpec.builder().charset("  ").build()).isInstanceOf(IllegalArgumentException.class);
    }
}
