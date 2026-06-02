package com.company.framework.security.auth;

/**
 * 1차 인증 결과. MFA 가 필요 없으면 토큰을 즉시 발급({@link Authenticated}), 필요하면 챌린지 티켓을
 * 반환({@link MfaRequired})한다. MFA 미사용 환경에서는 항상 {@link Authenticated} 이므로 기존 단일단계
 * 로그인 동작과 완전히 동일하다.
 */
public sealed interface LoginOutcome permits LoginOutcome.Authenticated, LoginOutcome.MfaRequired {

    /** 인증 완료 — 토큰 발급됨. */
    record Authenticated(TokenResponse tokens) implements LoginOutcome {}

    /** 2차 인증 필요 — 토큰 미발급, 티켓으로 2단계 진행. */
    record MfaRequired(MfaTicket ticket) implements LoginOutcome {}
}
