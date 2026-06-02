package com.company.framework.datasource.multi;

import com.company.framework.datasource.config.MultiDataSourceProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

/**
 * 독립 다중 DB 의 인프라 빈(HikariDataSource / SqlSessionFactory)을 키 단위로 만드는 static 빌더.
 *
 * <p>핵심: 보조 DB 의 {@link SqlSessionFactory} 도 단일 DB 자동구성과 <b>동일하게 동작</b>하도록 만든다.
 * {@code framework-mybatis} 의 {@code commonMyBatisCustomizer}(snake_case 매핑 등)와 동일한 기본값을 직접 설정한 뒤,
 * 컨텍스트에 등록된 {@link ConfigurationCustomizer}·{@link Interceptor}(예: 감사필드 인터셉터) 빈을 그대로 적용한다.
 * 이렇게 하면 어느 DB 든 매핑/감사 동작이 일관된다.
 */
final class MultiDataSourceSupport {

    private static final ResourcePatternResolver RESOURCE_RESOLVER = new PathMatchingResourcePatternResolver();

    private MultiDataSourceSupport() {}

    /** 키별 HikariDataSource 생성. url 누락 시 교정 메시지와 함께 실패. */
    static HikariDataSource buildDataSource(MultiDataSourceProperties.Source node, String key) {
        if (node == null || !node.hasUrl()) {
            throw new IllegalStateException(
                    "framework.datasource.multi.sources." + key + ".url 이 비어 있습니다 (multi.enabled=true).");
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
        config.setPoolName(node.getPoolName() != null ? node.getPoolName() : "multi-" + key);
        return new HikariDataSource(config);
    }

    /**
     * 키별 SqlSessionFactory 생성. 단일 DB 기본값 + 컨텍스트의 커스터마이저/인터셉터를 적용한다.
     *
     * @param dataSource 이 DB 의 DataSource (= {@code <key>DataSource} 빈)
     * @param node 이 DB 의 MyBatis 설정(typeAliases/mapperLocations)
     * @param customizers 컨텍스트의 {@link ConfigurationCustomizer} 빈(framework-mybatis + 앱). lazy 수집.
     * @param interceptors 컨텍스트의 {@link Interceptor} 빈(예: 감사필드 인터셉터). lazy 수집.
     */
    static org.apache.ibatis.session.SqlSessionFactory buildSqlSessionFactory(
            DataSource dataSource,
            MultiDataSourceProperties.Source node,
            ObjectProvider<ConfigurationCustomizer> customizers,
            ObjectProvider<Interceptor> interceptors) {
        try {
            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);

            // framework-mybatis MyBatisConfig.commonMyBatisCustomizer 와 동일한 기본값(단일 DB 와 동작 일치).
            Configuration mybatis = new Configuration();
            mybatis.setMapUnderscoreToCamelCase(true);
            mybatis.setCallSettersOnNulls(true);
            mybatis.setDefaultFetchSize(100);
            mybatis.setDefaultExecutorType(ExecutorType.SIMPLE);
            mybatis.setUseGeneratedKeys(true);
            // 등록된 커스터마이저(framework-mybatis + 앱)를 같은 순서로 적용 → 기본값 위에 덮어쓰기 허용.
            customizers.orderedStream().forEach(customizer -> customizer.customize(mybatis));
            factoryBean.setConfiguration(mybatis);

            // 감사필드 등 Interceptor 빈을 모든 독립 DB 에 동일 적용.
            List<Interceptor> plugins = interceptors.orderedStream().toList();
            if (!plugins.isEmpty()) {
                factoryBean.setPlugins(plugins.toArray(new Interceptor[0]));
            }

            if (node.getTypeAliasesPackage() != null
                    && !node.getTypeAliasesPackage().isBlank()) {
                factoryBean.setTypeAliasesPackage(node.getTypeAliasesPackage());
            }
            if (node.getMapperLocations() != null && !node.getMapperLocations().isEmpty()) {
                List<Resource> resources = new ArrayList<>();
                for (String location : node.getMapperLocations()) {
                    if (location != null && !location.isBlank()) {
                        for (Resource resource : RESOURCE_RESOLVER.getResources(location)) {
                            resources.add(resource);
                        }
                    }
                }
                factoryBean.setMapperLocations(resources.toArray(new Resource[0]));
            }

            org.apache.ibatis.session.SqlSessionFactory built = factoryBean.getObject();
            if (built == null) {
                throw new IllegalStateException("SqlSessionFactoryBean.getObject() 가 null 을 반환했습니다.");
            }
            return built;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("SqlSessionFactory 생성 실패 (multi DB): " + ex.getMessage(), ex);
        }
    }
}
