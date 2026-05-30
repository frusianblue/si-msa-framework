package com.company.framework.core.config;

import com.company.framework.core.aspect.AuditLogAspect;
import com.company.framework.core.aspect.ExecutionTimeAspect;
import com.company.framework.core.error.GlobalExceptionHandler;
import com.company.framework.core.logging.HttpLoggingFilter;
import com.company.framework.core.trace.MdcTraceFilter;
import com.company.framework.core.web.XssRequestFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 공통 코어 자동 설정. 각 기능은 framework.core.* 프로퍼티로 개별 on/off 가능.
 * (미설정 시 기본 활성화: matchIfMissing = true)
 */
@AutoConfiguration
@EnableConfigurationProperties(FrameworkCoreProperties.class)
@Import({JacksonConfig.class, AsyncConfig.class})
public class FrameworkCoreAutoConfiguration {

    // 표준 응답/예외 처리는 항상 활성 (프레임워크의 근간)
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework.core", name = "trace", havingValue = "true", matchIfMissing = true)
    public MdcTraceFilter mdcTraceFilter() {
        return new MdcTraceFilter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework.core", name = "xss", havingValue = "true", matchIfMissing = true)
    public XssRequestFilter xssRequestFilter() {
        return new XssRequestFilter();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "framework.core",
            name = "http-logging",
            havingValue = "true",
            matchIfMissing = true)
    public HttpLoggingFilter httpLoggingFilter() {
        return new HttpLoggingFilter();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "framework.core",
            name = "execution-time-aspect",
            havingValue = "true",
            matchIfMissing = true)
    public ExecutionTimeAspect executionTimeAspect() {
        return new ExecutionTimeAspect();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "framework.core",
            name = "audit-aspect",
            havingValue = "true",
            matchIfMissing = true)
    public AuditLogAspect auditLogAspect() {
        return new AuditLogAspect();
    }
}
