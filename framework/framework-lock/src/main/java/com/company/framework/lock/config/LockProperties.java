package com.company.framework.lock.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 분산 락 모듈 설정.
 *
 * <pre>{@code
 * framework:
 *   lock:
 *     enabled: false          # 선택형 → 명시적 on
 *     type: memory            # memory(단일JVM,기본) | redis | jdbc
 *     default-at-most-for: 5m # @SchedulerLock.atMostFor 미지정 시 기본 리스 상한
 *     default-at-least-for: 0 # @SchedulerLock.atLeastFor 미지정 시 기본 최소 보유
 *     scheduler:
 *       enabled: true         # @SchedulerLock 애스펙트 등록(기본 on; 끄면 애너테이션 무시)
 * }</pre>
 *
 * <p>운영(k8s 다중 replica)에서는 {@code type=redis} 또는 {@code type=jdbc} 를 쓴다. {@code memory} 는 인스턴스마다
 * 별도 맵이라 파드 간 상호배제가 되지 않는다(개발/단일 인스턴스 전용).
 */
@ConfigurationProperties(prefix = "framework.lock")
public class LockProperties {

    /** 선택형 → 기본 off. */
    private boolean enabled = false;

    /** 백엔드 선택: memory | redis | jdbc. */
    private String type = "memory";

    /** {@code @SchedulerLock.atMostFor} 미지정 시 기본 리스 상한. */
    private Duration defaultAtMostFor = Duration.ofMinutes(5);

    /** {@code @SchedulerLock.atLeastFor} 미지정 시 기본 최소 보유(기본 0 = 종료 즉시 해제). */
    private Duration defaultAtLeastFor = Duration.ZERO;

    private final Scheduler scheduler = new Scheduler();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Duration getDefaultAtMostFor() {
        return defaultAtMostFor;
    }

    public void setDefaultAtMostFor(Duration defaultAtMostFor) {
        this.defaultAtMostFor = defaultAtMostFor;
    }

    public Duration getDefaultAtLeastFor() {
        return defaultAtLeastFor;
    }

    public void setDefaultAtLeastFor(Duration defaultAtLeastFor) {
        this.defaultAtLeastFor = defaultAtLeastFor;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    /** {@code @SchedulerLock} 애스펙트 토글. */
    public static class Scheduler {
        /** 애스펙트 등록 여부(기본 true). false 면 {@code @SchedulerLock} 애너테이션이 무시된다. */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
