package com.example.settlement.batch;

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
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 청크지향 정산 배치 — {@code raw_transaction} 을 읽어 수수료를 차감하고 {@code settlement_result} 에 적재.
 *
 * <p><b>Spring Batch 6 패키지 주의</b>: 인프라 아이템(reader/writer/ItemProcessor)은
 * {@code org.springframework.batch.infrastructure.item.*} 로 이동했다(5.x 까지의 {@code org.springframework.batch.item.*}
 * 아님). core(Job/Step/JobRepository/builder)는 {@code org.springframework.batch.core.*} 그대로. (PITFALLS 참조)
 *
 * <p>JobRepository/PlatformTransactionManager 는 Boot 의 Batch 자동구성이 제공(DataSource 필요). 청크 커밋 단위 500,
 * {@code faultTolerant().skip(IllegalArgumentException)} 로 비정상 한 건이 전체를 멈추지 않게 한다.
 */
@Configuration
public class SettlementJobConfig {

    /** {@code spring.batch.job.name=settlementJob} 가 기동 시 1회 실행한다. */
    @Bean
    public Job settlementJob(JobRepository jobRepository, Step settleStep) {
        return new JobBuilder("settlementJob", jobRepository).start(settleStep).build();
    }

    @Bean
    public Step settleStep(JobRepository jobRepository, PlatformTransactionManager tx, DataSource dataSource) {
        return new StepBuilder("settleStep", jobRepository)
                .<RawTransaction, SettlementRecord>chunk(500, tx)
                .reader(rawTransactionReader(dataSource))
                .processor(new SettlementProcessor())
                .writer(settlementWriter(dataSource))
                .faultTolerant()
                .skip(IllegalArgumentException.class)
                .skipLimit(100)
                .build();
    }

    /** 커서 기반 리더 — 대용량을 한 행씩 스트리밍(전체 메모리 적재 회피). */
    @Bean
    public JdbcCursorItemReader<RawTransaction> rawTransactionReader(DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<RawTransaction>()
                .name("rawTransactionReader")
                .dataSource(dataSource)
                .sql("SELECT id, merchant_id, amount, status, trade_date FROM raw_transaction WHERE settled = false ORDER BY id")
                .fetchSize(500)
                .rowMapper((rs, rowNum) -> new RawTransaction(
                        rs.getLong("id"),
                        rs.getString("merchant_id"),
                        rs.getBigDecimal("amount"),
                        rs.getString("status"),
                        rs.getDate("trade_date").toLocalDate()))
                .build();
    }

    /** 네임드 파라미터 라이터 — {@code beanMapped()} 가 SettlementRecord 게터로 {@code :merchantId} 등을 채운다. */
    @Bean
    public JdbcBatchItemWriter<SettlementRecord> settlementWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<SettlementRecord>()
                .dataSource(dataSource)
                .sql(
                        "INSERT INTO settlement_result (merchant_id, trade_date, gross_amount, fee_amount, net_amount) "
                                + "VALUES (:merchantId, :tradeDate, :grossAmount, :feeAmount, :netAmount)")
                .beanMapped()
                .build();
    }

    // 참고: 실제 운영이라면 reader SQL 의 WHERE 절(미정산분)과 별도 마킹 스텝/업데이트로 멱등성을 확보한다.
}
