package com.company.framework.excel.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 검증을 통과한 한 행. {@code rowNumber} 는 엑셀 화면 기준 1-based(헤더 다음 첫 데이터행이 보통 2).
 * 값은 {@link ExcelColumnSpec#type()} 으로 변환된 타입(String/Long/Double/BigDecimal/Boolean/LocalDate/LocalDateTime)으로 담긴다.
 */
public final class ExcelRow {

    private final int rowNumber;
    private final Map<String, Object> values;

    public ExcelRow(int rowNumber, Map<String, Object> values) {
        this.rowNumber = rowNumber;
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public int rowNumber() {
        return rowNumber;
    }

    public Map<String, Object> values() {
        return values;
    }

    public Object get(String key) {
        return values.get(key);
    }

    public String getString(String key) {
        Object v = values.get(key);
        return v == null ? null : v.toString();
    }

    public Long getLong(String key) {
        return (Long) values.get(key);
    }

    public Double getDouble(String key) {
        return (Double) values.get(key);
    }

    public BigDecimal getBigDecimal(String key) {
        return (BigDecimal) values.get(key);
    }

    public Boolean getBoolean(String key) {
        return (Boolean) values.get(key);
    }

    public LocalDate getLocalDate(String key) {
        return (LocalDate) values.get(key);
    }

    public LocalDateTime getLocalDateTime(String key) {
        return (LocalDateTime) values.get(key);
    }

    @Override
    public String toString() {
        return "ExcelRow{row=" + rowNumber + ", values=" + values + '}';
    }
}
