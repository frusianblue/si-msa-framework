package com.company.framework.task.listener;

import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.task.listener.TaskExecutionListener;
import org.springframework.cloud.task.repository.TaskExecution;

/**
 * 태스크(Spring Cloud Task) 시작/종료/실패 표준 감사 로깅 리스너.
 *
 * <p>잡명·실행ID·외부실행ID(오케스트레이터 부여)·종료코드·소요시간·오류를 표준 포맷으로 남긴다(감사 친화).
 * Spring Cloud Task 의 {@code TaskLifecycleListener} 가 컨텍스트의 {@link TaskExecutionListener} 빈을 자동 수집하므로,
 * 본 리스너는 <b>빈으로 등록되기만 하면</b> 별도 부착 없이 호출된다({@code framework.task.enabled=true} 시 자동 등록).
 *
 * <p>{@link com.company.framework.batch.listener.LoggingJobExecutionListener}(배치 Job 단위 로깅)와 층위가 다르다:
 * 이쪽은 <b>태스크(프로세스 1회 실행)</b> 단위, 저쪽은 <b>배치 Job</b> 단위. 둘 다 켜면 태스크→배치 2계층 로깅이 남는다.
 */
public class FrameworkTaskExecutionListener implements TaskExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(FrameworkTaskExecutionListener.class);

    @Override
    public void onTaskStartup(TaskExecution taskExecution) {
        log.info(
                "[task] start name={} executionId={} externalExecutionId={} parentExecutionId={} args={}",
                taskExecution.getTaskName(),
                taskExecution.getExecutionId(),
                taskExecution.getExternalExecutionId(),
                taskExecution.getParentExecutionId(),
                taskExecution.getArguments());
    }

    @Override
    public void onTaskEnd(TaskExecution taskExecution) {
        log.info(
                "[task] end name={} executionId={} exitCode={} durationMs={} exitMessage={}",
                taskExecution.getTaskName(),
                taskExecution.getExecutionId(),
                taskExecution.getExitCode(),
                durationMs(taskExecution),
                taskExecution.getExitMessage());
    }

    @Override
    public void onTaskFailed(TaskExecution taskExecution, Throwable throwable) {
        log.error(
                "[task] failed name={} executionId={} durationMs={} errorMessage={}",
                taskExecution.getTaskName(),
                taskExecution.getExecutionId(),
                durationMs(taskExecution),
                (throwable != null ? throwable.toString() : taskExecution.getErrorMessage()),
                throwable);
    }

    private static long durationMs(TaskExecution taskExecution) {
        LocalDateTime start = taskExecution.getStartTime();
        LocalDateTime end = taskExecution.getEndTime();
        return (start != null && end != null) ? Duration.between(start, end).toMillis() : -1L;
    }
}
