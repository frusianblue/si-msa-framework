package com.company.framework.secureweb.config;

import com.company.framework.secureweb.cors.CorsConfigSourceFactory;
import com.company.framework.secureweb.csrf.DoubleSubmitCsrfFilter;
import com.company.framework.secureweb.headers.SecurityHeadersFilter;
import com.company.framework.secureweb.injection.InjectionScreeningFilter;
import com.company.framework.secureweb.path.PathTraversalFilter;
import com.company.framework.secureweb.ratelimit.RateLimitFilter;
import com.company.framework.secureweb.support.SecureWebResponder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 웹 보안 필터 모듈 오토컨피그.
 * 1단(모듈): @ConditionalOnClass(OncePerRequestFilter) + 서블릿 웹앱 — 의존성+웹스택 있어야 활성.
 * 2단(기능): framework.secure-web.enabled=true.
 * 3단(필터별 세부 토글):
 *   - cors.enabled           (기본 off) CORS(직접 노출 서비스 보조 — 게이트웨이 뒤면 켜지 말 것)
 *   - rate-limit.enabled     (기본 off) 인스턴스 로컬 레이트리밋(전역은 게이트웨이 Redis)
 *   - headers.enabled        (기본 on)  보안 응답 헤더
 *   - path-traversal.enabled (기본 on)  경로 조작 차단
 *   - injection.enabled      (기본 off) 인젝션 스크리닝(오탐 가능)
 *   - csrf.enabled           (기본 off) CSRF 더블서브밋(쿠키 인증/폼용)
 *
 * <p>필터 순서(@Order): cors(HIGHEST) → rate-limit(HIGHEST+1) → path(+1) → injection(+2)
 * → csrf(+3) → headers(+4) → (core) xss(+5). cors 는 프리플라이트(OPTIONS)를 가장 먼저 처리해
 * 다운스트림 스크리닝 필터에 막히지 않게 한다. rate-limit 과 path 는 같은 앞단 계층(상호 의존 없음).
 */
@AutoConfiguration
@ConditionalOnClass(OncePerRequestFilter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "framework.secure-web", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SecureWebProperties.class)
public class SecureWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SecureWebResponder secureWebResponder() {
        return new SecureWebResponder();
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework.secure-web.cors", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(name = "secureWebCorsFilterRegistration")
    public FilterRegistrationBean<CorsFilter> secureWebCorsFilterRegistration(SecureWebProperties properties) {
        CorsFilter filter = new CorsFilter(CorsConfigSourceFactory.create(properties.getCors()));
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("secureWebCorsFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework.secure-web.rate-limit", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    public RateLimitFilter rateLimitFilter(SecureWebResponder responder, SecureWebProperties properties) {
        return new RateLimitFilter(responder, properties.getRateLimit());
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
    public DoubleSubmitCsrfFilter doubleSubmitCsrfFilter(SecureWebResponder responder, SecureWebProperties properties) {
        return new DoubleSubmitCsrfFilter(responder, properties.getCsrf());
    }
}
