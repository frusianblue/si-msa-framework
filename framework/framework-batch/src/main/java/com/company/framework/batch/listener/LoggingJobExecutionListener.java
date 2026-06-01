package com.company.framework.batch.listener;

import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;

/**
 * 배치 Job 시작/종료 표준 로깅 리스너(감사 친화: 잡명·실행ID·상태·종료코드·소요시간).
 *
 * <p>Spring Batch 는 리스너를 전역 자동부착하지 않으므로, 앱이 Job 정의 시 명시 부착한다:
 * <pre>{@code
 * @Bean Job myJob(JobRepository repo, Step step, LoggingJobExecutionListener listener) {
 *     return new JobBuilder("myJob", repo).listener(listener).start(step).build();
 * }
 * }</pre>
 */
public class LoggingJobExecutionListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingJobExecutionListener.class);

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info(
                "[batch] start job={} executionId={} params={}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(),
                jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        LocalDateTime start = jobExecution.getStartTime();
        LocalDateTime end = jobExecution.getEndTime();
        long durationMs =
                (start != null && end != null) ? Duration.between(start, end).toMillis() : -1L;
        log.info(
                "[batch] end job={} executionId={} status={} exitCode={} durationMs={}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getId(),
                jobExecution.getStatus(),
                jobExecution.getExitStatus().getExitCode(),
                durationMs);
    }
}
