package com.company.framework.datasource.config;

import com.company.framework.datasource.multi.MultiDataSourceRegistrar;
import com.zaxxer.hikari.HikariDataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 독립 다중 DB 오토컨피그(읽기/쓰기 분리 {@code routing} 과 별개의 선택형 서브기능).
 *
 * <p>토글 단계:
 *
 * <ul>
 *   <li>1단(모듈): {@code @ConditionalOnClass(SqlSessionFactoryBean, HikariDataSource)} — MyBatis/Hikari 가 있어야 활성.
 *   <li>2단(기능): {@code framework.datasource.multi.enabled=true}.
 *   <li>3단(세부): {@code sources.<키>.*} 접속/풀/MyBatis 설정과 {@code primary} 선택.
 * </ul>
 *
 * <p><b>순서</b>: {@code @AutoConfiguration(before = {DataSourceAutoConfiguration, MybatisAutoConfiguration})} 으로
 * Boot 의 DataSource·MyBatis 자동구성보다 먼저 평가된다. {@link MultiDataSourceRegistrar} 가 {@code @Import} 로 이 단계에서
 * {@code @Primary} DataSource/SqlSessionFactory 빈 <i>정의</i>를 등록하므로, Boot 측 {@code @ConditionalOnMissingBean}
 * 들이 그 정의를 보고 백오프한다. ({@code MybatisAutoConfiguration} 은 런타임 부재 가능성을 고려해 {@code beforeName} 으로 참조.)
 *
 * <p>이 모듈을 켜면 {@code spring.datasource.*} 대신 {@code framework.datasource.multi.sources.*} 가 실제 접속 정보다.
 * 매퍼 스캔({@code @MapperScan(sqlSessionFactoryRef=...)})과 보조 DB 트랜잭션 지정({@code @Transactional("<키>TransactionManager")})은
 * 패키지를 아는 앱이 선언한다.
 */
@AutoConfiguration(
        before = DataSourceAutoConfiguration.class,
        beforeName = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
@ConditionalOnClass({SqlSessionFactoryBean.class, HikariDataSource.class})
@ConditionalOnProperty(prefix = "framework.datasource.multi", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MultiDataSourceProperties.class)
@Import(MultiDataSourceRegistrar.class)
public class MultiDataSourceAutoConfiguration {}
