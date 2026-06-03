package com.company.framework.pdf.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.pdf.font.PdfFontProvider;
import com.company.framework.pdf.model.PdfColumn;
import com.company.framework.pdf.model.PdfLayout;
import com.company.framework.pdf.model.PdfReport;
import com.company.framework.pdf.model.PdfTextAlign;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PdfExporter} 가 유효한 PDF 바이트(매직 헤더 {@code %PDF-} ~ trailer {@code %%EOF})를 생성하는지 검증한다.
 * 폰트는 라틴 폴백({@code PdfFontProvider(null, null)}) 으로 충분하다 — 구조 검증이라 별도 TTF 가 필요 없다.
 */
class PdfExporterTest {

    private record Tx(String date, String desc, String amount) {}

    private PdfExporter exporter() {
        return new PdfExporter(new PdfFontProvider(null, null), PdfLayout.defaults());
    }

    private PdfReport<Tx> sampleReport() {
        return PdfReport.<Tx>builder()
                .title("거래내역서")
                .metaLine("계좌: 123-456-7890")
                .metaLine("기간: 2026-01-01 ~ 2026-01-31")
                .column(PdfColumn.of("거래일시", Tx::date, 1.5f))
                .column(PdfColumn.of("적요", Tx::desc, 2f))
                .column(PdfColumn.of("금액", Tx::amount, 1f, PdfTextAlign.RIGHT))
                .rows(List.of(
                        new Tx("2026-01-03 10:21", "ATM deposit", "1,000,000"),
                        new Tx("2026-01-07 14:05", "card payment", "-23,400"),
                        new Tx("2026-01-15 09:00", "interest", "12")))
                .footerNote("info only")
                .build();
    }

    @Test
    @DisplayName("보고서 → 유효한 PDF 바이트 생성(%PDF- 헤더 + %%EOF trailer)")
    void writesValidPdfBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        exporter().write(out, sampleReport());

        byte[] pdf = out.toByteArray();
        assertThat(pdf.length).isGreaterThan(100);
        String head = new String(pdf, 0, 5, StandardCharsets.ISO_8859_1);
        assertThat(head).isEqualTo("%PDF-");
        String tail = new String(pdf, StandardCharsets.ISO_8859_1);
        assertThat(tail).contains("%%EOF");
    }

    @Test
    @DisplayName("가로(A5 landscape) + 페이지번호 off 조합도 정상 생성")
    void writesWithAlternateLayout() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfExporter ex =
                new PdfExporter(new PdfFontProvider(null, null), new PdfLayout("A5", true, 24f, 14f, 9f, 8f, false));

        ex.write(out, sampleReport());

        assertThat(new String(out.toByteArray(), 0, 5, StandardCharsets.ISO_8859_1))
                .isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("OutputStream/Report null 은 IllegalArgumentException")
    void rejectsNullArgs() {
        PdfExporter ex = exporter();
        assertThatThrownBy(() -> ex.write(null, sampleReport())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ex.write(new ByteArrayOutputStream(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("PdfReport 는 제목/컬럼이 없으면 빌드 단계에서 거부")
    void reportRequiresTitleAndColumns() {
        assertThatThrownBy(() -> PdfReport.<Tx>builder()
                        .column(PdfColumn.of("x", Tx::date))
                        .build())
                .isInstanceOf(IllegalArgumentException.class); // title 없음
        assertThatThrownBy(() -> PdfReport.<Tx>builder().title("t").build())
                .isInstanceOf(IllegalArgumentException.class); // column 없음
    }
}
