package com.example.batchcookbook.jobs.fileingest;

import java.math.BigDecimal;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * <b>패턴 1 — 파일(CSV) → DB 적재.</b> 외부 기관/가맹점이 내려준 거래 파일을 스테이징 테이블에 싣는,
 * SI 에서 가장 흔한 배치. {@code FlatFileItemReader}(구분자 CSV, 헤더 1줄 skip)로 한 줄씩 읽어
 * {@code JdbcBatchItemWriter}(네임드 파라미터 배치 INSERT)로 적재한다.
 *
 * <p>입력 위치는 {@code app.ingest.input} 으로 바꾼다(기본 classpath 샘플). 운영에선
 * {@code --app.ingest.input=file:/landing/transactions-20260607.csv} 처럼 실제 경로를 준다.
 *
 * <p><b>Batch 6 패키지 주의</b>: 파일 아이템은 {@code infrastructure.item.file.*}, DB 아이템은
 * {@code infrastructure.item.database.*}. core(Job/Step/builder)만 {@code batch.core.*}.
 */
@Configuration
public class FileIngestJobConfig {

    @Bean
    public Job fileIngestJob(JobRepository jobRepository, Step fileIngestStep) {
        return new JobBuilder("fileIngestJob", jobRepository).start(fileIngestStep).build();
    }

    @Bean
    public Step fileIngestStep(
            JobRepository jobRepository,
            PlatformTransactionManager tx,
            FlatFileItemReader<IncomingTransaction> incomingTxReader,
            JdbcBatchItemWriter<IncomingTransaction> incomingTxWriter) {
        return new StepBuilder("fileIngestStep", jobRepository)
                .<IncomingTransaction, IncomingTransaction>chunk(500, tx)
                .reader(incomingTxReader)
                .writer(incomingTxWriter)
                .build();
    }

    /**
     * CSV 리더. 헤더 1줄 skip, 쉼표 구분(기본). {@code fieldSetMapper} 로 컬럼을 명시 파싱한다
     * (자동 타입 바인딩 대신 직접 파싱 → LocalDate/BigDecimal 변환을 확실히 통제).
     */
    @Bean
    public FlatFileItemReader<IncomingTransaction> incomingTxReader(
            @Value("${app.ingest.input:classpath:sample/transactions.csv}") Resource input) {
        return new FlatFileItemReaderBuilder<IncomingTransaction>()
                .name("incomingTxReader")
                .resource(input)
                .linesToSkip(1) // 헤더 행 건너뜀
                .delimited()
                .names("id", "merchantId", "amount", "status", "tradeDate")
                .fieldSetMapper(fs -> {
                    IncomingTransaction t = new IncomingTransaction();
                    t.setId(fs.readLong("id"));
                    t.setMerchantId(fs.readString("merchantId"));
                    t.setAmount(new BigDecimal(fs.readString("amount")));
                    t.setStatus(fs.readString("status"));
                    t.setTradeDate(LocalDate.parse(fs.readString("tradeDate")));
                    return t;
                })
                .build();
    }

    /** 배치 INSERT — {@code beanMapped()} 가 IncomingTransaction 게터로 {@code :merchantId} 등을 채운다. */
    @Bean
    public JdbcBatchItemWriter<IncomingTransaction> incomingTxWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<IncomingTransaction>()
                .dataSource(dataSource)
                .sql("INSERT INTO incoming_transaction (id, merchant_id, amount, status, trade_date) "
                        + "VALUES (:id, :merchantId, :amount, :status, :tradeDate)")
                .beanMapped()
                .build();
    }
}
