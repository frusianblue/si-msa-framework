package com.company.framework.client.config;

import com.company.framework.client.interceptor.CircuitBreakerInterceptor;
import com.company.framework.client.interceptor.IntegrationLoggingInterceptor;
import com.company.framework.client.interceptor.RetryInterceptor;
import com.company.framework.client.interceptor.TracePropagationInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 외부 API 표준 호출 오토컨피그.
 * 1단(모듈): @ConditionalOnClass(RestClient) — web + 이 모듈 의존 시 활성.
 * 2단(기능): framework.client.enabled=true.
 * 제공: 'frameworkRestClientBuilder' (RestClient.Builder) — 앱은 .baseUrl(...) 만 붙여 사용.
 *   인터셉터 순서(바깥→안): Trace → CircuitBreaker → Retry → Logging.
 * 3단(override): @ConditionalOnMissingBean(name="frameworkRestClientBuilder").
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@ConditionalOnProperty(prefix = "framework.client", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ClientProperties.class)
public class ClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "frameworkRestClientBuilder")
    public RestClient.Builder frameworkRestClientBuilder(ClientProperties props) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(props.getConnectTimeout())
                .withReadTimeout(props.getReadTimeout());
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.detect().build(settings);

        RestClient.Builder builder = RestClient.builder().requestFactory(factory);

        if (props.getTrace().isEnabled()) {
            builder.requestInterceptor(new TracePropagationInterceptor());
        }
        if (props.getCircuitBreaker().isEnabled()) {
            builder.requestInterceptor(new CircuitBreakerInterceptor(props.getCircuitBreaker()));
        }
        if (props.getRetry().isEnabled()) {
            builder.requestInterceptor(new RetryInterceptor(props.getRetry()));
        }
        if (props.getLogging().isEnabled()) {
            builder.requestInterceptor(new IntegrationLoggingInterceptor(props.getLogging()));
        }
        return builder;
    }
}
