package com.company.framework.datasource.config;

import com.company.framework.datasource.routing.DataSourceType;
import com.company.framework.datasource.routing.RoutingDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 읽기/쓰기 분리 라우팅 DataSource 오토컨피그.
 *
 * <p>토글 단계:
 *
 * <ul>
 *   <li>1단(모듈): {@code @ConditionalOnClass(AbstractRoutingDataSource, HikariDataSource)} — jdbc/Hikari 가 있어야 활성.
 *   <li>2단(기능): {@code framework.datasource.routing.enabled=true}.
 *   <li>3단(세부): {@code write/read} 노드 접속·풀 설정. read.url 미지정 시 READ→WRITE 로 폴백(단일 DB 무해).
 * </ul>
 *
 * <p><b>Boot 기본 DataSource 와의 관계</b>: 본 오토컨피그는 {@code @AutoConfiguration(before = DataSourceAutoConfiguration.class)}
 * 로 Boot 의 DataSource 오토컨피그보다 먼저 평가된다. 여기서 {@code @Primary} DataSource 빈을 등록하면 Boot 의
 * {@code DataSourceAutoConfiguration}(풀 생성이 {@code @ConditionalOnMissingBean(DataSource)})은 백오프한다. 따라서
 * 라우팅을 켜면 {@code spring.datasource.*} 대신 {@code framework.datasource.routing.write/read} 가 실제 접속 정보가 된다.
 * 사용자가 직접 DataSource 빈을 정의한 경우엔 {@code @ConditionalOnMissingBean} 으로 본 모듈이 양보한다.
 *
 * <p><b>라우팅 정확도</b>: {@link LazyConnectionDataSourceProxy} 로 감싸 트랜잭션 시작이 아니라 최초 실제 쿼리 시점에
 * 물리 connection 을 잡게 한다 → 그 시점엔 {@code @Transactional(readOnly)} 플래그가 이미 바인딩되어 있어 정확히 분기된다.
 */
@AutoConfiguration(before = DataSourceAutoConfiguration.class)
@ConditionalOnClass({AbstractRoutingDataSource.class, HikariDataSource.class})
@ConditionalOnProperty(prefix = "framework.datasource.routing", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(DataSourceRoutingProperties.class)
public class DataSourceRoutingAutoConfiguration {

    @Bean
    @Primary
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource routingDataSource(DataSourceRoutingProperties properties) {
        DataSource write = buildHikari(properties.getWrite(), "write");

        // 복제 노드 url 이 비어 있으면 READ 도 write 로 매핑(단일 DB 환경에서 라우팅을 켜도 무해).
        DataSource read = properties.getRead().hasUrl() ? buildHikari(properties.getRead(), "read") : write;

        Map<Object, Object> targets = new HashMap<>();
        targets.put(DataSourceType.WRITE, write);
        targets.put(DataSourceType.READ, read);

        RoutingDataSource routing = new RoutingDataSource();
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(write); // 키 결정 실패/트랜잭션 밖 → WRITE
        routing.afterPropertiesSet();

        // 핵심: lazy 프록시로 감싸야 readOnly 플래그 확정 이후에 물리 connection 을 잡아 정확히 라우팅된다.
        return new LazyConnectionDataSourceProxy(routing);
    }

    private static HikariDataSource buildHikari(DataSourceRoutingProperties.Node node, String label) {
        if (node == null || !node.hasUrl()) {
            throw new IllegalStateException(
                    "framework.datasource.routing." + label + ".url 이 필요합니다 (routing.enabled=true 인데 노드 url 이 비어 있음).");
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(node.getUrl());
        if (node.getUsername() != null) {
            config.setUsername(node.getUsername());
        }
        if (node.getPassword() != null) {
            config.setPassword(node.getPassword());
        }
        if (node.getDriverClassName() != null && !node.getDriverClassName().isBlank()) {
            config.setDriverClassName(node.getDriverClassName());
        }
        if (node.getMaximumPoolSize() != null) {
            config.setMaximumPoolSize(node.getMaximumPoolSize());
        }
        if (node.getMinimumIdle() != null) {
            config.setMinimumIdle(node.getMinimumIdle());
        }
        if (node.getConnectionTimeoutMs() != null) {
            config.setConnectionTimeout(node.getConnectionTimeoutMs());
        }
        if (node.getMaxLifetimeMs() != null) {
            config.setMaxLifetime(node.getMaxLifetimeMs());
        }
        config.setPoolName(node.getPoolName() != null ? node.getPoolName() : "routing-" + label);
        return new HikariDataSource(config);
    }
}
