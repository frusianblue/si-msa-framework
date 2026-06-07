package com.company.framework.task.config;

import com.company.framework.task.listener.FrameworkTaskExecutionListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.task.listener.TaskExecutionListener;
import org.springframework.cloud.task.repository.TaskRepository;
import org.springframework.context.annotation.Bean;

/**
 * 태스크 표준(Spring Cloud Task) 오토컨피그.
 *
 * <p>{@code framework.task.enabled=true} 이고 Spring Cloud Task 코어가 클래스패스에 있으며(=
 * {@link TaskExecutionListener} 존재), Spring Cloud Task 자동구성이 만든 {@link TaskRepository} 빈이 있을 때
 * {@link FrameworkTaskExecutionListener}(표준 감사 로깅)를 등록한다. 이 리스너는 SCT 의 {@code TaskLifecycleListener}
 * 가 자동 수집하므로 별도 부착이 필요 없다.
 *
 * <p>실행 이력 기록 자체(=TaskLifecycleListener 활성)는 앱 메인의
 * {@link com.company.framework.task.EnableFrameworkTask @EnableFrameworkTask}(= {@code @EnableTask})가 담당한다.
 * 즉, 이력 기록을 켜려면 {@code @EnableFrameworkTask} 부착이 필요하고, 본 토글은 그 위에 "표준 로깅 계층"을 더한다.
 *
 * <p>Spring Cloud Task 의 {@code SimpleTaskAutoConfiguration} 이후 평가되도록 {@code afterName} 으로 순서를 잡는다 →
 * {@code @ConditionalOnBean(TaskRepository)} 가 신뢰성 있게 동작한다(저장소는 DataSource 가 있으면 JDBC, 없으면 인메모리).
 */
@AutoConfiguration(afterName = "org.springframework.cloud.task.configuration.SimpleTaskAutoConfiguration")
@ConditionalOnClass(TaskExecutionListener.class)
@EnableConfigurationProperties(FrameworkTaskProperties.class)
public class FrameworkTaskAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "framework.task", name = "enabled", havingValue = "true")
    @ConditionalOnBean(TaskRepository.class)
    @ConditionalOnMissingBean
    public FrameworkTaskExecutionListener frameworkTaskExecutionListener() {
        return new FrameworkTaskExecutionListener();
    }
}
