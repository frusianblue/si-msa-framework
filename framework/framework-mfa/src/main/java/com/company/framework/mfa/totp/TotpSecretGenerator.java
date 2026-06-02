package com.company.framework.mfa.totp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * TOTP 시크릿 발급 + 인증기 앱 등록용 {@code otpauth://} URI 생성.
 *
 * <p>QR 이미지는 생성하지 않는다(zxing 등 의존성 회피). 클라이언트가 이 URI 로 QR 을 그리거나
 * 사용자가 Base32 시크릿을 수동 입력하면 된다.
 */
public final class TotpSecretGenerator {

    private final SecureRandom random = new SecureRandom();
    private final int secretLengthBytes;
    private final String algorithm; // SHA1 | SHA256 | SHA512 (otpauth 표기용)
    private final int digits;
    private final int periodSeconds;

    public TotpSecretGenerator(int secretLengthBytes, String algorithm, int digits, int periodSeconds) {
        this.secretLengthBytes = Math.max(16, secretLengthBytes); // RFC 권고 최소 128비트
        this.algorithm = (algorithm == null ? "SHA1" : algorithm).trim().toUpperCase();
        this.digits = digits;
        this.periodSeconds = periodSeconds;
    }

    /** 새 시크릿(Base32 문자열). */
    public String newSecret() {
        byte[] buf = new byte[secretLengthBytes];
        random.nextBytes(buf);
        return Base32.encode(buf);
    }

    /**
     * 인증기 앱 등록용 URI. 예:
     * {@code otpauth://totp/si-msa:alice?secret=...&issuer=si-msa&algorithm=SHA1&digits=6&period=30}
     *
     * @param issuer 서비스/회사 라벨
     * @param account 계정 식별자(보통 loginId/userId)
     * @param secretBase32 {@link #newSecret()} 결과
     */
    public String provisioningUri(String issuer, String account, String secretBase32) {
        String safeIssuer = enc(issuer);
        String safeAccount = enc(account);
        String label = safeIssuer + ":" + safeAccount;
        return "otpauth://totp/" + label
                + "?secret=" + secretBase32
                + "&issuer=" + safeIssuer
                + "&algorithm=" + algorithm
                + "&digits=" + digits
                + "&period=" + periodSeconds;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
