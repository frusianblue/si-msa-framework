package com.company.framework.excel.model;

/**
 * 양식검증에서 발생한 오류 1건. 엑셀 화면 기준 행 번호(1-based)와 컬럼 헤더를 담아 사용자에게 그대로 안내할 수 있다.
 *
 * @param rowNumber 엑셀 행 번호(1-based, 헤더 다음 첫 데이터행이 보통 2)
 * @param columnHeader 헤더 표시 텍스트(예: "이메일")
 * @param columnKey 컬럼 키(예: "email")
 * @param rejectedValue 거부된 원본 값(문자열, 널 가능)
 * @param message 오류 메시지
 */
public record ExcelValidationError(
        int rowNumber, String columnHeader, String columnKey, String rejectedValue, String message) {

    @Override
    public String toString() {
        return rowNumber + "행 [" + columnHeader + "] " + message
                + (rejectedValue == null ? "" : " (값=" + rejectedValue + ")");
    }
}
