package com.company.framework.secureweb.config;

import com.company.framework.secureweb.csrf.DoubleSubmitCsrfFilter;
import com.company.framework.secureweb.headers.SecurityHeadersFilter;
import com.company.framework.secureweb.injection.InjectionScreeningFilter;
import com.company.framework.secureweb.path.PathTraversalFilter;
import com.company.framework.secureweb.support.SecureWebResponder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 웹 보안 필터 모듈 오토컨피그.
 * 1단(모듈): @ConditionalOnClass(OncePerRequestFilter) + 서블릿 웹앱 — 의존성+웹스택 있어야 활성.
 * 2단(기능): framework.secure-web.enabled=true.
 * 3단(필터별 세부 토글):
 *   - headers.enabled        (기본 on)  보안 응답 헤더
 *   - path-traversal.enabled (기본 on)  경로 조작 차단
 *   - injection.enabled      (기본 off) 인젝션 스크리닝(오탐 가능)
 *   - csrf.enabled           (기본 off) CSRF 더블서브밋(쿠키 인증/폼용)
 *
 * <p>필터는 @Order(HIGHEST_PRECEDENCE + n)로 정렬되어 core 의 XSS 필터(+5)보다 앞에서 동작한다:
 * path(+1) → injection(+2) → csrf(+3) → headers(+4) → (core) xss(+5).
 */
@AutoConfiguration
@ConditionalOnClass(OncePerRequestFilter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "framework.secure-web", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SecureWebProperties.class)
public class SecureWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SecureWebResponder secureWebResponder(ObjectMapper objectMapper) {
        return new SecureWebResponder(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "framework.secure-web.headers",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    public SecurityHeadersFilter securityHeadersFilter(SecureWebProperties properties) {
        return new SecurityHeadersFilter(properties.getHeaders());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "framework.secure-web.path-traversal",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    public PathTraversalFilter pathTraversalFilter(SecureWebResponder responder) {
        return new PathTraversalFilter(responder);
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework.secure-web.injection", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public InjectionScreeningFilter injectionScreeningFilter(
            SecureWebResponder responder, SecureWebProperties properties) {
        return new InjectionScreeningFilter(responder, properties.getInjection());
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework.secure-web.csrf", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public DoubleSubmitCsrfFilter doubleSubmitCsrfFilter(
            SecureWebResponder responder, SecureWebProperties properties) {
        return new DoubleSubmitCsrfFilter(responder, properties.getCsrf());
    }
}
