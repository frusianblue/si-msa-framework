package com.company.framework.session.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.session.data.redis.RedisSessionRepository;

/**
 * 서버 세션 클러스터 저장소 자동 설정.
 *
 * <p><b>실제 Redis 기반 HttpSession 외부화는 Spring Boot 의 표준 {@code SessionAutoConfiguration} 이</b>
 * {@code spring-session-data-redis} 가 클래스패스에 있으면 자동으로 구성한다(이 모듈이 그 의존성을 가져온다).
 * 따라서 이 모듈은 재배선하지 않고, 표준 3단 토글(클래스패스 → {@code framework.session.enabled} → 구현선택)을 따르며
 * 프레임워크 차원의 오설정 가드와 프로퍼티만 더한다.
 *
 * <p>클래스패스 게이트는 {@link RedisSessionRepository}(spring-session-data-redis 동봉) 존재로 판단한다 —
 * RedisTokenStoreAutoConfiguration 이 {@code StringRedisTemplate} 로 게이트하는 것과 동일 패턴.
 */
@AutoConfiguration
@ConditionalOnClass(RedisSessionRepository.class)
@ConditionalOnProperty(prefix = "framework.session", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FrameworkSessionProperties.class)
public class FrameworkSessionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SessionStoreSafetyGuard sessionStoreSafetyGuard(FrameworkSessionProperties props, Environment env) {
        return new SessionStoreSafetyGuard(props, env);
    }
}
