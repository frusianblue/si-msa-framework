package com.company.framework.mfa.totp;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * RFC 6238(TOTP) / RFC 4226(HOTP) 코드 생성·검증. JDK {@link Mac} 만 사용(외부 의존성 0).
 *
 * <p>알고리즘(SHA1/256/512)·자릿수·주기는 MfaProperties.totp 로 설정한다. 검증은 시계 오차를 위해
 * 앞뒤 {@code window} 스텝까지 허용한다. 비교는 타이밍 공격 완화를 위해 상수시간 비교를 쓴다.
 */
public final class Totp {

    private final String hmacAlgorithm; // HmacSHA1 | HmacSHA256 | HmacSHA512
    private final int digits;
    private final int periodSeconds;
    private final int window;

    public Totp(String algorithm, int digits, int periodSeconds, int window) {
        this.hmacAlgorithm = toHmac(algorithm);
        this.digits = digits;
        this.periodSeconds = Math.max(1, periodSeconds);
        this.window = Math.max(0, window);
    }

    private static String toHmac(String algorithm) {
        String a = (algorithm == null ? "SHA1" : algorithm).trim().toUpperCase();
        return switch (a) {
            case "SHA256", "HMACSHA256" -> "HmacSHA256";
            case "SHA512", "HMACSHA512" -> "HmacSHA512";
            default -> "HmacSHA1";
        };
    }

    /** 현재 시각 기준 코드 생성(주로 테스트/디버깅용). */
    public String generate(byte[] secret) {
        return generateForCounter(secret, currentCounter(System.currentTimeMillis()));
    }

    /**
     * 사용자가 입력한 코드가 현재 시각 기준 ±window 스텝 내에서 유효한지 검증.
     *
     * @param secretBase32 Base32 인코딩된 시크릿
     * @param code 사용자 입력 코드(공백/하이픈 허용)
     */
    public boolean verify(String secretBase32, String code) {
        if (secretBase32 == null || code == null) {
            return false;
        }
        String normalized = code.replace(" ", "").replace("-", "");
        if (normalized.length() != digits || !normalized.chars().allMatch(Character::isDigit)) {
            return false;
        }
        byte[] secret = Base32.decode(secretBase32);
        long counter = currentCounter(System.currentTimeMillis());
        for (int i = -window; i <= window; i++) {
            String candidate = generateForCounter(secret, counter + i);
            if (constantTimeEquals(candidate, normalized)) {
                return true;
            }
        }
        return false;
    }

    private long currentCounter(long epochMillis) {
        return (epochMillis / 1000L) / periodSeconds;
    }

    private String generateForCounter(byte[] secret, long counter) {
        byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();
        byte[] hash = hmac(secret, counterBytes);
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        int modulo = (int) Math.pow(10, digits);
        int otp = binary % modulo;
        return String.format("%0" + digits + "d", otp);
    }

    private byte[] hmac(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(hmacAlgorithm);
            mac.init(new SecretKeySpec(key, hmacAlgorithm));
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("TOTP HMAC 계산 실패: " + hmacAlgorithm, e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
