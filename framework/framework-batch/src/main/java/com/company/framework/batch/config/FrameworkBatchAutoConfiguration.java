package com.company.framework.batch.config;

import com.company.framework.batch.launch.JobLaunchSupport;
import com.company.framework.batch.listener.LoggingJobExecutionListener;
import com.company.framework.batch.scheduler.BatchSchedulerRegistrar;
import org.quartz.Scheduler;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * 배치/스케줄러 모듈 오토컨피그.
 *
 * <ul>
 *   <li><b>batch</b>({@code framework.batch.enabled=true}, {@code @ConditionalOnClass(JobOperator)} + Boot 가 만든
 *       {@link JobOperator} 빈 존재 시): {@link JobLaunchSupport} + {@link LoggingJobExecutionListener} 제공.
 *   <li><b>scheduler</b>({@code framework.scheduler.enabled=true} + {@code framework.batch.enabled=true}, Quartz 클래스 +
 *       Boot 가 만든 {@code Scheduler} 빈 존재 시): {@link BatchSchedulerRegistrar} 제공. → 스케줄러는 batch 도 함께 켜야 한다
 *       (Quartz 잡이 런타임에 {@link JobLaunchSupport} 를 조회하므로).
 * </ul>
 *
 * <p>Boot 의 batch/quartz 자동구성 이후 평가되도록 {@code afterName} 으로 순서를 잡는다(Boot 4 패키지 기준).
 * 호스트에 DataSource/트랜잭션매니저가 없으면 Boot 가 {@code JobOperator} 를 만들지 않아 본 모듈도 우아하게 비활성된다.
 */
@AutoConfiguration(
        afterName = {
            "org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration",
            "org.springframework.boot.quartz.autoconfigure.QuartzAutoConfiguration"
        })
@ConditionalOnClass(JobOperator.class)
@EnableConfigurationProperties({BatchProperties.class, SchedulerProperties.class})
public class FrameworkBatchAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "framework.batch", name = "enabled", havingValue = "true")
    @ConditionalOnBean(JobOperator.class)
    @ConditionalOnMissingBean
    public JobLaunchSupport jobLaunchSupport(JobOperator jobOperator) {
        return new JobLaunchSupport(jobOperator);
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework.batch", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public LoggingJobExecutionListener loggingJobExecutionListener() {
        return new LoggingJobExecutionListener();
    }

    /**
     * 스케줄러 하위 구성: Quartz 가 클래스패스에 있고 {@code framework.scheduler.enabled=true} 일 때만.
     * 등록기는 {@link JobLaunchSupport}(=batch 활성)와 {@link Scheduler} 빈이 모두 있어야 생성된다.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({Scheduler.class, QuartzJobBean.class})
    @ConditionalOnProperty(prefix = "framework.scheduler", name = "enabled", havingValue = "true")
    static class SchedulerConfiguration {

        @Bean
        @ConditionalOnBean(Scheduler.class)
        @ConditionalOnProperty(prefix = "framework.batch", name = "enabled", havingValue = "true")
        @ConditionalOnMissingBean
        public BatchSchedulerRegistrar batchSchedulerRegistrar(
                Scheduler scheduler, ApplicationContext applicationContext, SchedulerProperties properties) {
            return new BatchSchedulerRegistrar(scheduler, applicationContext, properties);
        }
    }
}
