package com.company.framework.core.util;

import java.nio.charset.Charset;

/**
 * 문자열 공통 유틸 — null 안전 헬퍼 + <b>바이트 기준 안전 자르기</b>(한글 안 깨짐) + 케이스 변환.
 *
 * <p>마스킹은 {@link MaskingUtils}, 검증은 {@link ValidationUtils} 가 담당한다(중복 없음).
 */
public final class TextUtils {

    private TextUtils() {}

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static boolean isNotBlank(String s) {
        return !isBlank(s);
    }

    /** 비어있으면(null/blank) 기본값. */
    public static String defaultIfBlank(String s, String defaultValue) {
        return isBlank(s) ? defaultValue : s;
    }

    public static String trimToEmpty(String s) {
        return s == null ? "" : s.strip();
    }

    public static String trimToNull(String s) {
        String t = trimToEmpty(s);
        return t.isEmpty() ? null : t;
    }

    /** 문자 수 기준으로 자른다(초과분 절단). null 은 null. */
    public static String truncate(String s, int maxChars) {
        if (s == null) {
            return null;
        }
        if (maxChars < 0) {
            throw new IllegalArgumentException("maxChars 는 0 이상이어야 합니다.");
        }
        return s.length() <= maxChars ? s : s.substring(0, maxChars);
    }

    /**
     * <b>바이트 길이 기준</b>으로 자르되 멀티바이트 문자(한글 등)·서로게이트(이모지)를 쪼개지 않는다.
     * 고정폭 전문·DB 컬럼(VARCHAR 바이트 제한)·헤더 길이 제한에서 글자 깨짐을 방지한다.
     */
    public static String truncateByBytes(String s, int maxBytes, Charset charset) {
        if (s == null) {
            return null;
        }
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes 는 0 이상이어야 합니다.");
        }
        if (charset == null) {
            throw new IllegalArgumentException("charset 이 null 입니다.");
        }
        if (s.getBytes(charset).length <= maxBytes) {
            return s;
        }
        // 바이트 길이가 maxBytes 이하인 최대 prefix(문자 수)를 이분 탐색.
        int lo = 0;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (s.substring(0, mid).getBytes(charset).length <= maxBytes) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        // 서로게이트 페어 중간에서 끊겼으면 한 칸 뒤로(이모지 보호).
        if (lo > 0 && Character.isHighSurrogate(s.charAt(lo - 1))) {
            lo--;
        }
        return s.substring(0, lo);
    }

    /** 좌측 패딩(우측 정렬). 이미 길면 그대로. */
    public static String leftPad(String s, int length, char pad) {
        String v = s == null ? "" : s;
        if (v.length() >= length) {
            return v;
        }
        return String.valueOf(pad).repeat(length - v.length()) + v;
    }

    /** 우측 패딩(좌측 정렬). 이미 길면 그대로. */
    public static String rightPad(String s, int length, char pad) {
        String v = s == null ? "" : s;
        if (v.length() >= length) {
            return v;
        }
        return v + String.valueOf(pad).repeat(length - v.length());
    }

    /** camelCase/PascalCase → snake_case(소문자). */
    public static String toSnakeCase(String s) {
        if (isBlank(s)) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** snake_case → camelCase. */
    public static String toCamelCase(String s) {
        if (isBlank(s)) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        boolean upperNext = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                sb.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
