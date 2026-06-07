package com.example.batchcookbook.jobs.purge;

import javax.sql.DataSource;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 패턴 4 — 단일 Tasklet 정리 배치 와이어링. */
@Configuration
public class PurgeJobConfig {

    @Bean
    public Job purgeJob(JobRepository jobRepository, Step purgeStep) {
        return new JobBuilder("purgeJob", jobRepository).start(purgeStep).build();
    }

    @Bean
    public Step purgeStep(
            JobRepository jobRepository,
            PlatformTransactionManager tx,
            DataSource dataSource,
            @Value("${app.purge.retention-days:90}") int retentionDays) {
        return new StepBuilder("purgeStep", jobRepository)
                .tasklet(new PurgeTasklet(new JdbcTemplate(dataSource), retentionDays), tx)
                .build();
    }
}
