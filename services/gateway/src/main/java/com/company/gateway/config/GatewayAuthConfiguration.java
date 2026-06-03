package com.company.gateway.config;

import com.company.gateway.auth.GatewayAuthGlobalFilter;
import com.company.gateway.auth.GatewayTokenVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Bean
    public GatewayAuthGlobalFilter gatewayAuthGlobalFilter(GatewayTokenVerifier verifier, GatewayAuthProperties props) {
        return new GatewayAuthGlobalFilter(verifier, props);
    }
}
