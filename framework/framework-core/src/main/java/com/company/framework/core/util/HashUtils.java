package com.company.framework.core.util;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 해시/인코딩 공통 유틸 — SHA-256/512(hex), Base64(표준/URL-safe), Hex.
 *
 * <p>비밀번호 저장에는 단방향 해시가 아니라 framework-security 의 비밀번호 인코더(BCrypt 등)를 쓸 것.
 * 본 유틸은 무결성 체크·식별자 해시·토큰 인코딩 등 범용 용도다. 모두 정적, 스레드 안전.
 */
public final class HashUtils {

    private HashUtils() {}

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public static String sha256Hex(String text) {
        return hex(digest("SHA-256", text.getBytes(StandardCharsets.UTF_8)));
    }

    public static String sha512Hex(String text) {
        return hex(digest("SHA-512", text.getBytes(StandardCharsets.UTF_8)));
    }

    public static String sha256Hex(byte[] data) {
        return hex(digest("SHA-256", data));
    }

    private static byte[] digest(String algorithm, byte[] data) {
        try {
            return MessageDigest.getInstance(algorithm).digest(data);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256/512 는 모든 JVM 필수 제공 — 사실상 도달 불가.
            throw new BusinessException(ErrorCode.Common.INTERNAL_ERROR, "해시 알고리즘 미지원: " + algorithm);
        }
    }

    /** 바이트 배열 → 소문자 hex 문자열. */
    public static String hex(byte[] data) {
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int b = data[i] & 0xFF;
            out[i * 2] = HEX[b >>> 4];
            out[i * 2 + 1] = HEX[b & 0x0F];
        }
        return new String(out);
    }

    public static String base64Encode(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    public static byte[] base64Decode(String text) {
        return Base64.getDecoder().decode(text);
    }

    /** URL-safe Base64(패딩 없음) — 토큰/식별자를 URL·헤더에 안전하게 실을 때. */
    public static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    public static byte[] base64UrlDecode(String text) {
        return Base64.getUrlDecoder().decode(text);
    }
}
