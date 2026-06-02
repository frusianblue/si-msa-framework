package com.company.framework.mybatis.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.mybatis.error.PersistenceExceptionHandler;
import com.company.framework.mybatis.interceptor.AuditFieldInterceptor;
import com.company.framework.mybatis.support.CurrentUserProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 공통 MyBatis 설정({@link MyBatisConfig}) 로딩 스모크.
 *
 * <p>{@code MyBatisConfig} 는 클래스레벨 토글이 없는 항상-적용 기반 설정이다(DataSource 불필요한 설정 빈만 제공).
 *
 * <ul>
 *   <li>기본 로딩 → 예외 변환기/공통 커스터마이저/기본 CurrentUserProvider/감사필드 인터셉터 등록.
 *   <li>{@code framework.mybatis.audit-injection=false} → 감사필드 인터셉터만 비활성.
 * </ul>
 */
class MyBatisConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(MyBatisConfig.class));

    @Test
    @DisplayName("기본 로딩 → 공통 설정 빈 + 감사필드 인터셉터(기본 ON) 등록")
    void registersCommonBeans() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PersistenceExceptionHandler.class);
            assertThat(context).hasSingleBean(ConfigurationCustomizer.class);
            assertThat(context).hasSingleBean(CurrentUserProvider.class);
            assertThat(context).hasSingleBean(AuditFieldInterceptor.class);
        });
    }

    @Test
    @DisplayName("audit-injection=false → 감사필드 인터셉터 비활성(나머지는 유지)")
    void disablesAuditInjection() {
        runner.withPropertyValues("framework.mybatis.audit-injection=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PersistenceExceptionHandler.class);
            assertThat(context).doesNotHaveBean(AuditFieldInterceptor.class);
        });
    }
}
