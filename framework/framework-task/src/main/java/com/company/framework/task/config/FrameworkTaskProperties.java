package com.company.framework.task.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 태스크 표준(Spring Cloud Task) 모듈 토글.
 *
 * <pre>{@code
 * framework:
 *   task:
 *     enabled: false   # 선택형 → 명시적 on. 켜면 FrameworkTaskExecutionListener(표준 감사 로깅) 빈 제공.
 * }</pre>
 *
 * <p>이 토글은 <b>프레임워크가 얹는 표준 계층</b>(감사 로깅 리스너)만 켠다. 태스크 실행 이력 기록 자체는
 * 앱 메인의 {@link com.company.framework.task.EnableFrameworkTask @EnableFrameworkTask}(= Spring Cloud Task
 * {@code @EnableTask})가 활성화한다 — 둘은 역할이 다르다(아래 README 참조).
 *
 * <p>태스크 동작 세부(테이블 prefix, 단일 인스턴스 락, 배치 실패 종료코드 등)는 Spring Cloud Task 의
 * {@code spring.cloud.task.*} 표준 프로퍼티로 제어한다(프레임워크가 재정의하지 않음 — 혼동 방지).
 */
@ConfigurationProperties(prefix = "framework.task")
public class FrameworkTaskProperties {

    /** 선택형 → 기본 off. */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
