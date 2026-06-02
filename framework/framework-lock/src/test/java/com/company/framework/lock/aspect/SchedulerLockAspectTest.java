package com.company.framework.lock.aspect;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.lock.DistributedLock;
import com.company.framework.lock.annotation.SchedulerLock;
import com.company.framework.lock.config.LockProperties;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * {@link SchedulerLockAspect} 를 실제 Spring AOP 프록시 경로로 검증한다(애너테이션 어드바이스가 실제로 가로채는지까지).
 * 제어 가능한 가짜 {@link DistributedLock} 으로 획득 성공/실패와 atLeastFor 분기를 확인한다.
 */
class SchedulerLockAspectTest {

    @Test
    @DisplayName("락 획득 성공 → 메서드 실행 + 해제(unlock) 1회, keepUntil 없음")
    void runsAndUnlocksWhenAcquired() {
        FakeLock fake = new FakeLock();
        fake.acquireResult = true;
        runnerWith(fake).run(context -> {
            Task task = context.getBean(Task.class);
            task.normal();
            assertThat(task.ran.get()).isEqualTo(1);
            assertThat(fake.unlockCalls.get()).isEqualTo(1);
            assertThat(fake.keepUntilCalls.get()).isZero();
        });
    }

    @Test
    @DisplayName("락 획득 실패(타 인스턴스 보유) → 메서드 미실행 + 해제 호출 없음")
    void skipsWhenNotAcquired() {
        FakeLock fake = new FakeLock();
        fake.acquireResult = false;
        runnerWith(fake).run(context -> {
            Task task = context.getBean(Task.class);
            task.normal();
            assertThat(task.ran.get()).isZero();
            assertThat(fake.unlockCalls.get()).isZero();
        });
    }

    @Test
    @DisplayName("atLeastFor 보다 빨리 끝나면 즉시 해제하지 않고 keepUntil 로 유지")
    void keepsUntilAtLeastForWhenFinishedEarly() {
        FakeLock fake = new FakeLock();
        fake.acquireResult = true;
        runnerWith(fake).run(context -> {
            Task task = context.getBean(Task.class);
            task.quick(); // atLeastFor=10s, 즉시 종료 → keepUntil
            assertThat(task.ran.get()).isEqualTo(1);
            assertThat(fake.keepUntilCalls.get()).isEqualTo(1);
            assertThat(fake.unlockCalls.get()).isZero();
        });
    }

    private ApplicationContextRunner runnerWith(FakeLock fake) {
        return new ApplicationContextRunner()
                .withBean(DistributedLock.class, () -> fake)
                .withBean(LockProperties.class, LockProperties::new)
                .withConfiguration(UserConfigurations.of(AspectConfig.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class AspectConfig {
        @Bean
        SchedulerLockAspect schedulerLockAspect(DistributedLock lock, LockProperties props) {
            return new SchedulerLockAspect(lock, props);
        }

        @Bean
        Task task() {
            return new Task();
        }
    }

    /** @SchedulerLock 대상 빈(CGLIB 프록시). */
    static class Task {
        final AtomicInteger ran = new AtomicInteger();

        @SchedulerLock(name = "job", atMostFor = "5m")
        public void normal() {
            ran.incrementAndGet();
        }

        @SchedulerLock(name = "quick", atMostFor = "5m", atLeastFor = "10s")
        public void quick() {
            ran.incrementAndGet();
        }
    }

    /** 제어 가능한 가짜 락. 획득 결과를 강제하고 해제/연장 호출을 센다. */
    static class FakeLock implements DistributedLock {
        volatile boolean acquireResult = true;
        final AtomicInteger unlockCalls = new AtomicInteger();
        final AtomicInteger keepUntilCalls = new AtomicInteger();

        @Override
        public boolean tryLock(String key, String token, Duration ttl) {
            return acquireResult;
        }

        @Override
        public void unlock(String key, String token) {
            unlockCalls.incrementAndGet();
        }

        @Override
        public void keepUntil(String key, String token, Duration ttl) {
            keepUntilCalls.incrementAndGet();
        }
    }
}
