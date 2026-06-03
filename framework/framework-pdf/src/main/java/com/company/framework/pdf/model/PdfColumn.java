package com.company.framework.pdf.model;

import java.util.function.Function;

/**
 * 표(table) 컬럼 1개 정의. 헤더 텍스트 + 행 객체에서 셀 문자열을 뽑는 추출 함수 + (선택) 상대 너비/정렬.
 *
 * <pre>{@code
 * List<PdfColumn<Tx>> columns = List.of(
 *     PdfColumn.of("거래일시", t -> t.getDate().toString()),
 *     PdfColumn.of("적요", Tx::getDesc, 2.0f),                         // 상대 너비 2배
 *     PdfColumn.of("금액", t -> MoneyUtils.format(t.getAmount()), 1.0f, PdfTextAlign.RIGHT));
 * }</pre>
 *
 * <p>엑셀의 {@code ExcelColumn} 과 같은 결이되, PDF 는 텍스트 렌더이므로 추출 함수가 {@code String} 을 반환한다
 * (숫자/날짜 포맷은 호출측 책임 — {@code MoneyUtils}/{@code DateUtils} 활용).
 *
 * @param <T> 행 객체 타입
 */
public final class PdfColumn<T> {

    private final String header;
    private final Function<T, String> valueExtractor;
    private final float relativeWidth;
    private final PdfTextAlign align;

    private PdfColumn(String header, Function<T, String> valueExtractor, float relativeWidth, PdfTextAlign align) {
        if (header == null) {
            throw new IllegalArgumentException("PdfColumn.header 는 필수입니다.");
        }
        if (valueExtractor == null) {
            throw new IllegalArgumentException("PdfColumn.valueExtractor 는 필수입니다.");
        }
        if (relativeWidth <= 0f) {
            throw new IllegalArgumentException("PdfColumn.relativeWidth 는 0 보다 커야 합니다.");
        }
        this.header = header;
        this.valueExtractor = valueExtractor;
        this.relativeWidth = relativeWidth;
        this.align = align == null ? PdfTextAlign.LEFT : align;
    }

    /** 헤더 + 값 추출 함수(상대 너비 1, 좌측 정렬). */
    public static <T> PdfColumn<T> of(String header, Function<T, String> valueExtractor) {
        return new PdfColumn<>(header, valueExtractor, 1.0f, PdfTextAlign.LEFT);
    }

    /** 헤더 + 값 추출 함수 + 상대 너비. */
    public static <T> PdfColumn<T> of(String header, Function<T, String> valueExtractor, float relativeWidth) {
        return new PdfColumn<>(header, valueExtractor, relativeWidth, PdfTextAlign.LEFT);
    }

    /** 헤더 + 값 추출 함수 + 상대 너비 + 정렬. */
    public static <T> PdfColumn<T> of(
            String header, Function<T, String> valueExtractor, float relativeWidth, PdfTextAlign align) {
        return new PdfColumn<>(header, valueExtractor, relativeWidth, align);
    }

    public String header() {
        return header;
    }

    /** 행 객체에서 셀 문자열을 추출. null 은 호출측({@code PdfExporter})에서 빈 문자열로 처리. */
    public String extract(T row) {
        return valueExtractor.apply(row);
    }

    public float relativeWidth() {
        return relativeWidth;
    }

    public PdfTextAlign align() {
        return align;
    }
}
