package com.company.framework.session.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * framework:
 *   session:
 *     enabled: true              # 이 모듈(클러스터 세션 저장소) 활성. 의존성만 추가하면 기본 on.
 *     warn-if-mode-stateless: true  # 모듈은 올라왔는데 framework.security.session.mode 가 session 이 아니면 경고(흔한 오설정)
 *
 * <p>실제 Redis 연결/직렬화/네임스페이스는 Spring Boot 표준 프로퍼티로 설정한다(이 모듈이 중복 정의하지 않음):
 * <pre>
 * spring:
 *   data:
 *     redis: { host: ..., port: 6379 }
 *   session:
 *     data:
 *       redis:
 *         namespace: si:session        # (Boot 4: spring.session.redis.* 에서 리네임)
 *         flush-mode: on-save
 *     timeout: 30m
 * </pre>
 */
@ConfigurationProperties(prefix = "framework.session")
public class FrameworkSessionProperties {

    /** 모듈 활성 여부. 의존성에 추가했다면 보통 그대로 둔다(true). */
    private boolean enabled = true;

    /** 모듈은 적재됐으나 보안 세션 모드가 꺼져 있는(=세션 공유 무의미) 오설정을 부팅 시 경고. */
    private boolean warnIfModeStateless = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isWarnIfModeStateless() {
        return warnIfModeStateless;
    }

    public void setWarnIfModeStateless(boolean warnIfModeStateless) {
        this.warnIfModeStateless = warnIfModeStateless;
    }
}
