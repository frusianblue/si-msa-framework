package com.company.framework.security.rbac.mybatis;

import com.company.framework.mybatis.support.CurrentUserProvider;
import com.company.framework.security.config.SecurityAutoConfiguration;
import com.company.framework.security.rbac.mapper.SecurityMapper;
import com.company.framework.security.rbac.spi.MenuProvider;
import com.company.framework.security.rbac.spi.ResourceMetadataProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * RBAC 영속의 MyBatis 어댑터 자동설정. 보안 코어의 RBAC 포트(SPI) 구현을 MyBatis 로 제공한다.
 *
 * <p><b>왜 코어가 아니라 어댑터인가</b> — {@code framework-security}(코어)는 인증({@code Authenticator}) 하나만
 * 강제해야 한다. RBAC(선택 기능) → MyBatis(특정 기술) → DataSource(인프라)의 줄결합을 끊기 위해 RBAC 영속을
 * 이 어댑터로 분리했다. 인증만 쓰는 서비스는 이 모듈을 의존하지 않으면 MyBatis/DataSource 가 전혀 필요 없다.
 *
 * <p><b>로딩/격리(PITFALLS 직결)</b>
 * <ul>
 *   <li>{@code before = SecurityAutoConfiguration.class} — 포트 빈을 코어보다 <b>먼저</b> 등록해, 코어의
 *       {@code @ConditionalOnBean(ResourceMetadataProvider)}(RBAC 빈 활성)·{@code @ConditionalOnMissingBean}(fail-fast)
 *       판정이 이 어댑터를 정확히 인식하게 한다.</li>
 *   <li>매퍼/Provider 빈은 {@code @ConditionalOnClass(SqlSessionFactory.class)} 가드된 nested {@code @Configuration}
 *       안에서만 만든다 — {@code @ConditionalOnMissingBean} 의 형제 빈 introspection 이 부재 의존 타입을 건드리지 않게 격리.</li>
 *   <li>{@code @MapperScan} 은 {@code annotationClass = Mapper.class} 필터와 함께 — 필터가 없으면 같은 패키지의
 *       SPI/도메인 인터페이스까지 스캔돼 {@code ConflictingBeanDefinitionException} 위험.</li>
 * </ul>
 */
@AutoConfiguration(before = SecurityAutoConfiguration.class)
public class SecurityRbacMyBatisAutoConfiguration {

    /**
     * MyBatis 매퍼 스캔 + RBAC 포트 구현. {@code SqlSessionFactory} 가 클래스패스에 있을 때만(= MyBatis 가용)
     * 활성화된다. 본 어댑터는 {@code framework-mybatis} 를 api 의존으로 끌어오므로 통상 항상 충족된다.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(SqlSessionFactory.class)
    @MapperScan(basePackages = "com.company.framework.security.rbac.mapper", annotationClass = Mapper.class)
    static class RbacMyBatisConfig {

        /** URL-역할 매핑 조회(동적 인가). 앱이 자체 구현을 주면 그쪽이 우선. */
        @Bean
        @ConditionalOnMissingBean(ResourceMetadataProvider.class)
        public ResourceMetadataProvider myBatisResourceMetadataProvider(SecurityMapper securityMapper) {
            return new MyBatisResourceMetadataProvider(securityMapper);
        }

        /** 역할별 메뉴 조회. 앱이 자체 구현을 주면 그쪽이 우선. */
        @Bean
        @ConditionalOnMissingBean(MenuProvider.class)
        public MenuProvider myBatisMenuProvider(SecurityMapper securityMapper) {
            return new MyBatisMenuProvider(securityMapper);
        }

        /**
         * 감사 브리지(코어→이전). 로그인 사용자 ID 를 created_by/updated_by 에 공급한다.
         * {@code @Primary} 로 framework-mybatis 의 기본(system) {@code CurrentUserProvider} 보다 우선(현행 동작과 동일).
         */
        @Bean
        @Primary
        public CurrentUserProvider securityContextCurrentUserProvider() {
            return new SecurityContextCurrentUserProvider();
        }
    }
}
