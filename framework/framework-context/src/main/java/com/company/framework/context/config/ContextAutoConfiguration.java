package com.company.framework.context.config;

import com.company.framework.context.async.ContextTaskDecorator;
import com.company.framework.context.client.ContextPropagationInterceptor;
import com.company.framework.context.web.ContextBindingFilter;
import com.company.framework.context.web.ContextResolver;
import com.company.framework.context.web.HeaderContextResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 요청 컨텍스트 자동설정. {@code framework.context.enabled=true} 이고 서블릿 웹 앱일 때만 활성(선택형 모듈 컨벤션).
 *
 * <p>노출 빈
 *
 * <ul>
 *   <li>{@link ContextResolver} — 기본 {@link HeaderContextResolver}(헤더 기반). 앱이 직접 정의하면 그쪽 우선.
 *   <li>{@link ContextBindingFilter} — 요청마다 컨텍스트 바인딩/정리(+MDC).
 *   <li>{@link ContextTaskDecorator} — {@code @Async}/풀 전파용(실행기에 직접 연결해 사용).
 *   <li>{@link ContextPropagationInterceptor} — 아웃바운드 헤더 전파({@code propagate-downstream=true} 일 때).
 * </ul>
 *
 * <p>core 외 새 의존성 없음. servlet/web 은 호스트가 제공(compileOnly).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "framework.context", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ContextProperties.class)
public class ContextAutoConfiguration {

    /** 기본 해소 전략: 헤더 기반. 앱이 ContextResolver 빈을 직접 정의하면 그쪽이 우선. */
    @Bean
    @ConditionalOnMissingBean
    public ContextResolver contextResolver(ContextProperties props) {
        return new HeaderContextResolver(props.getTenantHeader(), props.getUserHeader());
    }

    /** 요청마다 컨텍스트를 바인딩/정리하는 필터. */
    @Bean
    @ConditionalOnMissingBean
    public ContextBindingFilter contextBindingFilter(ContextResolver resolver, ContextProperties props) {
        return new ContextBindingFilter(resolver, props);
    }

    /** 비동기/풀 전파용 데코레이터(실행기에 직접 연결해 사용). */
    @Bean
    @ConditionalOnMissingBean
    public ContextTaskDecorator contextTaskDecorator() {
        return new ContextTaskDecorator();
    }

    /** 아웃바운드 호출에 컨텍스트 헤더를 전파하는 인터셉터(propagate-downstream=true 기본). */
    @Bean
    @ConditionalOnClass(name = "org.springframework.http.client.ClientHttpRequestInterceptor")
    @ConditionalOnProperty(
            prefix = "framework.context",
            name = "propagate-downstream",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    public ContextPropagationInterceptor contextPropagationInterceptor(ContextProperties props) {
        return new ContextPropagationInterceptor(props.getTenantHeader(), props.getUserHeader());
    }
}
