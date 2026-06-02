package com.company.framework.batch.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.company.framework.batch.launch.JobLaunchSupport;
import com.company.framework.batch.listener.LoggingJobExecutionListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 배치 오토컨피그 로딩/토글 스모크.
 *
 * <ul>
 *   <li>{@code framework.batch.enabled=true} (+ {@link JobOperator} 빈 존재) → {@link JobLaunchSupport} +
 *       {@link LoggingJobExecutionListener} 등록.
 *   <li>기본(미설정) → 어떤 배치 빈도 만들지 않음.
 * </ul>
 *
 * <p>{@code JobOperator} 는 {@code spring-boot-starter-batch}({@code api}) 전이로 클래스패스에 존재 →
 * {@code @ConditionalOnClass(JobOperator)} 통과. 실제 Boot 의 batch 자동구성을 띄우지 않으므로 {@link JobOperator}
 * 빈을 mock 으로 제공해 {@code @ConditionalOnBean} 분기를 검증한다(스케줄러는 Quartz {@code Scheduler} 빈 부재로 비활성).
 */
class FrameworkBatchAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FrameworkBatchAutoConfiguration.class))
            .withBean(JobOperator.class, () -> mock(JobOperator.class));

    @Test
    @DisplayName("enabled=true (+JobOperator) → JobLaunchSupport + 리스너 등록")
    void registersBeansWhenEnabled() {
        runner.withPropertyValues("framework.batch.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JobLaunchSupport.class);
            assertThat(context).hasSingleBean(LoggingJobExecutionListener.class);
        });
    }

    @Test
    @DisplayName("기본(비활성) → 어떤 배치 빈도 만들지 않음")
    void backsOffWhenDisabled() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(JobLaunchSupport.class);
            assertThat(context).doesNotHaveBean(LoggingJobExecutionListener.class);
        });
    }
}
