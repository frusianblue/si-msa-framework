package com.example.batchtypes.jobs.mybatis;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.batch.MyBatisBatchItemWriter;
import org.mybatis.spring.batch.MyBatisPagingItemReader;
import org.mybatis.spring.batch.builder.MyBatisBatchItemWriterBuilder;
import org.mybatis.spring.batch.builder.MyBatisPagingItemReaderBuilder;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * <b>DB 연동 — MyBatis 리더/라이터.</b> 이 프레임워크의 영속 스택(MyBatis)으로 배치를 구성한다.
 * {@link MyBatisPagingItemReader}(매퍼 select, 페이징) → {@link MyBatisBatchItemWriter}(매퍼 insert, 배치 실행).
 *
 * <p>statement 는 매퍼 XML({@code mapper/SettlementMapper.xml})에 정의하고, 리더/라이터는 그 <b>statement id 문자열</b>로
 * 가리킨다. 페이징 select 는 {@code LIMIT #{_pagesize} OFFSET #{_skiprows}} 를 써야 한다(MyBatis 가 주입).
 *
 * <p>⚠️ Batch 6 에선 {@code mybatis-spring 4.0.0+} 필수(배치 클래스가 신패키지 사용). 3.0.x 는 비호환.
 */
@Configuration
public class MyBatisJobConfig {

    private static final String NS = "com.example.batchtypes.jobs.mybatis.SettlementMapper";

    @Bean
    public Job mybatisJob(JobRepository jobRepository, Step mybatisStep) {
        return new JobBuilder("mybatisJob", jobRepository).start(mybatisStep).build();
    }

    @Bean
    public Step mybatisStep(
            JobRepository jobRepository,
            PlatformTransactionManager tx,
            MyBatisPagingItemReader<SettlementMb> mybatisReader,
            MyBatisBatchItemWriter<SettlementMb> mybatisWriter) {
        return new StepBuilder("mybatisStep", jobRepository)
                .<SettlementMb, SettlementMb>chunk(100, tx)
                .reader(mybatisReader)
                .writer(mybatisWriter)
                .build();
    }

    @Bean
    public MyBatisPagingItemReader<SettlementMb> mybatisReader(SqlSessionFactory sqlSessionFactory) {
        return new MyBatisPagingItemReaderBuilder<SettlementMb>()
                .sqlSessionFactory(sqlSessionFactory)
                .queryId(NS + ".selectTxnPage")
                .pageSize(100)
                .build();
    }

    @Bean
    public MyBatisBatchItemWriter<SettlementMb> mybatisWriter(SqlSessionFactory sqlSessionFactory) {
        return new MyBatisBatchItemWriterBuilder<SettlementMb>()
                .sqlSessionFactory(sqlSessionFactory)
                .statementId(NS + ".insertOut")
                .build();
    }
}
