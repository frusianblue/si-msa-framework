package com.example.batchcookbook.jobs.reportexport;

import javax.sql.DataSource;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * <b>패턴 2 — DB → 파일(CSV) 추출.</b> 정산 결과를 대외기관 송신용/EAI 연계용 CSV 로 떨어뜨린다.
 * {@code JdbcCursorItemReader}(커서 스트리밍)로 읽어 {@code FlatFileItemWriter}(헤더 + 구분자 라인)로 쓴다.
 *
 * <p>출력 경로는 {@code app.report.output}(기본 {@code /tmp/settlement-report.csv}).
 * {@code shouldDeleteIfExists(true)} 로 재실행 시 기존 파일을 덮어쓴다. 헤더는 {@code headerCallback}.
 *
 * <p><b>WritableResource 주의</b>: {@code FlatFileItemWriter.resource(...)} 는 쓰기 가능한 리소스를 요구한다.
 * {@code file:} 로케이션 문자열은 읽기 전용 {@code UrlResource} 로 해소될 수 있어, 경로 문자열을 받아
 * {@code FileSystemResource}(WritableResource 구현)로 직접 만든다.
 */
@Configuration
public class ReportExportJobConfig {

    @Bean
    public Job reportExportJob(JobRepository jobRepository, Step reportExportStep) {
        return new JobBuilder("reportExportJob", jobRepository).start(reportExportStep).build();
    }

    @Bean
    public Step reportExportStep(
            JobRepository jobRepository,
            PlatformTransactionManager tx,
            JdbcCursorItemReader<SettlementRow> settlementReader,
            FlatFileItemWriter<SettlementRow> settlementCsvWriter) {
        return new StepBuilder("reportExportStep", jobRepository)
                .<SettlementRow, SettlementRow>chunk(1000, tx)
                .reader(settlementReader)
                .writer(settlementCsvWriter)
                .build();
    }

    @Bean
    public JdbcCursorItemReader<SettlementRow> settlementReader(DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<SettlementRow>()
                .name("settlementReader")
                .dataSource(dataSource)
                .sql("SELECT merchant_id, trade_date, net_amount FROM settlement_result ORDER BY merchant_id, trade_date")
                .fetchSize(1000)
                .rowMapper((rs, rowNum) -> new SettlementRow(
                        rs.getString("merchant_id"),
                        rs.getDate("trade_date").toLocalDate(),
                        rs.getBigDecimal("net_amount")))
                .build();
    }

    @Bean
    public FlatFileItemWriter<SettlementRow> settlementCsvWriter(
            @Value("${app.report.output:/tmp/settlement-report.csv}") String outputPath) {
        return new FlatFileItemWriterBuilder<SettlementRow>()
                .name("settlementCsvWriter")
                .resource(new FileSystemResource(outputPath))
                .shouldDeleteIfExists(true)
                .headerCallback(writer -> writer.write("merchant_id,trade_date,net_amount"))
                .delimited()
                .names("merchantId", "tradeDate", "netAmount")
                .build();
    }
}
