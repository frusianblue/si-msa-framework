package com.company.framework.mfa.store;

import com.company.framework.mfa.core.MfaMethod;
import java.time.Instant;
import java.util.List;

/**
 * 사용자별·방식별 MFA 등록 정보.
 *
 * <ul>
 *   <li>TOTP: {@code secret}(Base32) + {@code recoveryCodeHashes}(SHA-256, 1회용) 보관.
 *   <li>OTP: 발송형이라 시크릿/복구코드 없음(목적지는 발송 시점에 OtpSender 가 userId 로 해석).
 * </ul>
 *
 * <p>{@code confirmed} 가 true 인 등록만 로그인 2차 인증에 사용된다(등록 후 코드 검증으로 활성화).
 */
public record MfaEnrollment(
        String userId,
        MfaMethod method,
        String secret,
        List<String> recoveryCodeHashes,
        boolean confirmed,
        Instant createdAt) {

    public MfaEnrollment withConfirmed(boolean value) {
        return new MfaEnrollment(userId, method, secret, recoveryCodeHashes, value, createdAt);
    }

    public MfaEnrollment withRecoveryCodeHashes(List<String> hashes) {
        return new MfaEnrollment(userId, method, secret, hashes, confirmed, createdAt);
    }
}
