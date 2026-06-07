package com.example.batchtypes.jobs.composite;

import com.example.batchtypes.jobs.multithreaded.TxnRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import javax.sql.DataSource;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.infrastructure.item.support.CompositeItemProcessor;
import org.springframework.batch.infrastructure.item.support.builder.CompositeItemProcessorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * <b>처리 방식 — 처리기 체인(CompositeItemProcessor).</b> 하나의 처리 단계를 여러 작은 처리기로 쪼개
 * 순서대로 흘린다(앞 처리기 출력 = 다음 처리기 입력). 검증 → 보강(가공)처럼 책임을 분리해 재사용/테스트가 쉽다.
 *
 * <ul>
 *   <li>1) {@code validate}: CANCELED 거래는 {@code null} 반환 → 필터(라이터로 안 감).</li>
 *   <li>2) {@code enrich}: 수수료(1.5%)·실수령·카테고리 계산 → {@link EnrichedTxn} 으로 변환.</li>
 * </ul>
 */
@Configuration
public class CompositeProcessorJobConfig {

    @Bean
    public Job compositeProcessorJob(JobRepository jobRepository, Step enrichStep) {
        return new JobBuilder("compositeProcessorJob", jobRepository).start(enrichStep).build();
    }

    @Bean
    public Step enrichStep(
            JobRepository jobRepository,
            PlatformTransactionManager tx,
            JdbcCursorItemReader<TxnRow> txnCursorReader,
            CompositeItemProcessor<TxnRow, EnrichedTxn> enrichComposite,
            JdbcBatchItemWriter<EnrichedTxn> enrichedWriter) {
        return new StepBuilder("enrichStep", jobRepository)
                .<TxnRow, EnrichedTxn>chunk(200, tx)
                .reader(txnCursorReader)
                .processor(enrichComposite)
                .writer(enrichedWriter)
                .build();
    }

    /** 1) 검증/필터 처리기 — CANCELED 면 null(필터). */
    @Bean
    public ItemProcessor<TxnRow, TxnRow> validateProcessor() {
        return txn -> "CANCELED".equals(txn.status()) ? null : txn;
    }

    /** 2) 보강 처리기 — 수수료/실수령/카테고리 계산. */
    @Bean
    public ItemProcessor<TxnRow, EnrichedTxn> enrichProcessor() {
        return txn -> {
            BigDecimal fee = txn.amount().multiply(new BigDecimal("0.015")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = txn.amount().subtract(fee);
            String category = txn.amount().compareTo(new BigDecimal("1000000")) >= 0 ? "LARGE" : "NORMAL";
            return new EnrichedTxn(txn.id(), txn.merchantId(), txn.amount(), fee, net, category);
        };
    }

    /** 체인 합성 — delegates 순서대로 실행(validate → enrich). */
    @Bean
    public CompositeItemProcessor<TxnRow, EnrichedTxn> enrichComposite(
            ItemProcessor<TxnRow, TxnRow> validateProcessor, ItemProcessor<TxnRow, EnrichedTxn> enrichProcessor) {
        return new CompositeItemProcessorBuilder<TxnRow, EnrichedTxn>()
                .delegates(validateProcessor, enrichProcessor)
                .build();
    }

    @Bean
    public JdbcCursorItemReader<TxnRow> txnCursorReader(DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<TxnRow>()
                .name("txnCursorReader")
                .dataSource(dataSource)
                .sql("SELECT id, merchant_id, amount, status FROM txn ORDER BY id")
                .fetchSize(200)
                .rowMapper((rs, rowNum) -> new TxnRow(
                        rs.getLong("id"),
                        rs.getString("merchant_id"),
                        rs.getBigDecimal("amount"),
                        rs.getString("status")))
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<EnrichedTxn> enrichedWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<EnrichedTxn>()
                .dataSource(dataSource)
                .sql("INSERT INTO txn_enriched (id, merchant_id, amount, fee, net_amount, category) "
                        + "VALUES (:id, :merchantId, :amount, :fee, :netAmount, :category)")
                .beanMapped()
                .build();
    }
}
