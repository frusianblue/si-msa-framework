package com.company.framework.security.loginattempt;

/**
 * 로그인 실패 횟수 추적 + 계정 잠금. 구현은 상황별 교체(메모리/Redis/JDBC).
 * TokenStore 와 동일한 사상: 인터페이스만 공통, 다중 인스턴스 운영은 Redis 구현 권장.
 */
public interface LoginAttemptService {

    /** 잠겨 있으면 BusinessException(LOGIN_LOCKED) 을 던진다. */
    void assertNotLocked(String key);

    /** 로그인 실패 1회 기록. 임계치 초과 시 잠금. */
    void recordFailure(String key);

    /** 로그인 성공 시 카운터 초기화. */
    void reset(String key);

    boolean isLocked(String key);
}
