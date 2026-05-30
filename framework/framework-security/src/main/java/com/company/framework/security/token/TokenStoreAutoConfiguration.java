package com.company.framework.security.token;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * token-store.type 에 따라 구현체를 선택. 기본 memory.
 * (redis 는 framework-redis 모듈이 RedisTokenStore 빈을 제공 → 여기 조건과 무관하게 우선 적용 가능)
 */
@AutoConfiguration
@EnableConfigurationProperties(TokenStoreProperties.class)
public class TokenStoreAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "framework.security.token-store", name = "type", havingValue = "jdbc")
    @ConditionalOnMissingBean(TokenStore.class)
    public TokenStore jdbcTokenStore(JdbcTemplate jdbcTemplate) {
        return new JdbcTokenStore(jdbcTemplate);
    }

    // 기본값(memory): type 미설정이거나 memory 일 때
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
}
