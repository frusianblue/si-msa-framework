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
import org.springframework.context.annotation.Configuration;
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
 * <p><b>⚠️ optional 백엔드는 반드시 중첩 설정 클래스로 격리한다.</b> redis/jdbc 백엔드는 각각 {@link StringRedisTemplate}/
 * {@link JdbcTemplate} 를 메서드 시그니처로 참조하는데, 이 메서드를 <b>최상위</b> 설정 클래스에 두면 — 그 클래스의 다른 빈
 * (예: {@link #schedulerLockAspect})이 타입 미지정 {@code @ConditionalOnMissingBean} 으로 타입 추론을 트리거할 때 —
 * 스프링이 {@code Class.getDeclaredMethods()} 로 <b>모든</b> 메서드의 파라미터 타입을 한꺼번에 로드하려다, 런타임
 * 클래스패스에 없는 optional 타입(예: redis 미사용 서비스의 {@code StringRedisTemplate})에서
 * {@code NoClassDefFoundError → 클래스 introspect 실패 → 기동 불가}로 떨어진다. 메서드 레벨
 * {@code @ConditionalOnClass} 는 이 introspect 단계를 못 막는다(형제 빈 추론이 먼저 클래스 통째를 로드). 따라서 optional
 * 의존을 시그니처로 갖는 빈은 {@code @ConditionalOnClass} 로 가드된 <b>중첩 {@code @Configuration}</b> 으로 분리해야
 * 한다(클래스 부재 시 중첩 클래스 통째 미로딩 → 최상위 클래스는 optional 타입을 일절 참조하지 않음).
 *
 * <p>{@code @SchedulerLock} 애스펙트는 {@code framework.lock.scheduler.enabled}(기본 true)로 등록되며, AOP 는 코어가
 * 가져오는 {@code spring-boot-starter-aspectj} 로 자동 활성된다.
 */
@AutoConfiguration
@ConditionalOnClass(DistributedLock.class)
@ConditionalOnProperty(prefix = "framework.lock", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(LockProperties.class)
public class LockAutoConfiguration {

    /** 기본 백엔드(외부 의존성 0). type 미지정 또는 memory 일 때. */
    @Bean
    @ConditionalOnMissingBean(DistributedLock.class)
    @ConditionalOnProperty(prefix = "framework.lock", name = "type", havingValue = "memory", matchIfMissing = true)
    public DistributedLock inMemoryDistributedLock() {
        return new InMemoryDistributedLock();
    }

    /**
     * {@code @SchedulerLock} 처리 애스펙트. {@code framework.lock.scheduler.enabled}(기본 true)일 때 등록.
     * {@link DistributedLock} 빈이 하나 결정된 뒤 그것을 주입받는다.
     *
     * <p>{@code @ConditionalOnMissingBean(SchedulerLockAspect.class)} 로 <b>타입을 명시</b>한다 — 타입 미지정이면 스프링이
     * 이 설정 클래스를 리플렉션 introspect 해 반환 타입을 추론하는데, 그 과정이 위 ⚠️ 의 클래스 로딩 폭발을 트리거한다.
     */
    @Bean
    @ConditionalOnClass(Aspect.class)
    @ConditionalOnProperty(
            prefix = "framework.lock.scheduler",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(SchedulerLockAspect.class)
    public SchedulerLockAspect schedulerLockAspect(DistributedLock distributedLock, LockProperties properties) {
        return new SchedulerLockAspect(distributedLock, properties);
    }

    /**
     * redis 백엔드 — spring-data-redis({@link StringRedisTemplate})가 클래스패스에 있을 때만 로딩(없으면 이 중첩 클래스
     * 통째 미로딩 → 최상위 {@link LockAutoConfiguration} introspect 안전). 호스트 앱이 {@code StringRedisTemplate} 빈 제공.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    static class RedisLockConfiguration {

        @Bean
        @ConditionalOnMissingBean(DistributedLock.class)
        @ConditionalOnProperty(prefix = "framework.lock", name = "type", havingValue = "redis")
        public DistributedLock redisDistributedLock(StringRedisTemplate redisTemplate) {
            return new RedisDistributedLock(redisTemplate);
        }
    }

    /**
     * jdbc 백엔드 — spring-jdbc({@link JdbcTemplate})가 클래스패스에 있을 때만 로딩. 호스트 앱이 {@code DataSource} 를
     * 두면 부트가 {@code JdbcTemplate} 빈을 오토컨피그한다(락 테이블 DDL 은 호스트가 마이그레이션).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcTemplate.class)
    static class JdbcLockConfiguration {

        @Bean
        @ConditionalOnMissingBean(DistributedLock.class)
        @ConditionalOnProperty(prefix = "framework.lock", name = "type", havingValue = "jdbc")
        public DistributedLock jdbcDistributedLock(JdbcTemplate jdbcTemplate) {
            return new JdbcDistributedLock(jdbcTemplate);
        }
    }
}
