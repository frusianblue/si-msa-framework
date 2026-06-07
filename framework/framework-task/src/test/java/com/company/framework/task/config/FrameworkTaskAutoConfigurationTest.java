package com.company.framework.task.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.company.framework.task.listener.FrameworkTaskExecutionListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.task.repository.TaskRepository;

/**
 * 태스크 오토컨피그 로딩/토글 스모크.
 *
 * <ul>
 *   <li>{@code framework.task.enabled=true} (+ {@link TaskRepository} 빈 존재) → {@link FrameworkTaskExecutionListener} 등록.
 *   <li>기본(미설정) → 어떤 태스크 빈도 만들지 않음.
 * </ul>
 *
 * <p>{@code TaskRepository}/{@code TaskExecutionListener} 는 {@code spring-cloud-task-core}({@code api}) 전이로
 * 클래스패스에 존재 → {@code @ConditionalOnClass(TaskExecutionListener)} 통과. 실제 Spring Cloud Task 자동구성을 띄우지
 * 않으므로 {@link TaskRepository} 빈을 mock 으로 제공해 {@code @ConditionalOnBean} 분기를 검증한다.
 */
class FrameworkTaskAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FrameworkTaskAutoConfiguration.class))
            .withBean(TaskRepository.class, () -> mock(TaskRepository.class));

    @Test
    @DisplayName("enabled=true (+TaskRepository) → FrameworkTaskExecutionListener 등록")
    void registersListenerWhenEnabled() {
        runner.withPropertyValues("framework.task.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FrameworkTaskExecutionListener.class);
        });
    }

    @Test
    @DisplayName("기본(비활성) → 어떤 태스크 빈도 만들지 않음")
    void backsOffWhenDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(FrameworkTaskExecutionListener.class);
        });
    }
}
