package com.company.gateway.config;

import com.company.gateway.auth.GatewayAuthGlobalFilter;
import com.company.gateway.auth.GatewayTokenBlacklist;
import com.company.gateway.auth.GatewayTokenVerifier;
import com.company.gateway.auth.NoOpGatewayTokenBlacklist;
import com.company.gateway.auth.RedisGatewayTokenBlacklist;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * 엣지 인증 배선. {@code gateway.auth.enabled=true} 일 때만 검증기/필터를 등록한다(기본 off = 현행 무인증 통과).
 */
@Configuration
@EnableConfigurationProperties(GatewayAuthProperties.class)
@ConditionalOnProperty(prefix = "gateway.auth", name = "enabled", havingValue = "true")
public class GatewayAuthConfiguration {

    @Bean
    public GatewayTokenVerifier gatewayTokenVerifier(GatewayAuthProperties props) {
        if (!StringUtils.hasText(props.getJwtSecret())) {
            throw new IllegalStateException(
                    "gateway.auth.enabled=true 이면 gateway.auth.jwt-secret 이 필요합니다(framework.security.jwt.secret 과 동일 값).");
        }
        return new GatewayTokenVerifier(props.getJwtSecret(), props.getTokenType());
    }

    /**
     * 중앙 로그아웃 블랙리스트 조회기. {@code blacklist-check.enabled=true} 면 reactive Redis 를 쓰고,
     * 없으면 fail-fast. 비활성(기본)이면 NoOp(항상 통과 — 기존 서명/만료 검증만).
     */
    @Bean
    public GatewayTokenBlacklist gatewayTokenBlacklist(
            GatewayAuthProperties props, ObjectProvider<ReactiveStringRedisTemplate> redisProvider) {
        if (!props.getBlacklistCheck().isEnabled()) {
            return new NoOpGatewayTokenBlacklist();
        }
        ReactiveStringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            throw new IllegalStateException("gateway.auth.blacklist-check.enabled=true 이면 reactive Redis"
                    + "(ReactiveStringRedisTemplate)가 필요합니다. spring.data.redis 설정을 확인하세요"
                    + "(게이트웨이는 spring-boot-starter-data-redis-reactive 를 이미 보유).");
        }
        return new RedisGatewayTokenBlacklist(redis);
    }

    @Bean
    public GatewayAuthGlobalFilter gatewayAuthGlobalFilter(
            GatewayTokenVerifier verifier, GatewayTokenBlacklist blacklist, GatewayAuthProperties props) {
        return new GatewayAuthGlobalFilter(verifier, blacklist, props);
    }
}
