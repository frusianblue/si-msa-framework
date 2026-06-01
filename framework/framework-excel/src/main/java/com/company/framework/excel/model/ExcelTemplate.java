package com.company.framework.excel.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 업로드 엑셀 양식(템플릿) 정의. {@link com.company.framework.excel.importer.ExcelImporter} 가 이 정의로 검증한다.
 *
 * <ul>
 *   <li>{@code sheetName} — 읽을 시트명(널이면 첫 번째 시트).
 *   <li>{@code headerRowIndex} — 헤더행 0-based 인덱스(기본 0).
 *   <li>{@code columns} — 컬럼 정의 목록.
 *   <li>{@code strictHeader} — true(기본)면 헤더 텍스트가 정의와 정확히 일치해야 한다(불일치 시 양식 오류로 하드 실패).
 *   <li>{@code failFast} — true 면 첫 오류에서 중단, false(기본)면 모든 행의 오류를 수집.
 * </ul>
 *
 * <pre>{@code
 * ExcelTemplate template = ExcelTemplate.builder()
 *     .sheetName("회원")
 *     .column(ExcelColumnSpec.builder("name", "이름", ExcelCellType.STRING).required(true).maxLength(50).build())
 *     .column(ExcelColumnSpec.builder("age",  "나이", ExcelCellType.LONG).build())
 *     .build();
 * }</pre>
 */
public final class ExcelTemplate {

    private final String sheetName;
    private final int headerRowIndex;
    private final List<ExcelColumnSpec> columns;
    private final boolean strictHeader;
    private final boolean failFast;

    private ExcelTemplate(Builder b) {
        if (b.columns.isEmpty()) {
            throw new IllegalArgumentException("ExcelTemplate 에는 최소 1개 컬럼이 필요합니다.");
        }
        this.sheetName = b.sheetName;
        this.headerRowIndex = b.headerRowIndex;
        this.columns = List.copyOf(b.columns);
        this.strictHeader = b.strictHeader;
        this.failFast = b.failFast;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String sheetName() {
        return sheetName;
    }

    public int headerRowIndex() {
        return headerRowIndex;
    }

    public List<ExcelColumnSpec> columns() {
        return columns;
    }

    public boolean strictHeader() {
        return strictHeader;
    }

    public boolean failFast() {
        return failFast;
    }

    public static final class Builder {
        private String sheetName;
        private int headerRowIndex = 0;
        private final List<ExcelColumnSpec> columns = new ArrayList<>();
        private boolean strictHeader = true;
        private boolean failFast = false;

        public Builder sheetName(String sheetName) {
            this.sheetName = sheetName;
            return this;
        }

        public Builder headerRowIndex(int headerRowIndex) {
            if (headerRowIndex < 0) {
                throw new IllegalArgumentException("headerRowIndex 는 0 이상이어야 합니다.");
            }
            this.headerRowIndex = headerRowIndex;
            return this;
        }

        public Builder column(ExcelColumnSpec column) {
            this.columns.add(column);
            return this;
        }

        public Builder columns(List<ExcelColumnSpec> columns) {
            this.columns.addAll(columns);
            return this;
        }

        public Builder strictHeader(boolean strictHeader) {
            this.strictHeader = strictHeader;
            return this;
        }

        public Builder failFast(boolean failFast) {
            this.failFast = failFast;
            return this;
        }

        public ExcelTemplate build() {
            return new ExcelTemplate(this);
        }
    }
}
