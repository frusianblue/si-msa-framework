package com.company.framework.mybatis.config;

import com.company.framework.mybatis.interceptor.AuditFieldInterceptor;
import com.company.framework.mybatis.error.PersistenceExceptionHandler;
import com.company.framework.mybatis.support.CurrentUserProvider;
import org.apache.ibatis.session.ExecutorType;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.Optional;

/**
 * 전사 공통 MyBatis 기본 설정 + 감사필드 자동주입(토글).
 *  - snake_case <-> camelCase 자동 매핑, null 컬럼 매핑, 기본 fetchSize
 *  - framework.mybatis.audit-injection=true 면 INSERT/UPDATE 시 감사필드 자동 채움
 */
@AutoConfiguration
public class MyBatisConfig {

    @Bean
    public PersistenceExceptionHandler persistenceExceptionHandler() {
        return new PersistenceExceptionHandler();
    }

    @Bean
    public ConfigurationCustomizer commonMyBatisCustomizer() {
        return configuration -> {
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setCallSettersOnNulls(true);
            configuration.setDefaultFetchSize(100);
            configuration.setDefaultExecutorType(ExecutorType.SIMPLE);
            configuration.setUseGeneratedKeys(true);
        };
    }

    /** 기본 사용자 제공자: 항상 "system" (framework-security 가 있으면 그쪽 구현이 우선) */
    @Bean
    @ConditionalOnMissingBean(CurrentUserProvider.class)
    public CurrentUserProvider defaultCurrentUserProvider() {
        return Optional::empty;
    }

    /** 감사필드 자동주입 인터셉터 (mybatis-spring 이 Interceptor 빈을 자동 등록) */
    @Bean
    @ConditionalOnProperty(prefix = "framework.mybatis", name = "audit-injection", havingValue = "true", matchIfMissing = true)
    public AuditFieldInterceptor auditFieldInterceptor(CurrentUserProvider currentUserProvider) {
        return new AuditFieldInterceptor(currentUserProvider);
    }
}
