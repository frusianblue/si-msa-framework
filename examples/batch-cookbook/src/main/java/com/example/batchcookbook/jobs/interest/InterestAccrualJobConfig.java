package com.example.batchcookbook.jobs.interest;

import javax.sql.DataSource;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * <b>패턴 5 — 내결함(fault-tolerant) Chunk + 재실행 가능.</b> 예금 이자 계산.
 *
 * <ul>
 *   <li><b>skip</b>: 불량 데이터({@code IllegalArgumentException}, 여기선 음수 잔액)는 건너뛰고
 *       {@code skipLimit} 까지 허용. 한 건이 전체를 멈추지 않게 한다.</li>
 *   <li><b>retry</b>: 일시적 장애({@code TransientDataAccessException}, 예: 데드락/일시적 커넥션)는
 *       {@code retryLimit} 까지 재시도. (영구 오류는 재시도해도 소용없으니 대상에서 제외.)</li>
 *   <li><b>RunIdIncrementer</b>: 같은 파라미터로 다시 돌려도 {@code run.id} 가 증가해 <b>새 JobInstance</b>로
 *       실행된다(동일 파라미터 재실행 시 "이미 완료" 거부를 피함). 일/배치 키가 따로 없을 때 유용.</li>
 * </ul>
 *
 * <p>{@code RunIdIncrementer} 는 Batch 6 에서 {@code core.job.parameters} 로 이동(5.x {@code launch.support} 아님).
 */
@Configuration
public class InterestAccrualJobConfig {

    @Bean
    public Job interestAccrualJob(JobRepository jobRepository, Step interestStep) {
        return new JobBuilder("interestAccrualJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(interestStep)
                .build();
    }

    @Bean
    public Step interestStep(
            JobRepository jobRepository,
            PlatformTransactionManager tx,
            JdbcCursorItemReader<DepositRow> depositReader,
            JdbcBatchItemWriter<InterestRecord> interestWriter) {
        return new StepBuilder("interestStep", jobRepository)
                .<DepositRow, InterestRecord>chunk(500, tx)
                .reader(depositReader)
                .processor(new InterestProcessor())
                .writer(interestWriter)
                .faultTolerant()
                .skip(IllegalArgumentException.class)
                .skipLimit(50)
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .build();
    }

    @Bean
    public JdbcCursorItemReader<DepositRow> depositReader(DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<DepositRow>()
                .name("depositReader")
                .dataSource(dataSource)
                .sql("SELECT id, account_no, balance, rate FROM deposit ORDER BY id")
                .fetchSize(500)
                .rowMapper((rs, rowNum) -> new DepositRow(
                        rs.getLong("id"),
                        rs.getString("account_no"),
                        rs.getBigDecimal("balance"),
                        rs.getBigDecimal("rate")))
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<InterestRecord> interestWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<InterestRecord>()
                .dataSource(dataSource)
                .sql("INSERT INTO interest_accrual (account_no, base_balance, interest) "
                        + "VALUES (:accountNo, :baseBalance, :interest)")
                .beanMapped()
                .build();
    }
}
