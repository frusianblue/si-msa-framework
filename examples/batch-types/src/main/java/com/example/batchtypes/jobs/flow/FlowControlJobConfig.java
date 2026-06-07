package com.example.batchtypes.jobs.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * <b>처리 방식 — 플로우 제어(조건 분기 + 병렬 split).</b> 스텝의 종료 상태로 흐름을 가른다.
 *
 * <pre>
 *   validate ──(FAILED)──▶ failAlert ──▶ (종료)
 *      │
 *    (그 외)
 *      ▼
 *   [ enrichA ‖ notifyB ]  ← split: 두 스텝 병렬
 *      ▼
 *   finalize
 * </pre>
 *
 * <p>플로우 DSL: {@code .start(step).on("FAILED").to(x).from(step).on("*").to(flow).next(step).end()}.
 * {@code Flow}/{@code FlowBuilder} 는 {@code core.job.builder}/{@code core.job.flow}. split 은 두 {@code Flow} 를
 * {@code taskExecutor} 로 동시에 실행한다(독립적인 두 후처리를 병렬화).
 */
@Configuration
public class FlowControlJobConfig {

    private static final Logger log = LoggerFactory.getLogger(FlowControlJobConfig.class);

    @Bean
    public Job flowControlJob(
            JobRepository jobRepository,
            Step validateStep,
            Step failAlertStep,
            Step enrichStepA,
            Step notifyStepB,
            Step finalizeStep,
            TaskExecutor flowExecutor) {

        Flow enrichFlow = new FlowBuilder<Flow>("enrichFlow").start(enrichStepA).build();
        Flow notifyFlow = new FlowBuilder<Flow>("notifyFlow").start(notifyStepB).build();
        Flow parallel = new FlowBuilder<Flow>("parallelFlow")
                .split(flowExecutor)
                .add(enrichFlow, notifyFlow)
                .build();

        return new JobBuilder("flowControlJob", jobRepository)
                .start(validateStep)
                .on("FAILED")
                .to(failAlertStep) // 검증 실패 분기
                .from(validateStep)
                .on("*")
                .to(parallel) // 성공 → 두 스텝 병렬
                .next(finalizeStep) // 병렬 종료 후 마무리
                .end()
                .build();
    }

    // --- 스텝들(데모용 로깅 Tasklet) ---

    @Bean
    public Step validateStep(JobRepository jobRepository, PlatformTransactionManager tx) {
        // 기본은 정상(COMPLETED)으로 둬 split 경로가 실행된다. 실패 분기를 보려면
        // 이 tasklet 이 ExitStatus.FAILED 를 내게(또는 예외) 바꾸면 failAlertStep 으로 라우팅된다.
        return logStep("validateStep", "입력 검증", jobRepository, tx);
    }

    @Bean
    public Step failAlertStep(JobRepository jobRepository, PlatformTransactionManager tx) {
        return logStep("failAlertStep", "검증 실패 — 운영 알림 발송", jobRepository, tx);
    }

    @Bean
    public Step enrichStepA(JobRepository jobRepository, PlatformTransactionManager tx) {
        return logStep("enrichStepA", "[병렬A] 집계/보강", jobRepository, tx);
    }

    @Bean
    public Step notifyStepB(JobRepository jobRepository, PlatformTransactionManager tx) {
        return logStep("notifyStepB", "[병렬B] 통지 큐 적재", jobRepository, tx);
    }

    @Bean
    public Step finalizeStep(JobRepository jobRepository, PlatformTransactionManager tx) {
        return logStep("finalizeStep", "마무리(상태 플래그/요약)", jobRepository, tx);
    }

    private Step logStep(String name, String message, JobRepository jobRepository, PlatformTransactionManager tx) {
        return new StepBuilder(name, jobRepository)
                .tasklet(
                        (contribution, chunkContext) -> {
                            log.info("[flow] {} — {}", name, message);
                            return RepeatStatus.FINISHED;
                        },
                        tx)
                .build();
    }

    @Bean
    public TaskExecutor flowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setThreadNamePrefix("batch-flow-");
        executor.initialize();
        return executor;
    }
}
