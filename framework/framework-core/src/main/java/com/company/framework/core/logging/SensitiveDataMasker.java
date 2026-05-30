package com.company.framework.core.logging;

import java.util.regex.Pattern;

/**
 * 로그에 노출되면 안 되는 민감정보를 정규식으로 마스킹한다. (개인정보보호/시큐어코딩 대응)
 */
public final class SensitiveDataMasker {

    private SensitiveDataMasker() {}

    // JSON 키 기반 마스킹: "password":"xxx" -> "password":"****"
    private static final Pattern JSON_SECRET = Pattern.compile(
            "(\"(?:password|passwd|pwd|secret|token|accessToken|refreshToken|authorization)\"\\s*:\\s*\")[^\"]*(\")",
            Pattern.CASE_INSENSITIVE);
    // 주민등록번호 (앞 6 + 뒤 7)
    private static final Pattern RRN = Pattern.compile("(\\d{6})[-\\s]?[1-4]\\d{6}");
    // 카드번호 (16자리)
    private static final Pattern CARD = Pattern.compile("\\b(\\d{4})[-\\s]?\\d{4}[-\\s]?\\d{4}[-\\s]?(\\d{4})\\b");

    public static String mask(String input) {
        if (input == null || input.isBlank()) return input;
        String out = JSON_SECRET.matcher(input).replaceAll("$1****$2");
        out = RRN.matcher(out).replaceAll("$1-*******");
        out = CARD.matcher(out).replaceAll("$1-****-****-$2");
        return out;
    }
}
