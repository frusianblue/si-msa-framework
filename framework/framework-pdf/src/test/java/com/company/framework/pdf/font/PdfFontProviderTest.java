package com.company.framework.pdf.font;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PdfFontProvider} 의 폴백/견고성: 폰트 미설정과 깨진 바이트 모두 예외 없이 라틴 폴백으로 동작해야 한다
 * (PDF 생성이 폰트 문제로 통째로 실패하면 안 됨 — 운영 경고 후 라틴으로라도 생성).
 */
class PdfFontProviderTest {

    @Test
    @DisplayName("폰트 미설정(null) → 임베딩 없음 + 폴백 폰트 제공")
    void nullBytesFallsBackToLatin() {
        PdfFontProvider provider = new PdfFontProvider(null, null);

        assertThat(provider.hasEmbeddedFont()).isFalse();
        assertThat(provider.body(10f)).isNotNull();
        assertThat(provider.bold(12f)).isNotNull();
    }

    @Test
    @DisplayName("깨진 폰트 바이트 → 예외 없이 폴백(hasEmbeddedFont=false)")
    void invalidBytesFallsBackGracefully() {
        PdfFontProvider provider = new PdfFontProvider(new byte[] {1, 2, 3, 4, 5}, "bad.ttf");

        assertThat(provider.hasEmbeddedFont()).isFalse();
        assertThat(provider.body(9f)).isNotNull();
    }
}
