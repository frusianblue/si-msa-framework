package com.company.framework.security.auth;

/**
 * 2단계 인증 게이트(SPI). framework-mfa 가 구현하며, 없으면(미의존/비활성) LoginService 는 단일단계 로그인으로 동작한다.
 *
 * <p>framework-security 는 이 타입만 알고 구현은 모른다(의존 방향 mfa → security 단방향). LoginService 는
 * 이 빈을 <b>선택적</b>으로 주입받아, 1차 인증 성공 후 사용자에게 2차 인증이 필요한지 판정하고 챌린지를 발급한다.
 */
public interface MfaGate {

    /** 1차 인증을 통과한 사용자에게 2차 인증이 필요한지. (예: 확정된 MFA 등록이 있으면 true) */
    boolean isRequired(AuthenticatedUser user);

    /**
     * 2차 인증 챌린지를 발급한다. 서버는 (userId/roles 등) 대기 인증 상태를 단기 보관하고, 클라이언트가 2단계에서
     * 제시할 티켓과 사용 가능한 방식 목록을 돌려준다. OTP 방식이면 이 시점에 코드를 발송할 수 있다.
     *
     * @param clientIp 감사/발송 컨텍스트용(없으면 null)
     */
    MfaTicket issueChallenge(AuthenticatedUser user, String clientIp);
}
