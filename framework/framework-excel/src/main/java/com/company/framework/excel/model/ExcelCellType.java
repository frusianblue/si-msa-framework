package com.company.framework.excel.model;

/**
 * 업로드(import) 양식검증에서 셀 값을 해석/검증할 타입.
 *
 * <p>POI 의 {@code org.apache.poi.ss.usermodel.CellType} 과 혼동을 피하려고 별도 enum 으로 둔다(프레임워크 공개 타입).
 * 숫자/날짜는 POI 가 셀 포맷대로 해석하고, 그 외는 문자열로 읽어 파싱한다.
 */
public enum ExcelCellType {
    /** 문자열. maxLength/pattern 검증 대상. */
    STRING,
    /** 정수(Long). 숫자 셀이면 정수부, 문자열이면 Long.parseLong. */
    LONG,
    /** 실수(Double). */
    DOUBLE,
    /** 정밀 십진(BigDecimal) — 금액 등. */
    BIG_DECIMAL,
    /** 불리언. true/false, Y/N, 1/0 허용. */
    BOOLEAN,
    /** 날짜(LocalDate). 날짜 서식 셀 또는 ISO-8601(yyyy-MM-dd) 문자열. */
    LOCAL_DATE,
    /** 일시(LocalDateTime). 날짜 서식 셀 또는 ISO-8601 문자열. */
    LOCAL_DATE_TIME
}
