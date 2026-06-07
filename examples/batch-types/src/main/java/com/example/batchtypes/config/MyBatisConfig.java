package com.example.batchtypes.config;

import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * MyBatis 수동 배선 — 부트 스타터 대신 직접 {@link SqlSessionFactoryBean} 을 구성한다.
 * 이유: {@code mybatis-spring-boot-starter} 는 현재 {@code mybatis-spring 3.0.5}(Batch 5 패키지)를
 * 끌어오는데, Batch 6 의 배치 리더/라이터는 {@code mybatis-spring 4.0.0+}(신패키지)이 필요하다.
 * build.gradle 에서 4.0.0 을 직접 선언했고, 여기선 그 SqlSessionFactory 만 만들면 된다.
 *
 * <p>{@link MapperScan} 으로 {@code jobs.mybatis} 패키지의 매퍼 인터페이스를 빈으로 등록한다.
 * 매퍼 XML 은 {@code classpath:mapper/*.xml}.
 */
@Configuration
@MapperScan(basePackages = "com.example.batchtypes.jobs.mybatis")
public class MyBatisConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
        return factoryBean.getObject();
    }
}
