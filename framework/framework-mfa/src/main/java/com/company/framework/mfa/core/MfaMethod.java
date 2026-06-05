package com.company.framework.mfa.core;

/** 2차 인증 등록 방식. (복구코드는 등록 방식이 아니라 TOTP 등록에 부속된 검증 폴백이므로 별도 enum 값이 아니다.) */
public enum MfaMethod {
    /** 인증기 앱 기반 시간 기반 OTP(RFC 6238). 시크릿을 서버가 보관. */
    TOTP,
    /** 발송형 일회성 코드(SMS/메일/알림톡). 발송은 OtpSender SPI 에 위임. */
    OTP,
    /**
     * WebAuthn/패스키(FIDO2) 2차 인증. 등록/검증 ceremony 는 SS7 RP 연산에 위임하고(framework-webauthn 의
     * {@code WebAuthnRelyingPartyOperations}/저장소 재사용), MFA 는 등록 메타({@link MfaMethod#WEBAUTHN} 확정)와
     * 티켓 기반 challenge 보관·소유 검증만 담당한다. 시크릿/복구코드는 없다(자격증명은 SS 저장소가 보관).
     */
    WEBAUTHN;

    public static MfaMethod from(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toUpperCase()) {
            case "TOTP" -> TOTP;
            case "OTP" -> OTP;
            case "WEBAUTHN" -> WEBAUTHN;
            default -> null;
        };
    }

    public String code() {
        return name().toLowerCase();
    }
}
