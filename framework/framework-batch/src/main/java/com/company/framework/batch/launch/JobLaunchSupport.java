package com.company.framework.batch.launch;

import java.util.Map;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;

/**
 * 배치 Job 온디맨드 기동 헬퍼. Spring Batch 6 의 {@link JobOperator}(구 JobLauncher 는 6.0 deprecated)를 래핑한다.
 *
 * <p>핵심 편의:
 * <ul>
 *   <li><b>재실행 보장</b>: 매 실행마다 고유 식별 파라미터({@value #RUN_ID_KEY})를 추가 → 파라미터가 같거나 없는 Job 도
 *       매번 새 JobInstance 로 실행된다(동일 파라미터 재실행 시 {@code JobInstanceAlreadyCompleteException} 회피).
 *   <li><b>체크예외 제거</b>: {@code start(...)} 의 체크예외({@link JobExecutionException} 계열)를
 *       {@link BatchLaunchException} 런타임으로 감싼다.
 * </ul>
 *
 * <p>주입해서 사용: {@code jobLaunchSupport.launch(myJob)} 또는 파라미터 맵/JobParameters 동반.
 */
public class JobLaunchSupport {

    /** 매 실행 고유값으로 넣는 식별 파라미터 키. */
    public static final String RUN_ID_KEY = "framework.run.id";

    private final JobOperator jobOperator;

    public JobLaunchSupport(JobOperator jobOperator) {
        this.jobOperator = jobOperator;
    }

    public JobExecution launch(Job job) {
        return launch(job, new JobParameters());
    }

    public JobExecution launch(Job job, JobParameters parameters) {
        JobParameters effective = new JobParametersBuilder()
                .addJobParameters(parameters)
                .addLong(RUN_ID_KEY, System.currentTimeMillis())
                .toJobParameters();
        try {
            return jobOperator.start(job, effective);
        } catch (JobExecutionException e) {
            throw new BatchLaunchException("배치 Job 기동 실패: " + job.getName(), e);
        }
    }

    /** String/Long/Double/그 외(toString) 타입을 식별 파라미터로 추가해 기동. */
    public JobExecution launch(Job job, Map<String, Object> parameters) {
        JobParametersBuilder builder = new JobParametersBuilder();
        if (parameters != null) {
            for (Map.Entry<String, Object> e : parameters.entrySet()) {
                Object v = e.getValue();
                switch (v) {
                    case null -> {
                        /* 빈 값은 건너뜀 */
                    }
                    case String s -> builder.addString(e.getKey(), s);
                    case Long l -> builder.addLong(e.getKey(), l);
                    case Integer i -> builder.addLong(e.getKey(), i.longValue());
                    case Double d -> builder.addDouble(e.getKey(), d);
                    default -> builder.addString(e.getKey(), v.toString());
                }
            }
        }
        return launch(job, builder.toJobParameters());
    }
}
