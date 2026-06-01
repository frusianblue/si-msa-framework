package com.company.framework.excel.model;

import java.util.Collections;
import java.util.List;

/**
 * 업로드 파싱+검증 결과.
 *
 * <ul>
 *   <li>{@link #rows()} — 검증을 통과한 행만 담긴다(오류 있는 행은 제외).
 *   <li>{@link #errors()} — 수집된 모든 검증 오류(행/컬럼 단위).
 *   <li>{@link #hasErrors()} — 오류가 하나라도 있으면 true.
 * </ul>
 *
 * <p>업무 코드는 보통 {@code if (result.hasErrors()) { 오류 응답 } else { 저장 }} 형태로 쓴다.
 * 부분 저장이 필요하면 {@code rows()} 만 저장하고 {@code errors()} 를 함께 반환할 수도 있다.
 */
public final class ExcelImportResult {

    private final List<ExcelRow> rows;
    private final List<ExcelValidationError> errors;

    public ExcelImportResult(List<ExcelRow> rows, List<ExcelValidationError> errors) {
        this.rows = Collections.unmodifiableList(rows);
        this.errors = Collections.unmodifiableList(errors);
    }

    public List<ExcelRow> rows() {
        return rows;
    }

    public List<ExcelValidationError> errors() {
        return errors;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public int rowCount() {
        return rows.size();
    }

    public int errorCount() {
        return errors.size();
    }
}
