package com.company.framework.pdf.exporter;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.pdf.font.PdfFontProvider;
import com.company.framework.pdf.model.PdfColumn;
import com.company.framework.pdf.model.PdfLayout;
import com.company.framework.pdf.model.PdfReport;
import com.company.framework.pdf.model.PdfTextAlign;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/**
 * {@link PdfReport} 스펙을 받아 표 기반 PDF 산출물을 {@link OutputStream} 으로 생성한다(거래내역서·통지서 등).
 * 제목(중앙) → 부가정보 줄들 → 표(헤더 반복) → 하단 고지문 순으로 배치하고, 설정 시 모든 페이지 하단 중앙에 페이지 번호를 찍는다.
 *
 * <p>한글은 {@link PdfFontProvider} 가 제공하는 임베딩 폰트로 렌더된다. 익스포터는 Spring 비의존이며, 폰트/레이아웃을
 * 생성자에서 주입받는다(엑셀 {@code ExcelExporter} 와 동일 결).
 *
 * <p>스트리밍: 컨트롤러에서 {@code HttpServletResponse.getOutputStream()} 에 바로 흘려보내면 클라이언트로 직접 전송된다.
 * OpenPDF 는 표 전체를 메모리에 구성하므로 초대용량(수십만 행)에는 적합하지 않다(그 경우 분할 생성 또는 Excel 사용 권장).
 */
public class PdfExporter {

    private static final Color HEADER_BG = new Color(235, 235, 235);

    private final PdfFontProvider fonts;
    private final PdfLayout layout;

    public PdfExporter(PdfFontProvider fonts, PdfLayout layout) {
        if (fonts == null) {
            throw new IllegalArgumentException("PdfFontProvider 는 필수입니다.");
        }
        this.fonts = fonts;
        this.layout = layout == null ? PdfLayout.defaults() : layout;
    }

    /** 보고서를 PDF 로 작성해 출력 스트림으로 흘려보낸다. */
    public <T> void write(OutputStream out, PdfReport<T> report) {
        if (out == null) {
            throw new IllegalArgumentException("OutputStream 은 필수입니다.");
        }
        if (report == null) {
            throw new IllegalArgumentException("PdfReport 는 필수입니다.");
        }
        List<PdfColumn<T>> columns = report.columns();

        Rectangle pageSize = resolvePageSize(layout.pageSize(), layout.landscape());
        float margin = layout.margin();

        // try-with-resources: Document.close() 가 PDF 를 마무리(trailer/xref)하고 out 으로 flush.
        try (Document document = new Document(pageSize, margin, margin, margin, margin)) {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            if (layout.pageNumber()) {
                writer.setPageEvent(new PageNumberEvent(fonts.body(Math.max(7f, layout.bodyFontSize() - 1f))));
            }
            document.open();

            // 제목(중앙)
            Paragraph title = new Paragraph(report.title(), fonts.bold(layout.titleFontSize()));
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(12f);
            document.add(title);

            // 부가정보 줄
            Font bodyFont = fonts.body(layout.bodyFontSize());
            for (String meta : report.metaLines()) {
                document.add(new Paragraph(nullToEmpty(meta), bodyFont));
            }
            if (!report.metaLines().isEmpty()) {
                Paragraph gap = new Paragraph(" ", bodyFont);
                gap.setSpacingAfter(4f);
                document.add(gap);
            }

            // 표
            document.add(buildTable(columns, report.rows(), bodyFont));

            // 하단 고지문
            String note = report.footerNote();
            if (note != null && !note.isBlank()) {
                Paragraph p = new Paragraph(note, fonts.body(Math.max(7f, layout.bodyFontSize() - 1f)));
                p.setSpacingBefore(14f);
                document.add(p);
            }
        } catch (DocumentException e) {
            throw new BusinessException(ErrorCode.Common.INTERNAL_ERROR, "PDF 생성 실패: " + e.getMessage());
        }
    }

    private <T> PdfPTable buildTable(List<PdfColumn<T>> columns, List<T> rows, Font bodyFont) throws DocumentException {
        PdfPTable table = new PdfPTable(columns.size());
        table.setWidthPercentage(100f);
        table.setWidths(relativeWidths(columns));
        table.setHeaderRows(1); // 페이지가 넘어가도 헤더행 반복

        Font headerFont = fonts.bold(layout.headerFontSize());
        for (PdfColumn<T> column : columns) {
            PdfPCell cell = new PdfPCell(new Phrase(nullToEmpty(column.header()), headerFont));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(4f);
            cell.setBackgroundColor(HEADER_BG);
            table.addCell(cell);
        }

        for (T row : rows) {
            for (PdfColumn<T> column : columns) {
                PdfPCell cell = new PdfPCell(new Phrase(nullToEmpty(column.extract(row)), bodyFont));
                cell.setHorizontalAlignment(toElementAlign(column.align()));
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(3f);
                table.addCell(cell);
            }
        }
        return table;
    }

    private static <T> float[] relativeWidths(List<PdfColumn<T>> columns) {
        float[] widths = new float[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            widths[i] = columns.get(i).relativeWidth();
        }
        return widths;
    }

    private static int toElementAlign(PdfTextAlign align) {
        return switch (align == null ? PdfTextAlign.LEFT : align) {
            case CENTER -> Element.ALIGN_CENTER;
            case RIGHT -> Element.ALIGN_RIGHT;
            default -> Element.ALIGN_LEFT;
        };
    }

    private static Rectangle resolvePageSize(String name, boolean landscape) {
        String key = (name == null ? "A4" : name.trim().toUpperCase(Locale.ROOT));
        Rectangle base =
                switch (key) {
                    case "A5" -> PageSize.A5;
                    case "LETTER" -> PageSize.LETTER;
                    case "LEGAL" -> PageSize.LEGAL;
                    default -> PageSize.A4;
                };
        // PageSize.* 는 공유 상수이므로 새 Rectangle 로 복제(회전 포함). landscape 면 가로.
        return landscape ? new Rectangle(base).rotate() : new Rectangle(base);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** 모든 페이지 하단 중앙에 "- N -" 페이지 번호를 찍는 이벤트. */
    private static final class PageNumberEvent extends PdfPageEventHelper {
        private final Font font;

        private PageNumberEvent(Font font) {
            this.font = font;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Rectangle page = document.getPageSize();
            float x = (page.getLeft() + page.getRight()) / 2f;
            float y = page.getBottom() + 18f;
            ColumnText.showTextAligned(
                    writer.getDirectContent(),
                    Element.ALIGN_CENTER,
                    new Phrase("- " + writer.getPageNumber() + " -", font),
                    x,
                    y,
                    0f);
        }
    }
}
