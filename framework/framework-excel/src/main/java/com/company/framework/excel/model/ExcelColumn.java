package com.company.framework.excel.model;

import java.util.function.Function;

/**
 * 다운로드(export) 컬럼 1개 정의. 헤더 텍스트와 행 객체에서 셀 값을 뽑는 추출 함수로 구성한다.
 *
 * <pre>{@code
 * List<ExcelColumn<Member>> columns = List.of(
 *     ExcelColumn.of("이름", Member::getName),
 *     ExcelColumn.of("나이", Member::getAge),
 *     ExcelColumn.of("가입일", Member::getJoinedAt));
 * }</pre>
 *
 * @param <T> 행 객체 타입
 */
public final class ExcelColumn<T> {

    private final String header;
    private final Function<T, Object> valueExtractor;
    private final Integer width;

    private ExcelColumn(String header, Function<T, Object> valueExtractor, Integer width) {
        if (header == null) {
            throw new IllegalArgumentException("ExcelColumn.header 는 필수입니다.");
        }
        if (valueExtractor == null) {
            throw new IllegalArgumentException("ExcelColumn.valueExtractor 는 필수입니다.");
        }
        this.header = header;
        this.valueExtractor = valueExtractor;
        this.width = width;
    }

    /** 헤더 + 값 추출 함수. */
    public static <T> ExcelColumn<T> of(String header, Function<T, Object> valueExtractor) {
        return new ExcelColumn<>(header, valueExtractor, null);
    }

    /** 헤더 + 값 추출 함수 + 열 너비(문자 수 기준). */
    public static <T> ExcelColumn<T> of(String header, Function<T, Object> valueExtractor, int widthInChars) {
        return new ExcelColumn<>(header, valueExtractor, widthInChars);
    }

    public String header() {
        return header;
    }

    public Object extract(T row) {
        return valueExtractor.apply(row);
    }

    public Integer width() {
        return width;
    }
}
