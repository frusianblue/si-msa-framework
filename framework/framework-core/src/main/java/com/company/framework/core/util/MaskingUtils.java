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

    /** 주민등록번호: 생년월일+성별 한 자리만 노출, 뒤 6자리 마스킹 (9001011234567 -> 900101-1******) */
    public static String maskResidentNo(String rrn) {
        if (rrn == null) return null;
        String d = rrn.replaceAll("[^0-9]", "");
        if (d.length() != 13) return rrn;
        return d.substring(0, 6) + "-" + d.charAt(6) + "******";
    }

    /** 카드번호: 앞 4자리·뒤 4자리만 노출 (1234567812345678 -> 1234-****-****-5678) */
    public static String maskCard(String card) {
        if (card == null) return null;
        String d = card.replaceAll("[^0-9]", "");
        if (d.length() < 12 || d.length() > 19) return card;
        return d.substring(0, 4) + "-****-****-" + d.substring(d.length() - 4);
    }

    /** 계좌번호: 앞 3자리·뒤 3자리만 노출, 가운데 마스킹 (110-1234-567890 -> 110****890) */
    public static String maskAccount(String account) {
        if (account == null) return null;
        String d = account.replaceAll("[^0-9]", "");
        if (d.length() <= 6) return "*".repeat(d.length());
        return d.substring(0, 3) + "*".repeat(d.length() - 6) + d.substring(d.length() - 3);
    }

    /** 주소: 앞 2개 토큰(시/도·시군구 등)만 노출, 상세주소 마스킹 (서울시 강남구 테헤란로 123 -> 서울시 강남구 ***) */
    public static String maskAddress(String address) {
        if (address == null || address.isBlank()) return address;
        String[] tokens = address.trim().split("\\s+");
        if (tokens.length <= 2) return address;
        return tokens[0] + " " + tokens[1] + " ***";
    }
}
