package com.company.framework.core.util;

/**
 * 개인정보 마스킹 유틸 (SI 프로젝트 개인정보보호 대응 공통 기능).
 */
public final class MaskingUtils {

    private MaskingUtils() {}

    /** 이름: 가운데 글자 마스킹 (홍길동 -> 홍*동) */
    public static String maskName(String name) {
        if (name == null || name.length() < 2) return name;
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    /** 이메일: 아이디 앞 2자리만 노출 (gildong@x.com -> gi*****@x.com) */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int at = email.indexOf('@');
        String id = email.substring(0, at);
        String domain = email.substring(at);
        if (id.length() <= 2) return "*".repeat(id.length()) + domain;
        return id.substring(0, 2) + "*".repeat(id.length() - 2) + domain;
    }

    /** 휴대폰: 가운데 4자리 마스킹 (010-1234-5678 -> 010-****-5678) */
    public static String maskPhone(String phone) {
        if (phone == null) return null;
        return phone.replaceAll("(\\d{2,3})-?(\\d{3,4})-?(\\d{4})", "$1-****-$3");
    }
}
