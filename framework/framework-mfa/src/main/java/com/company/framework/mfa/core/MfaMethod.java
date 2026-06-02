package com.company.framework.mfa.core;

/** 2차 인증 등록 방식. (복구코드는 등록 방식이 아니라 TOTP 등록에 부속된 검증 폴백이므로 별도 enum 값이 아니다.) */
public enum MfaMethod {
    /** 인증기 앱 기반 시간 기반 OTP(RFC 6238). 시크릿을 서버가 보관. */
    TOTP,
    /** 발송형 일회성 코드(SMS/메일/알림톡). 발송은 OtpSender SPI 에 위임. */
    OTP;

    public static MfaMethod from(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toUpperCase()) {
            case "TOTP" -> TOTP;
            case "OTP" -> OTP;
            default -> null;
        };
    }

    public String code() {
        return name().toLowerCase();
    }
}
