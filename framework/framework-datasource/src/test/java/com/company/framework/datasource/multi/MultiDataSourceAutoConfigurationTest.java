package com.company.framework.datasource.multi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.datasource.config.MultiDataSourceAutoConfiguration;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 독립 다중 DB(multi) 통합 검증. 두 개의 인메모리 H2 를 서로 독립 DB 로 띄워, DB 별 빈 세트가 등록되고
 * 물리적으로 분리되며(한쪽 테이블이 다른 쪽엔 없음), {@code @Primary} 가 Boot 백오프를 유도하는지 확인한다.
 *
 * <p>주의: 작성 환경(Maven Central 차단)에서는 실행 불가 → 받는 쪽에서
 * {@code ./gradlew :framework:framework-datasource:test} 로 그린 확인.
 */
class MultiDataSourceAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(MultiDataSourceAutoConfiguration.class, DataSourceAutoConfiguration.class))
            .withPropertyValues(
                    "framework.datasource.multi.enabled=true",
                    "framework.datasource.multi.primary=order",
                    "framework.datasource.multi.sources.order.url=jdbc:h2:mem:multi_order;DB_CLOSE_DELAY=-1",
                    "framework.datasource.multi.sources.order.username=sa",
                    "framework.datasource.multi.sources.user.url=jdbc:h2:mem:multi_user;DB_CLOSE_DELAY=-1",
                    "framework.datasource.multi.sources.user.username=sa");

    @Test
    void registersFullBeanSetPerSource() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            for (String key : new String[] {"order", "user"}) {
                assertThat(context.containsBean(key + "DataSource")).isTrue();
                assertThat(context.containsBean(key + "SqlSessionFactory")).isTrue();
                assertThat(context.containsBean(key + "SqlSessionTemplate")).isTrue();
                assertThat(context.containsBean(key + "TransactionManager")).isTrue();
                assertThat(context.getBean(key + "DataSource", DataSource.class))
                        .isNotNull();
                assertThat(context.getBean(key + "SqlSessionFactory", SqlSessionFactory.class))
                        .isNotNull();
                assertThat(context.getBean(key + "SqlSessionTemplate", SqlSessionTemplate.class))
                        .isNotNull();
            }
        });
    }

    @Test
    void primaryKeyWinsTypeResolutionAndBootBacksOff() {
        // DataSourceAutoConfiguration 도 포함했지만 spring.datasource.url 미설정 → 우리 @Primary DS 로 백오프해야 기동 성공.
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            // 이름 없는 타입 주입은 @Primary(order)로 해소.
            assertThat(context.getBean(DataSource.class))
                    .isSameAs(context.getBean("orderDataSource", DataSource.class));
            assertThat(context.getBean(PlatformTransactionManager.class))
                    .isSameAs(context.getBean("orderTransactionManager", PlatformTransactionManager.class));
            assertThat(context.getBean(SqlSessionFactory.class))
                    .isSameAs(context.getBean("orderSqlSessionFactory", SqlSessionFactory.class));
        });
    }

    @Test
    void eachTransactionManagerWrapsItsOwnDataSource() {
        runner.run(context -> {
            JdbcTransactionManager orderTm = context.getBean("orderTransactionManager", JdbcTransactionManager.class);
            JdbcTransactionManager userTm = context.getBean("userTransactionManager", JdbcTransactionManager.class);
            assertThat(orderTm.getDataSource()).isSameAs(context.getBean("orderDataSource", DataSource.class));
            assertThat(userTm.getDataSource()).isSameAs(context.getBean("userDataSource", DataSource.class));
        });
    }

    @Test
    void databasesArePhysicallyIndependent() {
        runner.run(context -> {
            JdbcTemplate order = new JdbcTemplate(context.getBean("orderDataSource", DataSource.class));
            JdbcTemplate user = new JdbcTemplate(context.getBean("userDataSource", DataSource.class));

            order.execute("create table only_in_order(id int primary key)");
            order.update("insert into only_in_order(id) values (1)");
            assertThat(order.queryForObject("select count(*) from only_in_order", Integer.class))
                    .isEqualTo(1);

            // 다른 DB 에는 그 테이블이 없어야 한다(진짜 독립 DB 증명).
            assertThatThrownBy(() -> user.queryForObject("select count(*) from only_in_order", Integer.class))
                    .isInstanceOf(Exception.class);
        });
    }

    @Test
    void failsFastWhenRoutingAndMultiBothEnabled() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MultiDataSourceAutoConfiguration.class))
                .withPropertyValues(
                        "framework.datasource.multi.enabled=true",
                        "framework.datasource.routing.enabled=true",
                        "framework.datasource.multi.sources.order.url=jdbc:h2:mem:conflict;DB_CLOSE_DELAY=-1",
                        "framework.datasource.multi.sources.order.username=sa")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("동시에 켤 수 없습니다");
                });
    }

    @Test
    void failsFastWhenMultipleSourcesWithoutPrimary() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MultiDataSourceAutoConfiguration.class))
                .withPropertyValues(
                        "framework.datasource.multi.enabled=true",
                        "framework.datasource.multi.sources.order.url=jdbc:h2:mem:np_order;DB_CLOSE_DELAY=-1",
                        "framework.datasource.multi.sources.order.username=sa",
                        "framework.datasource.multi.sources.user.url=jdbc:h2:mem:np_user;DB_CLOSE_DELAY=-1",
                        "framework.datasource.multi.sources.user.username=sa")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("primary");
                });
    }
}
