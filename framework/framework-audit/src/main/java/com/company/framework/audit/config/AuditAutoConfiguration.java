package com.company.framework.audit.config;

import com.company.framework.audit.aspect.AuditTrailAspect;
import com.company.framework.audit.listener.LoginAuditListener;
import com.company.framework.audit.model.AuditEvent;
import com.company.framework.audit.query.AuditController;
import com.company.framework.audit.query.AuditQueryService;
import com.company.framework.audit.query.JdbcAuditQueryService;
import com.company.framework.audit.sink.AuditEventSink;
import com.company.framework.audit.sink.JdbcAuditEventSink;
import com.company.framework.audit.sink.LoggingAuditEventSink;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 감사 모듈 오토컨피그.
 * 1단(모듈): @ConditionalOnClass(AuditEvent) — 이 모듈을 의존성에 넣어야 활성.
 * 2단(기능): framework.audit.enabled=true (+ method-audit/login-audit 세부 토글).
 * 3단(구현): store.type=logging(기본·인프라0) | jdbc(영속·조회API). [kafka 는 framework-messaging 도입 시]
 *
 * 싱크 선택은 @Bean 정의 순서(위→아래)와 @ConditionalOnMissingBean 으로 결정:
 *   jdbc(type=jdbc) 우선 → 그 외 모든 경우 logging 으로 우아하게 축소(kafka 미구현 포함).
 */
@AutoConfiguration
@ConditionalOnClass(AuditEvent.class)
@ConditionalOnProperty(prefix = "framework.audit", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AuditProperties.class)
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnProperty(prefix = "framework.audit.store", name = "type", havingValue = "jdbc")
    @ConditionalOnMissingBean(AuditEventSink.class)
    public AuditEventSink jdbcAuditEventSink(JdbcTemplate jdbcTemplate) {
        return new JdbcAuditEventSink(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(AuditEventSink.class)
    public AuditEventSink loggingAuditEventSink() {
        return new LoggingAuditEventSink();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "framework.audit",
            name = "method-audit",
            havingValue = "true",
            matchIfMissing = true)
    public AuditTrailAspect auditTrailAspect(AuditEventSink sink) {
        return new AuditTrailAspect(sink);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "framework.audit",
            name = "login-audit",
            havingValue = "true",
            matchIfMissing = true)
    public LoginAuditListener loginAuditListener(AuditEventSink sink) {
        return new LoginAuditListener(sink);
    }

    // ===== 조회 API: 영속 싱크(jdbc)일 때만 의미 있음 =====
    @Bean
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnProperty(prefix = "framework.audit.store", name = "type", havingValue = "jdbc")
    @ConditionalOnMissingBean(AuditQueryService.class)
    public AuditQueryService auditQueryService(JdbcTemplate jdbcTemplate) {
        return new JdbcAuditQueryService(jdbcTemplate);
    }

    @Bean
    @ConditionalOnProperty(prefix = "framework.audit.store", name = "type", havingValue = "jdbc")
    @ConditionalOnMissingBean(AuditController.class)
    public AuditController auditController(AuditQueryService auditQueryService) {
        return new AuditController(auditQueryService);
    }
}
