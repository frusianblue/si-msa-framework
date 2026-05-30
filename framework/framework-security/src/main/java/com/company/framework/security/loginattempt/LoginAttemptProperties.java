package com.company.framework.security.loginattempt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그인 시도 제한(브루트포스 방어).
 * framework:
 *   security:
 *     login-attempt:
 *       enabled: true
 *       max-attempts: 5            # 연속 실패 허용 횟수
 *       lock-duration: 15m         # 초과 시 잠금 시간
 */
@ConfigurationProperties(prefix = "framework.security.login-attempt")
public class LoginAttemptProperties {
    private boolean enabled = true;
    private int maxAttempts = 5;
    private Duration lockDuration = Duration.ofMinutes(15);

    public boolean isEnabled() {
        return enabled;
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
