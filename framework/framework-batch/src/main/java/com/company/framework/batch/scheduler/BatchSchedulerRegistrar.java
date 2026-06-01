package com.company.framework.batch.scheduler;

import com.company.framework.batch.config.SchedulerProperties;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;

/**
 * {@code framework.scheduler.jobs[*]} 선언을 읽어 각 Spring Batch Job 을 Quartz cron 트리거로 등록한다.
 *
 * <p>{@link SmartLifecycle} 로 컨텍스트 기동 마지막 단계에서 동작(Quartz Scheduler 준비 후). 등록 시
 * {@link ApplicationContext} 를 스케줄러 컨텍스트에 넣어 {@link BatchJobQuartzJob} 이 런타임에 Job 빈을 조회하게 한다.
 *
 * <p>잘못된 cron/이름 누락은 <b>기동 실패(fail-fast)</b> 로 처리(배포 전 발견 목적). 재기동 시 동일 키는 갱신(delete→schedule).
 */
public class BatchSchedulerRegistrar implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(BatchSchedulerRegistrar.class);

    private final Scheduler scheduler;
    private final ApplicationContext applicationContext;
    private final SchedulerProperties properties;
    private volatile boolean running = false;

    public BatchSchedulerRegistrar(
            Scheduler scheduler, ApplicationContext applicationContext, SchedulerProperties properties) {
        this.scheduler = scheduler;
        this.applicationContext = applicationContext;
        this.properties = properties;
    }

    @Override
    public void start() {
        try {
            scheduler.getContext().put(BatchJobQuartzJob.APP_CONTEXT_KEY, applicationContext);
            for (SchedulerProperties.JobSchedule job : properties.getJobs()) {
                if (!job.isEnabled()) {
                    log.info("[scheduler] 비활성 스킵 job={}", job.getName());
                    continue;
                }
                validate(job);
                register(job);
            }
            running = true;
        } catch (SchedulerException e) {
            throw new IllegalStateException("배치 스케줄 등록 실패", e);
        }
    }

    private void validate(SchedulerProperties.JobSchedule job) {
        if (job.getName() == null || job.getName().isBlank()) {
            throw new IllegalStateException("framework.scheduler.jobs[*].name 이 비어 있습니다.");
        }
        if (job.getCron() == null || !CronExpression.isValidExpression(job.getCron())) {
            throw new IllegalStateException("유효하지 않은 cron 식: job=" + job.getName() + " cron=" + job.getCron());
        }
    }

    private void register(SchedulerProperties.JobSchedule job) throws SchedulerException {
        JobKey jobKey = new JobKey(job.getName(), job.getGroup());
        JobDetail detail = JobBuilder.newJob(BatchJobQuartzJob.class)
                .withIdentity(jobKey)
                .storeDurably()
                .usingJobData(BatchJobQuartzJob.JOB_NAME_KEY, job.getName())
                .build();

        TriggerKey triggerKey = new TriggerKey(job.getName(), job.getGroup());
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKey)
                .forJob(jobKey)
                .withSchedule(CronScheduleBuilder.cronSchedule(job.getCron()))
                .build();

        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey); // 재기동 시 cron 변경 반영
        }
        scheduler.scheduleJob(detail, trigger);
        log.info("[scheduler] 등록 job={} cron={} group={}", job.getName(), job.getCron(), job.getGroup());
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        // 가장 늦게 시작 → Quartz Scheduler 가 완전히 올라온 뒤 등록.
        return Integer.MAX_VALUE;
    }
}
