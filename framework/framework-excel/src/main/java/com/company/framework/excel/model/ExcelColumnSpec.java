package com.company.framework.excel.model;

/**
 * 업로드 템플릿의 컬럼 1개 정의(양식검증 규칙).
 *
 * <ul>
 *   <li>{@code key} — 파싱 결과 {@link ExcelRow} 의 맵 키(업무 코드에서 꺼낼 이름).
 *   <li>{@code header} — 엑셀 헤더행에 있어야 하는 표시 텍스트. (헤더 텍스트로 컬럼 위치를 찾으므로 열 순서와 무관)
 *   <li>{@code type} — 값 해석/검증 타입.
 *   <li>{@code required} — 빈 셀이면 오류.
 *   <li>{@code maxLength} — STRING 최대 길이(널이면 무제한).
 *   <li>{@code pattern} — STRING 정규식(널이면 미검증).
 * </ul>
 *
 * <pre>{@code
 * ExcelColumnSpec.builder("email", "이메일", ExcelCellType.STRING)
 *     .required(true).pattern("^[^@]+@[^@]+\\.[^@]+$").build();
 * }</pre>
 */
public final class ExcelColumnSpec {

    private final String key;
    private final String header;
    private final ExcelCellType type;
    private final boolean required;
    private final Integer maxLength;
    private final String pattern;

    private ExcelColumnSpec(Builder b) {
        this.key = b.key;
        this.header = b.header;
        this.type = b.type;
        this.required = b.required;
        this.maxLength = b.maxLength;
        this.pattern = b.pattern;
    }

    public static Builder builder(String key, String header, ExcelCellType type) {
        return new Builder(key, header, type);
    }

    public String key() {
        return key;
    }

    public String header() {
        return header;
    }

    public ExcelCellType type() {
        return type;
    }

    public boolean required() {
        return required;
    }

    public Integer maxLength() {
        return maxLength;
    }

    public String pattern() {
        return pattern;
    }

    public static final class Builder {
        private final String key;
        private final String header;
        private final ExcelCellType type;
        private boolean required = false;
        private Integer maxLength;
        private String pattern;

        private Builder(String key, String header, ExcelCellType type) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("ExcelColumnSpec.key 는 필수입니다.");
            }
            if (header == null || header.isBlank()) {
                throw new IllegalArgumentException("ExcelColumnSpec.header 는 필수입니다.");
            }
            if (type == null) {
                throw new IllegalArgumentException("ExcelColumnSpec.type 은 필수입니다.");
            }
            this.key = key;
            this.header = header;
            this.type = type;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder maxLength(Integer maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public ExcelColumnSpec build() {
            return new ExcelColumnSpec(this);
        }
    }
}
