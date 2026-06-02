package com.company.framework.datasource.multi;

import com.company.framework.datasource.config.MultiDataSourceProperties;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.jdbc.support.JdbcTransactionManager;

/**
 * {@code framework.datasource.multi.sources} 의 키마다 DB 인프라 빈 4종을 동적으로 등록한다.
 *
 * <p><b>왜 {@link ImportBeanDefinitionRegistrar} 인가</b>: 등록이 설정클래스 파싱 단계에서 일어나야
 * {@code @AutoConfiguration(before = DataSourceAutoConfiguration)} 의 순서가 적용되어, Boot 의
 * {@code @ConditionalOnMissingBean(DataSource)} 가 우리 {@code @Primary} DataSource 를 보고 백오프한다.
 * ({@code BeanDefinitionRegistryPostProcessor} 는 {@code ConfigurationClassPostProcessor} 보다 늦게 돌아 이 순서를 못 맞춘다.)
 *
 * <p>키 {@code <k>} 별 등록 빈:
 *
 * <ul>
 *   <li>{@code <k>DataSource} (HikariDataSource)
 *   <li>{@code <k>SqlSessionFactory}
 *   <li>{@code <k>SqlSessionTemplate}
 *   <li>{@code <k>TransactionManager} (JdbcTransactionManager)
 * </ul>
 *
 * primary 키의 4종은 모두 {@code @Primary} 로 표시된다(이름 없는 주입·Boot 백오프·Flyway 가 이 키로 해소).
 */
public class MultiDataSourceRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware, BeanFactoryAware {

    private Environment environment;
    private BeanFactory beanFactory;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        MultiDataSourceProperties properties = bindProperties();
        if (!properties.isEnabled()) {
            return; // 어노테이션 조건과 동일하지만 방어적으로 한 번 더.
        }
        // routing 과 동시 활성 금지(둘 다 @Primary DataSource 를 만들어 충돌).
        MultiDataSourcePlan.assertNotConflictingWithRouting(
                environment.getProperty("framework.datasource.routing.enabled", Boolean.class, Boolean.FALSE));

        Map<String, MultiDataSourceProperties.Source> sources = properties.getSources();
        String primaryKey = MultiDataSourcePlan.resolvePrimaryKey(sources.keySet(), properties.getPrimary());

        sources.forEach((key, node) -> {
            boolean primary = key.equals(primaryKey);
            registerDataSource(registry, key, node, primary);
            registerSqlSessionFactory(registry, key, node, primary);
            registerSqlSessionTemplate(registry, key, primary);
            registerTransactionManager(registry, key, primary);
        });
    }

    private MultiDataSourceProperties bindProperties() {
        return org.springframework.boot.context.properties.bind.Binder.get(environment)
                .bind("framework.datasource.multi", MultiDataSourceProperties.class)
                .orElseGet(MultiDataSourceProperties::new);
    }

    private void registerDataSource(
            BeanDefinitionRegistry registry, String key, MultiDataSourceProperties.Source node, boolean primary) {
        AbstractBeanDefinition definition = BeanDefinitionBuilder.genericBeanDefinition(
                        DataSource.class, () -> MultiDataSourceSupport.buildDataSource(node, key))
                .getBeanDefinition();
        definition.setDestroyMethodName("close"); // HikariDataSource 풀 정리.
        markPrimary(definition, primary);
        registry.registerBeanDefinition(MultiDataSourcePlan.dataSourceBeanName(key), definition);
    }

    private void registerSqlSessionFactory(
            BeanDefinitionRegistry registry, String key, MultiDataSourceProperties.Source node, boolean primary) {
        String dataSourceBean = MultiDataSourcePlan.dataSourceBeanName(key);
        AbstractBeanDefinition definition = BeanDefinitionBuilder.genericBeanDefinition(SqlSessionFactory.class, () -> {
                    ConfigurableListableBeanFactory clbf = configurableBeanFactory();
                    DataSource dataSource = clbf.getBean(dataSourceBean, DataSource.class);
                    return MultiDataSourceSupport.buildSqlSessionFactory(
                            dataSource,
                            node,
                            clbf.getBeanProvider(ConfigurationCustomizer.class),
                            clbf.getBeanProvider(Interceptor.class));
                })
                .getBeanDefinition();
        definition.setDependsOn(dataSourceBean);
        markPrimary(definition, primary);
        registry.registerBeanDefinition(MultiDataSourcePlan.sqlSessionFactoryBeanName(key), definition);
    }

    private void registerSqlSessionTemplate(BeanDefinitionRegistry registry, String key, boolean primary) {
        String factoryBean = MultiDataSourcePlan.sqlSessionFactoryBeanName(key);
        AbstractBeanDefinition definition = BeanDefinitionBuilder.genericBeanDefinition(
                        SqlSessionTemplate.class, () -> {
                            ConfigurableListableBeanFactory clbf = configurableBeanFactory();
                            return new SqlSessionTemplate(clbf.getBean(factoryBean, SqlSessionFactory.class));
                        })
                .getBeanDefinition();
        definition.setDependsOn(factoryBean);
        markPrimary(definition, primary);
        registry.registerBeanDefinition(MultiDataSourcePlan.sqlSessionTemplateBeanName(key), definition);
    }

    private void registerTransactionManager(BeanDefinitionRegistry registry, String key, boolean primary) {
        String dataSourceBean = MultiDataSourcePlan.dataSourceBeanName(key);
        AbstractBeanDefinition definition = BeanDefinitionBuilder.genericBeanDefinition(
                        JdbcTransactionManager.class, () -> {
                            ConfigurableListableBeanFactory clbf = configurableBeanFactory();
                            return new JdbcTransactionManager(clbf.getBean(dataSourceBean, DataSource.class));
                        })
                .getBeanDefinition();
        definition.setDependsOn(dataSourceBean);
        markPrimary(definition, primary);
        registry.registerBeanDefinition(MultiDataSourcePlan.transactionManagerBeanName(key), definition);
    }

    private static void markPrimary(BeanDefinition definition, boolean primary) {
        if (primary) {
            definition.setPrimary(true);
        }
    }

    private ConfigurableListableBeanFactory configurableBeanFactory() {
        if (beanFactory instanceof ConfigurableListableBeanFactory clbf) {
            return clbf;
        }
        throw new IllegalStateException("예상치 못한 BeanFactory 타입: "
                + (beanFactory == null ? "null" : beanFactory.getClass().getName()));
    }
}
