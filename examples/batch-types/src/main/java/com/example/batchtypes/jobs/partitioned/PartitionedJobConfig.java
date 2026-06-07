package com.example.batchtypes.jobs.partitioned;

import com.example.batchtypes.jobs.multithreaded.TxnRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.batch.core.configuration.annotation.StepScope;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * <b>처리 방식 — 로컬 파티셔닝.</b> 매니저 스텝이 {@link IdRangePartitioner} 로 데이터를 id 범위로 쪼개고,
 * 같은 워커 스텝을 파티션마다 인스턴스화해 병렬 실행한다. 멀티스레드 청크가 "한 스텝 안 동시성"이라면,
 * 파티셔닝은 "스텝 자체를 N개로 복제"라 더 큰 규모/원격 분산(remote partitioning)으로 확장된다.
 *
 * <p>워커 리더는 {@code @StepScope} 라 파티션마다 새로 생성되고, {@code stepExecutionContext} 의
 * {@code minId}/{@code maxId} 를 주입받아 자기 범위만 읽는다.
 */
@Configuration
public class PartitionedJobConfig {

    @Bean
    public Job partitionedJob(JobRepository jobRepository, Step partitionManagerStep) {
        return new JobBuilder("partitionedJob", jobRepository).start(partitionManagerStep).build();
    }

    /** 매니저 스텝 — gridSize 4 로 분할, 워커를 taskExecutor 로 병렬 실행. */
    @Bean
    public Step partitionManagerStep(
            JobRepository jobRepository, Step partitionWorkerStep, DataSource dataSource, TaskExecutor partitionExecutor) {
        return new StepBuilder("partitionManagerStep", jobRepository)
                .partitioner("partitionWorkerStep", new IdRangePartitioner(new JdbcTemplate(dataSource)))
                .step(partitionWorkerStep)
                .gridSize(4)
                .taskExecutor(partitionExecutor)
                .build();
    }

    /** 워커 스텝 — 자기 파티션 범위를 청크로 읽어 정산 결과 적재. */
    @Bean
    public Step partitionWorkerStep(
            JobRepository jobRepository,
            PlatformTransactionManager tx,
            JdbcPagingItemReader<TxnRow> partitionReader,
            JdbcBatchItemWriter<SettledTxn> settledWriter) {
        return new StepBuilder("partitionWorkerStep", jobRepository)
                .<TxnRow, SettledTxn>chunk(100, tx)
                .reader(partitionReader)
                .processor(txn -> new SettledTxn(
                        txn.id(),
                        txn.merchantId(),
                        txn.amount().multiply(new BigDecimal("0.985")).setScale(2, RoundingMode.HALF_UP)))
                .writer(settledWriter)
                .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<TxnRow> partitionReader(
            DataSource dataSource,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) {
        return new JdbcPagingItemReaderBuilder<TxnRow>()
                .name("partitionReader")
                .dataSource(dataSource)
                .selectClause("SELECT id, merchant_id, amount, status")
                .fromClause("FROM txn")
                // 파티션 경계값은 파티셔너가 만든 정수라 인젝션 위험 없음(사용자 입력 아님).
                .whereClause("id >= " + minId + " AND id <= " + maxId)
                .sortKeys(Map.of("id", Order.ASCENDING))
                .pageSize(100)
                .saveState(false)
                .rowMapper((rs, rowNum) -> new TxnRow(
                        rs.getLong("id"),
                        rs.getString("merchant_id"),
                        rs.getBigDecimal("amount"),
                        rs.getString("status")))
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<SettledTxn> settledWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<SettledTxn>()
                .dataSource(dataSource)
                .sql("INSERT INTO txn_settled (id, merchant_id, net_amount) VALUES (:id, :merchantId, :netAmount)")
                .beanMapped()
                .build();
    }

    @Bean
    public TaskExecutor partitionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setThreadNamePrefix("batch-partition-");
        executor.initialize();
        return executor;
    }
}
