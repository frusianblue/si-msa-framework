package com.company.framework.core.util;

import java.util.regex.Pattern;

/**
 * 형식 검증 공통 유틸 — 이메일/전화/카드(Luhn). 한국 전화번호 패턴에 특화.
 *
 * <p>범용 문자열/컬렉션 검증은 {@code org.springframework.util.StringUtils} 등 표준을 쓰고, 여기서는
 * 도메인 형식(이메일/휴대폰/유선/카드)만 다룬다. 모두 {@code null}-safe 이며 실패 시 {@code false} 반환.
 */
public final class ValidationUtils {

    private ValidationUtils() {}

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** 휴대폰: 010/011/016/017/018/019, 하이픈 유무 허용. */
    private static final Pattern MOBILE = Pattern.compile("^01[016789]-?\\d{3,4}-?\\d{4}$");

    /** 유선: 02(서울) 또는 지역번호 3자리, 하이픈 유무 허용. */
    private static final Pattern LANDLINE = Pattern.compile("^0(2|[3-6][0-5])-?\\d{3,4}-?\\d{4}$");

    public static boolean isEmail(String s) {
        return s != null && EMAIL.matcher(s).matches();
    }

    public static boolean isMobile(String s) {
        return s != null && MOBILE.matcher(s).matches();
    }

    public static boolean isLandline(String s) {
        return s != null && LANDLINE.matcher(s).matches();
    }

    /** 휴대폰 또는 유선 중 하나라도 부합하면 true. */
    public static boolean isPhone(String s) {
        return isMobile(s) || isLandline(s);
    }

    /**
     * 카드번호 Luhn(모듈러스 10) 검증. 하이픈/공백 제거 후 12~19자리 숫자에 대해 적용.
     * (예: 4111-1111-1111-1111 → 유효)
     */
    public static boolean isValidCardNumber(String card) {
        if (card == null) {
            return false;
        }
        String d = card.replaceAll("[^0-9]", "");
        if (d.length() < 12 || d.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean alt = false;
        for (int i = d.length() - 1; i >= 0; i--) {
            int n = d.charAt(i) - '0';
            if (alt) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alt = !alt;
        }
        return sum % 10 == 0;
    }
}
