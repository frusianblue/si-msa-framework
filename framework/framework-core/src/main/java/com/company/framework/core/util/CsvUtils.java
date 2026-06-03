package com.company.framework.core.util;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV 공통 유틸 — RFC 4180 인용/이스케이프를 지키는 안전한 직렬화/파싱. 대외기관 연계·내보내기에 사용.
 *
 * <p>구분자/줄바꿈/큰따옴표가 포함된 필드는 자동으로 큰따옴표로 감싸고 내부 {@code "} 는 {@code ""} 로 이스케이프한다.
 * 파싱은 인용된 필드 안의 구분자·줄바꿈을 올바로 처리한다. 행 종결자는 출력 시 RFC 4180 의 CRLF 를 쓴다.
 */
public final class CsvUtils {

    private static final char DEFAULT_DELIMITER = ',';
    private static final char QUOTE = '"';
    private static final String CRLF = "\r\n";

    private CsvUtils() {}

    /** 단일 필드를 RFC 4180 규칙으로 인용/이스케이프한다(null → 빈 문자열). */
    public static String writeField(String field, char delimiter) {
        if (field == null) {
            return "";
        }
        boolean needQuote = field.indexOf(delimiter) >= 0
                || field.indexOf(QUOTE) >= 0
                || field.indexOf('\n') >= 0
                || field.indexOf('\r') >= 0;
        if (!needQuote) {
            return field;
        }
        return QUOTE + field.replace("\"", "\"\"") + QUOTE;
    }

    public static String writeRow(List<String> fields) {
        return writeRow(fields, DEFAULT_DELIMITER);
    }

    public static String writeRow(List<String> fields, char delimiter) {
        if (fields == null || fields.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(writeField(fields.get(i), delimiter));
        }
        return sb.toString();
    }

    /** 여러 행을 CRLF 로 이어 CSV 문서를 만든다. */
    public static String writeRows(List<? extends List<String>> rows) {
        return writeRows(rows, DEFAULT_DELIMITER);
    }

    public static String writeRows(List<? extends List<String>> rows, char delimiter) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                sb.append(CRLF);
            }
            sb.append(writeRow(rows.get(i), delimiter));
        }
        return sb.toString();
    }

    public static List<List<String>> parse(String csv) {
        return parse(csv, DEFAULT_DELIMITER);
    }

    /**
     * RFC 4180 파서 — 인용된 필드 안의 구분자/줄바꿈, {@code ""} 이스케이프를 처리한다.
     * 행 종결자는 CRLF/LF/CR 모두 허용. 빈 입력은 빈 리스트.
     */
    public static List<List<String>> parse(String csv, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        if (csv == null || csv.isEmpty()) {
            return rows;
        }
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        int n = csv.length();
        while (i < n) {
            char c = csv.charAt(i);
            if (inQuotes) {
                if (c == QUOTE) {
                    if (i + 1 < n && csv.charAt(i + 1) == QUOTE) {
                        field.append(QUOTE); // 이스케이프된 따옴표
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                    i++;
                } else {
                    field.append(c);
                    i++;
                }
            } else {
                if (c == QUOTE) {
                    inQuotes = true;
                    i++;
                } else if (c == delimiter) {
                    row.add(field.toString());
                    field.setLength(0);
                    i++;
                } else if (c == '\r' || c == '\n') {
                    row.add(field.toString());
                    field.setLength(0);
                    rows.add(row);
                    row = new ArrayList<>();
                    // CRLF 는 한 줄바꿈으로
                    if (c == '\r' && i + 1 < n && csv.charAt(i + 1) == '\n') {
                        i += 2;
                    } else {
                        i++;
                    }
                } else {
                    field.append(c);
                    i++;
                }
            }
        }
        // 마지막 필드/행 마감(끝에 줄바꿈이 없을 때)
        row.add(field.toString());
        rows.add(row);
        return rows;
    }
}
