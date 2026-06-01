package com.company.framework.batch.scheduler;

import com.company.framework.batch.launch.JobLaunchSupport;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.springframework.batch.core.job.Job;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * Quartz 트리거가 발화하면 지정된 Spring Batch {@link Job} 빈을 찾아 기동하는 어댑터.
 *
 * <p>Quartz 가 인스턴스를 직접 생성하므로 Spring 자동주입에 의존하지 않고, 등록 시 스케줄러 컨텍스트에 넣어 둔
 * {@link ApplicationContext}({@value #APP_CONTEXT_KEY})에서 Job 빈과 {@link JobLaunchSupport} 를 직접 조회한다.
 * 실행할 Job 빈 이름은 JobDataMap({@value #JOB_NAME_KEY})으로 전달된다.
 */
public class BatchJobQuartzJob extends QuartzJobBean {

    public static final String JOB_NAME_KEY = "jobName";
    public static final String APP_CONTEXT_KEY = "applicationContext";

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        String jobName = context.getMergedJobDataMap().getString(JOB_NAME_KEY);
        try {
            ApplicationContext applicationContext =
                    (ApplicationContext) context.getScheduler().getContext().get(APP_CONTEXT_KEY);
            if (applicationContext == null) {
                throw new IllegalStateException("스케줄러 컨텍스트에 applicationContext 가 없습니다.");
            }
            Job batchJob = applicationContext.getBean(jobName, Job.class);
            JobLaunchSupport launcher = applicationContext.getBean(JobLaunchSupport.class);
            launcher.launch(batchJob);
        } catch (SchedulerException | RuntimeException e) {
            // Quartz 에 실패를 알려 재시도/로깅 정책을 태운다(refireImmediately 는 기본 미사용).
            throw new JobExecutionException("스케줄 배치 기동 실패: jobName=" + jobName, e);
        }
    }
}
