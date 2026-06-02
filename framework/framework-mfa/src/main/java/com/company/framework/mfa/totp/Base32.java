package com.company.framework.mfa.totp;

/**
 * RFC 4648 Base32 인코더/디코더 (대문자 A-Z2-7). TOTP 시크릿 표현용.
 *
 * <p>외부 라이브러리(commons-codec 등)를 끌어오지 않기 위해 직접 구현한다(프레임워크 의존성 0 원칙).
 * 인증기 앱(Google Authenticator 등) 호환을 위해 패딩('=')은 인코딩 시 생략하고 디코딩 시 허용한다.
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int[] DECODE = new int[128];

    static {
        for (int i = 0; i < DECODE.length; i++) {
            DECODE[i] = -1;
        }
        for (int i = 0; i < ALPHABET.length(); i++) {
            DECODE[ALPHABET.charAt(i)] = i;
        }
    }

    private Base32() {}

    /** 바이트 → Base32 문자열(패딩 없음). */
    public static String encode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1f;
                bitsLeft -= 5;
                sb.append(ALPHABET.charAt(index));
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1f;
            sb.append(ALPHABET.charAt(index));
        }
        return sb.toString();
    }

    /** Base32 문자열 → 바이트. 공백/하이픈/패딩/대소문자를 허용한다. */
    public static byte[] decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return new byte[0];
        }
        String clean =
                encoded.replace(" ", "").replace("-", "").replace("=", "").toUpperCase();
        if (clean.isEmpty()) {
            return new byte[0];
        }
        int outLength = clean.length() * 5 / 8;
        byte[] result = new byte[outLength];
        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if (c >= DECODE.length || DECODE[c] < 0) {
                throw new IllegalArgumentException("유효하지 않은 Base32 문자: " + c);
            }
            buffer = (buffer << 5) | DECODE[c];
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                result[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return result;
    }
}
