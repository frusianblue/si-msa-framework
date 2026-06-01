package com.company.framework.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 배치 실행 지원 모듈 토글.
 *
 * <pre>{@code
 * framework:
 *   batch:
 *     enabled: false   # 선택형 → 명시적 on. 켜면 JobLaunchSupport / LoggingJobExecutionListener 빈 제공.
 * }</pre>
 *
 * <p>참고: Spring Boot 는 {@code spring.batch.job.name} 이 설정된 경우에만 기동 시 Job 을 자동 실행한다.
 * 미설정이 기본이므로 본 모듈을 켜도 기동 시 자동 실행은 없으며, 실행은 {@code JobLaunchSupport} 또는 스케줄러로 한다.
 */
@ConfigurationProperties(prefix = "framework.batch")
public class BatchProperties {

    /** 선택형 → 기본 off. */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
