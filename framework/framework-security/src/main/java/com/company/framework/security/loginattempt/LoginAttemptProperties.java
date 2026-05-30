package com.company.framework.security.loginattempt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 시도 제한(브루트포스 방어).
 * framework:
 *   security:
 *     login-attempt:
 *       enabled: true
 *       type: memory               # memory | redis (redis 는 framework-redis 모듈 필요, 다중 인스턴스 공유)
 *       max-attempts: 5            # 연속 실패 허용 횟수
 *       lock-duration: 15m         # 초과 시 잠금 시간(redis 구현은 실패 카운터 윈도우에도 동일 적용)
 *       key-strategy: login-id     # login-id | login-id-and-ip (계정 단위 vs 계정+IP 단위 카운트)
 *       client-ip-header: X-Forwarded-For   # IP 결합 시 클라이언트 IP 를 읽을 헤더(프록시/인그레스 뒤)
 */
@ConfigurationProperties(prefix = "framework.security.login-attempt")
public class LoginAttemptProperties {

    /**
     * 실패 카운트 키 정책.
     *  - LOGIN_ID: 계정 단위. 특정 계정을 노린 공격은 IP 가 바뀌어도 차단되나, 공격자가 피해자 loginId 로 의도적
     *    계정 잠금(DoS) 을 유발할 수 있고 분산 공격(여러 계정)에는 약하다.
     *  - LOGIN_ID_AND_IP: 계정+IP 단위. NAT/공유망 동료 잠금·계정 DoS 를 줄이나, X-Forwarded-For 위조로
     *    IP 를 회전시키면 우회 가능(신뢰 가능한 프록시가 헤더를 세팅하는 환경에서만 의미 있음).
     */
    public enum KeyStrategy {
        LOGIN_ID,
        LOGIN_ID_AND_IP
    }

    private boolean enabled = true;
    private String type = "memory";
    private int maxAttempts = 5;
    private Duration lockDuration = Duration.ofMinutes(15);
    private KeyStrategy keyStrategy = KeyStrategy.LOGIN_ID;
    private String clientIpHeader = "X-Forwarded-For";

    public boolean isEnabled() {
        return enabled;
    }

    public KeyStrategy getKeyStrategy() {
        return keyStrategy;
    }

    public void setKeyStrategy(KeyStrategy keyStrategy) {
        this.keyStrategy = keyStrategy;
    }

    public String getClientIpHeader() {
        return clientIpHeader;
    }

    public void setClientIpHeader(String clientIpHeader) {
        this.clientIpHeader = clientIpHeader;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getLockDuration() {
        return lockDuration;
    }

    public void setLockDuration(Duration lockDuration) {
        this.lockDuration = lockDuration;
    }
}
