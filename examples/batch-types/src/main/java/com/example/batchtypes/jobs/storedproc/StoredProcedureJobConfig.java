package com.example.batchtypes.jobs.storedproc;

import com.example.batchtypes.jobs.multithreaded.TxnRow;
import java.sql.Types;
import javax.sql.DataSource;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.StoredProcedureItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.StoredProcedureItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * <b>DB 연동 — 저장 프로시저 리더.</b> 레거시/금융 SI 에서 흔한 "서버측 프로시저가 결과 집합을 돌려주는" 연계.
 * PostgreSQL 함수가 {@code refcursor} 를 반환하고, {@link StoredProcedureItemReader} 가 그 커서를 한 행씩 읽는다.
 *
 * <p>리더 출력은 {@code TxnRow}(record) 이고, 라이터는 {@code itemPreparedStatementSetter}(위치형 {@code ?})
 * 로 적재한다 — beanMapped(게터 필요)와 달리 <b>record 를 그대로</b> 쓸 수 있는 또 하나의 라이터 방식.
 *
 * <p>⚠️ <b>PostgreSQL refcursor 주의</b>: refcursor 는 <u>트랜잭션 안에서만</u> 유효하다. 청크 스텝이 트랜잭션을
 * 제공하므로 동작하지만, 커서를 여는 호출과 읽기가 같은 커넥션/트랜잭션이어야 한다. 함수 정의는 {@code V4__stored_proc.sql}.
 */
@Configuration
public class StoredProcedureJobConfig {

    @Bean
    public Job storedProcedureJob(JobRepository jobRepository, Step procStep) {
        return new JobBuilder("storedProcedureJob", jobRepository).start(procStep).build();
    }

    @Bean
    public Step procStep(
            JobRepository jobRepository,
            PlatformTransactionManager tx,
            StoredProcedureItemReader<TxnRow> procReader,
            JdbcBatchItemWriter<TxnRow> procWriter) {
        return new StepBuilder("procStep", jobRepository)
                .<TxnRow, TxnRow>chunk(100, tx)
                .reader(procReader)
                .writer(procWriter)
                .build();
    }

    @Bean
    public StoredProcedureItemReader<TxnRow> procReader(DataSource dataSource) {
        return new StoredProcedureItemReaderBuilder<TxnRow>()
                .name("procReader")
                .dataSource(dataSource)
                .procedureName("get_approved_txn") // refcursor 반환 함수
                .function() // 프로시저가 아닌 "함수(반환값)" 형태
                .refCursorPosition(1) // 반환 refcursor 위치
                .parameters(new SqlOutParameter("results", Types.REF_CURSOR))
                .rowMapper((rs, rowNum) -> new TxnRow(
                        rs.getLong("id"),
                        rs.getString("merchant_id"),
                        rs.getBigDecimal("amount"),
                        rs.getString("status")))
                .build();
    }

    /** 위치형 PreparedStatement 매핑 — record(TxnRow) 를 게터 없이 그대로 적재. */
    @Bean
    public JdbcBatchItemWriter<TxnRow> procWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<TxnRow>()
                .dataSource(dataSource)
                .sql("INSERT INTO txn_proc_out (id, merchant_id, amount) VALUES (?, ?, ?)")
                .itemPreparedStatementSetter((item, ps) -> {
                    ps.setLong(1, item.id());
                    ps.setString(2, item.merchantId());
                    ps.setBigDecimal(3, item.amount());
                })
                .build();
    }
}
