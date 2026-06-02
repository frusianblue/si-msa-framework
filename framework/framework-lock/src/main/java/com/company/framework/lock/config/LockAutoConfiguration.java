package com.company.framework.lock.config;

import com.company.framework.lock.DistributedLock;
import com.company.framework.lock.aspect.SchedulerLockAspect;
import com.company.framework.lock.support.InMemoryDistributedLock;
import com.company.framework.lock.support.JdbcDistributedLock;
import com.company.framework.lock.support.RedisDistributedLock;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 분산 락 오토컨피그(3단 토글).
 *
 * <ol>
 *   <li>모듈: {@code @ConditionalOnClass(DistributedLock)} — 이 모듈을 의존성에 넣어야 활성.
 *   <li>기능: {@code framework.lock.enabled=true}.
 *   <li>구현: {@code framework.lock.type=memory|redis|jdbc} ({@code @ConditionalOnMissingBean} 으로 프로젝트 override 허용).
 * </ol>
 *
 * <p>redis/jdbc 백엔드는 각각 {@link StringRedisTemplate}/{@link JdbcTemplate} 가 클래스패스+컨텍스트에 있어야 한다
 * (호스트 앱이 제공). {@code @SchedulerLock} 애스펙트는 {@code framework.lock.scheduler.enabled}(기본 true)로 등록되며,
 * AOP 는 코어가 가져오는 {@code spring-boot-starter-aspectj} 로 자동 활성된다.
 */
@AutoConfiguration
@ConditionalOnClass(DistributedLock.class)
@ConditionalOnProperty(prefix = "framework.lock", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LockProperties.class)
public class LockAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DistributedLock.class)
    @ConditionalOnProperty(prefix = "framework.lock", name = "type", havingValue = "memory", matchIfMissing = true)
    public DistributedLock inMemoryDistributedLock() {
        return new InMemoryDistributedLock();
    }

    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnMissingBean(DistributedLock.class)
    @ConditionalOnProperty(prefix = "framework.lock", name = "type", havingValue = "redis")
    public DistributedLock redisDistributedLock(StringRedisTemplate redisTemplate) {
        return new RedisDistributedLock(redisTemplate);
    }

    @Bean
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnMissingBean(DistributedLock.class)
    @ConditionalOnProperty(prefix = "framework.lock", name = "type", havingValue = "jdbc")
    public DistributedLock jdbcDistributedLock(JdbcTemplate jdbcTemplate) {
        return new JdbcDistributedLock(jdbcTemplate);
    }

    /**
     * {@code @SchedulerLock} 처리 애스펙트. {@code framework.lock.scheduler.enabled}(기본 true)일 때 등록.
     * {@link DistributedLock} 빈이 위에서 하나 결정된 뒤 그것을 주입받는다.
     */
    @Bean
    @ConditionalOnClass(Aspect.class)
    @ConditionalOnProperty(
            prefix = "framework.lock.scheduler",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    public SchedulerLockAspect schedulerLockAspect(DistributedLock distributedLock, LockProperties properties) {
        return new SchedulerLockAspect(distributedLock, properties);
    }
}
