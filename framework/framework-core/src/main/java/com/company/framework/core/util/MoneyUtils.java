package com.company.framework.core.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 숫자/금액 표기 공통 유틸 — 천단위 콤마, 한글 금액(증서/계약서용 "일금 …원정"), 반올림/절사 정책.
 *
 * <p>금액 계산은 부동소수점 오차를 피하기 위해 {@link BigDecimal} 기반 메서드를 제공한다.
 */
public final class MoneyUtils {

    private MoneyUtils() {}

    private static final String[] DIGITS = {"", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"};
    private static final String[] SMALL_UNITS = {"", "십", "백", "천"};
    private static final String[] BIG_UNITS = {"", "만", "억", "조", "경"};

    /** 천단위 콤마: {@code 1234567 → "1,234,567"} */
    public static String comma(long value) {
        return String.format("%,d", value);
    }

    /** 천단위 콤마(BigDecimal). */
    public static String comma(BigDecimal value) {
        return value == null ? null : String.format("%,.0f", value);
    }

    /**
     * 정수를 한글 수로 변환: {@code 123456 → "십이만삼천사백오십육"}, {@code 0 → "영"}, 음수는 "마이너스 " 접두.
     * 십/백/천/만 단위의 선행 "일"은 한국어 읽기 관행대로 생략한다(예: 10→"십", 100→"백").
     */
    public static String toKoreanNumber(long value) {
        if (value == 0) {
            return "영";
        }
        boolean negative = value < 0;
        String digits = Long.toString(Math.abs(value));
        int len = digits.length();
        int groups = (len + 3) / 4;
        StringBuilder out = new StringBuilder();
        for (int g = groups - 1; g >= 0; g--) {
            int end = len - g * 4;
            int start = Math.max(0, end - 4);
            String group = digits.substring(start, end);
            int glen = group.length();
            StringBuilder gs = new StringBuilder();
            for (int i = 0; i < glen; i++) {
                int d = group.charAt(i) - '0';
                int unit = glen - 1 - i;
                if (d != 0) {
                    if (d == 1 && unit > 0) {
                        gs.append(SMALL_UNITS[unit]);
                    } else {
                        gs.append(DIGITS[d]).append(SMALL_UNITS[unit]);
                    }
                }
            }
            if (gs.length() > 0) {
                out.append(gs).append(BIG_UNITS[g]);
            }
        }
        return (negative ? "마이너스 " : "") + out;
    }

    /** 증서/계약서용 금액 표기: {@code 50000 → "일금 오만원정"} */
    public static String toKoreanAmount(long value) {
        return "일금 " + toKoreanNumber(value) + "원정";
    }

    /** 지정 소수 자릿수로 반올림(HALF_UP). */
    public static BigDecimal round(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.HALF_UP);
    }

    /** 지정 소수 자릿수로 절사(내림, FLOOR). 금융 정산의 "원 미만 절사" 등. */
    public static BigDecimal truncate(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.DOWN);
    }

    /** 지정 소수 자릿수로 올림(CEILING). */
    public static BigDecimal ceil(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale, RoundingMode.UP);
    }
}
