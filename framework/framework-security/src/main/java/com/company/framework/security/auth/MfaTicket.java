package com.company.framework.security.auth;

import java.util.List;

/**
 * 2차 인증 챌린지 발급 결과(로그인 1단계 응답 data). 클라이언트는 {@code ticket} 과 선택한 {@code method},
 * 사용자 입력 코드를 2단계 검증 엔드포인트(/api/v1/auth/mfa/verify)로 보낸다.
 *
 * @param ticket 서버측 대기 인증 상태를 가리키는 단기 일회성 식별자
 * @param methods 사용 가능한 2차 인증 방식(예: ["totp","otp"])
 * @param expiresInSeconds 티켓 유효 시간(초)
 */
public record MfaTicket(String ticket, List<String> methods, long expiresInSeconds) {}
