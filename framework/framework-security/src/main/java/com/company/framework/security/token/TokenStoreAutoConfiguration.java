package com.company.framework.security.token;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * token-store.type 에 따라 구현체를 선택. 기본 memory.
 * (redis 는 framework-redis 모듈이 RedisTokenStore 빈을 제공 → 여기 조건과 무관하게 우선 적용 가능)
 *
 * <p>jdbc 백엔드는 host 앱이 spring-jdbc(JdbcTemplate)를 제공할 때만 등록한다(보안-영속 결합 분리).
 * 인증만 쓰는 서비스는 spring-jdbc 가 없어 jdbc 설정이 백오프 → memory 기본값으로 DataSource 없이 동작.
 */
@AutoConfiguration
@EnableConfigurationProperties(TokenStoreProperties.class)
public class TokenStoreAutoConfiguration {

    // 기본값(memory): type 미설정이거나 memory 일 때. spring-jdbc 유무와 무관 → 톱레벨 유지.
    @Bean
    @ConditionalOnProperty(
            prefix = "framework.security.token-store",
            name = "type",
            havingValue = "memory",
            matchIfMissing = true)
    @ConditionalOnMissingBean(TokenStore.class)
    public TokenStore inMemoryTokenStore() {
        return new InMemoryTokenStore();
    }

    /**
     * jdbc 백엔드. spring-jdbc(JdbcTemplate)가 클래스패스에 있을 때만. nested + 클래스레벨 @ConditionalOnClass 로
     * 격리해 JdbcTemplate 부재 시 메서드 introspection 으로 깨지지 않게 한다.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    static class JdbcTokenStoreConfig {

        @Bean
        @ConditionalOnProperty(prefix = "framework.security.token-store", name = "type", havingValue = "jdbc")
        @ConditionalOnMissingBean(TokenStore.class)
        public TokenStore jdbcTokenStore(JdbcTemplate jdbcTemplate) {
            return new JdbcTokenStore(jdbcTemplate);
        }
    }
}
