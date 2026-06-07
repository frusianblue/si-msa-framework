package com.example.batchtypes.jobs.multithreaded;

import java.math.BigDecimal;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * <b>처리 방식 — 멀티스레드 청크.</b> 한 스텝 안에서 청크를 여러 스레드로 병렬 처리한다
 * ({@code .taskExecutor(...)}). 처리량이 큰 단순 변환에 효과적.
 *
 * <p><b>왜 {@link JdbcPagingItemReader} 인가</b>: 멀티스레드 스텝에서 <u>커서 리더는 안전하지 않다</u>
 * (단일 ResultSet/커넥션 공유). 페이징 리더는 페이지 단위로 끊어 읽어 멀티스레드에 적합하다.
 * 동시성에선 재시작 의미가 깨지므로 {@code saveState(false)}. 페이징은 결정적 정렬이 필수라 {@code sortKeys} 지정.
 */
@Configuration
public class MultiThreadedChunkJobConfig {

    @Bean
    public Job multiThreadedChunkJob(JobRepository jobRepository, Step gradeStep) {
        return new JobBuilder("multiThreadedChunkJob", jobRepository).start(gradeStep).build();
    }

    @Bean
    public Step gradeStep(
            JobRepository jobRepository,
            PlatformTransactionManager tx,
            JdbcPagingItemReader<TxnRow> txnPagingReader,
            JdbcBatchItemWriter<GradedTxn> gradedWriter,
            TaskExecutor batchTaskExecutor) {
        return new StepBuilder("gradeStep", jobRepository)
                .<TxnRow, GradedTxn>chunk(100, tx)
                .reader(txnPagingReader)
                .processor(txn -> new GradedTxn(txn.id(), txn.merchantId(), grade(txn.amount())))
                .writer(gradedWriter)
                .taskExecutor(batchTaskExecutor) // ← 멀티스레드 청크
                .build();
    }

    private static String grade(BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("1000000")) >= 0) {
            return "A";
        }
        if (amount.compareTo(new BigDecimal("100000")) >= 0) {
            return "B";
        }
        return "C";
    }

    @Bean
    public JdbcPagingItemReader<TxnRow> txnPagingReader(DataSource dataSource) {
        return new JdbcPagingItemReaderBuilder<TxnRow>()
                .name("txnPagingReader")
                .dataSource(dataSource)
                .selectClause("SELECT id, merchant_id, amount, status")
                .fromClause("FROM txn")
                .sortKeys(Map.of("id", Order.ASCENDING)) // 페이징은 결정적 정렬 필수
                .pageSize(100)
                .saveState(false) // 멀티스레드 → 재시작 상태 저장 비활성
                .rowMapper((rs, rowNum) -> new TxnRow(
                        rs.getLong("id"),
                        rs.getString("merchant_id"),
                        rs.getBigDecimal("amount"),
                        rs.getString("status")))
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<GradedTxn> gradedWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<GradedTxn>()
                .dataSource(dataSource)
                .sql("INSERT INTO txn_graded (id, merchant_id, grade) VALUES (:id, :merchantId, :grade)")
                .beanMapped()
                .build();
    }

    @Bean
    public TaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setThreadNamePrefix("batch-grade-");
        executor.initialize();
        return executor;
    }
}
