package com.company.gateway.config;

import com.company.gateway.auth.GatewayAuthGlobalFilter;
import com.company.gateway.auth.GatewayJwksTokenVerifier;
import com.company.gateway.auth.GatewayTokenAuthenticator;
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
import org.springframework.web.client.RestClient;

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

    /**
     * 이중 발급기(AUTH_SERVER.md §4) — AS(OP) 발급 RS256 토큰 검증기. {@code authorization-server.enabled=true} 일 때만 등록.
     * issuer 가 비면 fail-fast(라우팅/검증 기준이 사라지면 위험). jwks-uri 생략 시 {issuer}/oauth2/jwks 로 구성.
     */
    @Bean
    @ConditionalOnProperty(prefix = "gateway.auth.authorization-server", name = "enabled", havingValue = "true")
    public GatewayJwksTokenVerifier gatewayJwksTokenVerifier(GatewayAuthProperties props) {
        GatewayAuthProperties.AuthorizationServer as = props.getAuthorizationServer();
        if (!StringUtils.hasText(as.getIssuer())) {
            throw new IllegalStateException(
                    "gateway.auth.authorization-server.enabled=true 이면 issuer 가 필요합니다(= auth-server.issuer 와 동일 값).");
        }
        String jwksUri = as.resolvedJwksUri();
        if (!StringUtils.hasText(jwksUri)) {
            throw new IllegalStateException("gateway.auth.authorization-server.jwks-uri 를 해석할 수 없습니다(issuer 확인).");
        }
        return new GatewayJwksTokenVerifier(
                RestClient.create(),
                as.getIssuer(),
                jwksUri,
                as.getRolesClaim(),
                as.getClockSkew(),
                as.getJwkCacheTtl(),
                as.getAudiences());
    }

    /**
     * 발급자 분기 라우터. AS 검증기가 있으면(옵트인 활성) iss=AS issuer 인 토큰을 JWKS 경로로, 그 외는 자체 JWT(HMAC) 경로로
     * 보낸다. AS 검증기가 없으면(기본) 항상 자체 JWT 경로 — 도입 전 동작과 동일.
     */
    @Bean
    public GatewayTokenAuthenticator gatewayTokenAuthenticator(
            GatewayTokenVerifier verifier, ObjectProvider<GatewayJwksTokenVerifier> jwksVerifierProvider) {
        return new GatewayTokenAuthenticator(verifier, jwksVerifierProvider.getIfAvailable());
    }

    @Bean
    public GatewayAuthGlobalFilter gatewayAuthGlobalFilter(
            GatewayTokenAuthenticator authenticator, GatewayTokenBlacklist blacklist, GatewayAuthProperties props) {
        return new GatewayAuthGlobalFilter(authenticator, blacklist, props);
    }
}
