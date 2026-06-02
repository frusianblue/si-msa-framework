package com.company.framework.mfa.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * MFA 보조 암호 유틸(외부 의존성 0). 발송형 OTP 숫자코드 생성, 복구코드 생성, 단방향 해시(SHA-256).
 *
 * <p>발송 OTP/복구코드는 평문을 저장하지 않고 해시만 보관한다(노출 시에도 재사용 방지). 비교는 상수시간으로 한다.
 */
public final class MfaCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    // 혼동 문자(0/O/1/I) 제외 — 복구코드 수기 입력 오류 감소
    private static final char[] RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private MfaCrypto() {}

    /** 자릿수 길이의 숫자 OTP(앞자리 0 허용). */
    public static String numericOtp(int length) {
        int len = Math.max(4, Math.min(10, length));
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /** count 개의 복구코드(형식: XXXX-XXXX). 평문은 1회만 사용자에게 노출하고 해시만 저장한다. */
    public static List<String> recoveryCodes(int count) {
        int n = Math.max(1, count);
        List<String> codes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            codes.add(randomBlock(4) + "-" + randomBlock(4));
        }
        return codes;
    }

    private static String randomBlock(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(RECOVERY_ALPHABET[RANDOM.nextInt(RECOVERY_ALPHABET.length)]);
        }
        return sb.toString();
    }

    /** SHA-256 hex. OTP/복구코드 저장·비교용. 입력은 정규화(공백/하이픈 제거, 대문자)한다. */
    public static String sha256Hex(String raw) {
        try {
            String normalized = normalize(raw);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원", e);
        }
    }

    /** 해시 동등 비교(상수시간). */
    public static boolean matches(String raw, String expectedHash) {
        if (expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sha256Hex(raw).getBytes(StandardCharsets.UTF_8), expectedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.replace(" ", "").replace("-", "").toUpperCase();
    }
}
