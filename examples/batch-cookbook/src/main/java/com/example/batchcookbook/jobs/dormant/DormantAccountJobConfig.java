package com.example.batchcookbook.jobs.dormant;

import javax.sql.DataSource;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * <b>패턴 3 — 다단계 Job(Tasklet → Chunk).</b> 휴면계좌 전환 같은 "선(先) 마킹 → 후(後) 일괄처리"를
 * 두 스텝으로 나눈다. {@code JobBuilder.start(step1).next(step2)} 로 순차 실행한다.
 *
 * <ul>
 *   <li><b>step1 (Tasklet)</b>: 1년 이상 무활동 ACTIVE 계좌를 휴면 <i>후보</i>로 마킹(집합 UPDATE 1방).</li>
 *   <li><b>step2 (Chunk)</b>: 후보를 한 건씩 읽어 상태를 DORMANT 로 전환(대량을 청크 커밋으로).</li>
 * </ul>
 *
 * <p>두 스텝을 나누는 이유: 마킹은 집합연산 1방이 효율적이고, 전환은 건별 후처리(통지 발송 등 확장)를
 * 청크로 다루는 게 자연스럽다. 스텝 경계가 곧 재시작 지점이 된다(step1 성공 후 step2 실패 시 step2 부터 재시작).
 */
@Configuration
public class DormantAccountJobConfig {

    @Bean
    public Job dormantAccountJob(JobRepository jobRepository, Step markDormantStep, Step transitionDormantStep) {
        return new JobBuilder("dormantAccountJob", jobRepository)
                .start(markDormantStep)
                .next(transitionDormantStep)
                .build();
    }

    // ---- step1: Tasklet (마킹) ----

    @Bean
    public Step markDormantStep(JobRepository jobRepository, PlatformTransactionManager tx, DataSource dataSource) {
        return new StepBuilder("markDormantStep", jobRepository)
                .tasklet(new MarkDormantCandidatesTasklet(new JdbcTemplate(dataSource)), tx)
                .build();
    }

    // ---- step2: Chunk (전환) ----

    @Bean
    public Step transitionDormantStep(
            JobRepository jobRepository,
            PlatformTransactionManager tx,
            JdbcCursorItemReader<AccountRow> dormantCandidateReader,
            JdbcBatchItemWriter<AccountRow> dormantTransitionWriter) {
        return new StepBuilder("transitionDormantStep", jobRepository)
                .<AccountRow, AccountRow>chunk(200, tx)
                .reader(dormantCandidateReader)
                .writer(dormantTransitionWriter)
                .build();
    }

    @Bean
    public JdbcCursorItemReader<AccountRow> dormantCandidateReader(DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<AccountRow>()
                .name("dormantCandidateReader")
                .dataSource(dataSource)
                .sql("SELECT id, owner FROM account WHERE dormant_candidate = true AND status = 'ACTIVE' ORDER BY id")
                .fetchSize(200)
                .rowMapper((rs, rowNum) -> new AccountRow(rs.getLong("id"), rs.getString("owner")))
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<AccountRow> dormantTransitionWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<AccountRow>()
                .dataSource(dataSource)
                .sql("UPDATE account SET status = 'DORMANT', dormant_candidate = false WHERE id = :id")
                .beanMapped()
                .build();
    }
}
