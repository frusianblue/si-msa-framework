package com.company.framework.pdf.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PDF 산출물 1건의 "내용" 스펙(레이아웃은 {@link PdfLayout}/프로퍼티가 담당). 제목 + 부가정보(생성일시·기간 등) +
 * 표(컬럼 정의 + 행 데이터) + 하단 고지문으로 구성한다.
 *
 * <pre>{@code
 * PdfReport<Tx> report = PdfReport.<Tx>builder()
 *     .title("거래내역서")
 *     .metaLine("계좌: 123-456-7890")
 *     .metaLine("기간: 2026-01-01 ~ 2026-01-31")
 *     .column(PdfColumn.of("거래일시", t -> t.getDate().toString()))
 *     .column(PdfColumn.of("적요", Tx::getDesc, 2f))
 *     .column(PdfColumn.of("금액", t -> MoneyUtils.format(t.getAmount()), 1f, PdfTextAlign.RIGHT))
 *     .rows(txList)
 *     .footerNote("본 내역서는 안내용이며 법적 효력이 없습니다.")
 *     .build();
 * pdfExporter.write(response.getOutputStream(), report);
 * }</pre>
 *
 * @param <T> 행 객체 타입
 */
public final class PdfReport<T> {

    private final String title;
    private final List<String> metaLines;
    private final List<PdfColumn<T>> columns;
    private final List<T> rows;
    private final String footerNote;

    private PdfReport(Builder<T> b) {
        if (b.title == null || b.title.isBlank()) {
            throw new IllegalArgumentException("PdfReport.title 은 필수입니다.");
        }
        if (b.columns.isEmpty()) {
            throw new IllegalArgumentException("PdfReport.columns 가 비어 있습니다(최소 1개).");
        }
        this.title = b.title;
        this.metaLines = Collections.unmodifiableList(new ArrayList<>(b.metaLines));
        this.columns = Collections.unmodifiableList(new ArrayList<>(b.columns));
        this.rows = Collections.unmodifiableList(new ArrayList<>(b.rows));
        this.footerNote = b.footerNote;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public String title() {
        return title;
    }

    public List<String> metaLines() {
        return metaLines;
    }

    public List<PdfColumn<T>> columns() {
        return columns;
    }

    public List<T> rows() {
        return rows;
    }

    /** 표 아래 고지문(선택). null/blank 이면 출력하지 않음. */
    public String footerNote() {
        return footerNote;
    }

    /** PdfReport 빌더. columns/rows 는 누적, metaLine 은 한 줄씩 추가. */
    public static final class Builder<T> {
        private String title;
        private final List<String> metaLines = new ArrayList<>();
        private final List<PdfColumn<T>> columns = new ArrayList<>();
        private final List<T> rows = new ArrayList<>();
        private String footerNote;

        public Builder<T> title(String title) {
            this.title = title;
            return this;
        }

        public Builder<T> metaLine(String line) {
            if (line != null) {
                this.metaLines.add(line);
            }
            return this;
        }

        public Builder<T> column(PdfColumn<T> column) {
            if (column != null) {
                this.columns.add(column);
            }
            return this;
        }

        public Builder<T> columns(List<PdfColumn<T>> cols) {
            if (cols != null) {
                this.columns.addAll(cols);
            }
            return this;
        }

        public Builder<T> rows(List<T> data) {
            if (data != null) {
                this.rows.addAll(data);
            }
            return this;
        }

        public Builder<T> footerNote(String footerNote) {
            this.footerNote = footerNote;
            return this;
        }

        public PdfReport<T> build() {
            return new PdfReport<>(this);
        }
    }
}
