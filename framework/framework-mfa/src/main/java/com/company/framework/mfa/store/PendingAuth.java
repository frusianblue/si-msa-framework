package com.company.framework.mfa.store;

import java.util.List;

/**
 * 로그인 1단계 통과 후 2차 인증 완료 전까지의 서버측 대기 상태. 티켓으로 식별되며 단기 TTL 로 만료된다.
 *
 * <p>WebAuthn 방식은 challenge-response 라, 발급한 assertion 옵션(challenge 포함)을 검증 시 다시 제출해야 한다.
 * 세션 대신 이 대기 상태에 {@link #webauthnOptionsJson} 로 바인딩해 무상태 티켓 일관성을 유지한다(SS7 의 기본
 * 세션 보관을 티켓 보관으로 대체). 등록 ceremony 도 같은 저장소를 단기 티켓으로 재사용하며, 이 필드에 creation
 * 옵션 JSON 을 담는다.
 *
 * @param userId 인증 대상 사용자
 * @param roles 토큰 발급에 쓸 권한(2차 인증 성공 시 그대로 사용 — 등록 ticket 에서는 비어 있을 수 있음)
 * @param methods 이 사용자가 쓸 수 있는 방식 코드(예: ["totp","otp","webauthn"])
 * @param otpCodeHash 발송형 OTP 코드의 SHA-256(발송한 경우만, 평문 미보관)
 * @param attempts 누적 검증 실패 횟수(임계치 초과 시 챌린지 폐기)
 * @param webauthnOptionsJson WebAuthn assertion/creation 옵션 JSON(challenge 바인딩, WebAuthn 흐름에서만 채워짐)
 */
public record PendingAuth(
        String userId,
        List<String> roles,
        List<String> methods,
        String otpCodeHash,
        int attempts,
        String webauthnOptionsJson) {

    public PendingAuth withOtpCodeHash(String hash) {
        return new PendingAuth(userId, roles, methods, hash, attempts, webauthnOptionsJson);
    }

    public PendingAuth withAttempts(int value) {
        return new PendingAuth(userId, roles, methods, otpCodeHash, value, webauthnOptionsJson);
    }

    public PendingAuth withWebauthnOptionsJson(String json) {
        return new PendingAuth(userId, roles, methods, otpCodeHash, attempts, json);
    }
}
