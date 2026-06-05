package com.company.framework.security.concurrent;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 동시(중복) 로그인 제어 — ISMS-P/보안성 심의 대응.
 * framework:
 *   security:
 *     concurrent-session:
 *       enabled: false            # 2단 토글(기본 off → 켜면 사용자별 동시 세션 수 제한)
 *       max-sessions: 1           # 사용자당 허용 동시 세션 수
 *       strategy: evict-oldest    # evict-oldest(기존 세션 강제 로그아웃) | reject(신규 로그인 거부)
 *       store:
 *         type: memory            # 3단 토글: memory | jdbc | redis (상호배제)
 */
@ConfigurationProperties(prefix = "framework.security.concurrent-session")
public class ConcurrentSessionProperties {

    /** 한도 초과 시 처리 전략. */
    public enum Strategy {
        /** 기존 세션 중 가장 오래된 것을 강제 로그아웃(토큰 무효화)하고 신규 로그인을 허용. */
        EVICT_OLDEST,
        /** 신규 로그인을 거부(CONFLICT). 기존 세션 유지. */
        REJECT
    }

    private boolean enabled = false;
    private int maxSessions = 1;
    private Strategy strategy = Strategy.EVICT_OLDEST;
    private final Store store = new Store();

    public static class Store {
        private String type = "memory";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxSessions() {
        return maxSessions;
    }

    public void setMaxSessions(int maxSessions) {
        this.maxSessions = maxSessions;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    public Store getStore() {
        return store;
    }
}
