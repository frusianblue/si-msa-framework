package com.company.framework.security.concurrent;

import java.util.List;

/**
 * 사용자별 활성 세션 추적 + 동시 로그인 한도 제어. 구현 교체(memory/jdbc/redis).
 * 세션 식별자(sessionId)는 로그인 1회당 고유(refresh token 사용). accessJti 는 강제 로그아웃 시
 * 액세스 토큰 블랙리스트에 사용한다.
 */
public interface ConcurrentSessionService {

    /** 활성 세션 1건. */
    record ActiveSession(
            String userId, String sessionId, String accessJti, String refreshToken, long issuedAtEpochMs) {}

    /**
     * 신규 세션 등록 + 한도 적용.
     *  - REJECT 전략: 한도 초과 시 BusinessException(CONFLICT) 을 던진다(등록 안 됨).
     *  - EVICT_OLDEST 전략: 초과분(가장 오래된 세션들)을 반환하고 신규를 등록한다.
     * @return 강제 로그아웃 대상(호출자가 토큰 무효화). REJECT/여유 있음이면 빈 리스트.
     */
    List<ActiveSession> register(ActiveSession session);

    /** 세션 제거(로그아웃/회전 시). */
    void unregister(String sessionId);

    /** 현재 활성 세션(최신순 권장, 구현 자유). */
    List<ActiveSession> activeSessions(String userId);
}
