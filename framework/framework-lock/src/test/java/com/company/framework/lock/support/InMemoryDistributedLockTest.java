package com.company.framework.lock.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.lock.DistributedLock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 단일 JVM 락 의미 검증(순수 로직). 획득/경합/만료-재획득/소유자 해제/연장 및 {@link DistributedLock#runIfLocked} 기본 구현.
 */
class InMemoryDistributedLockTest {

    private final InMemoryDistributedLock lock = new InMemoryDistributedLock();

    @Test
    @DisplayName("최초 획득은 true, 다른 토큰의 동시 획득은 false(상호배제)")
    void acquiresOnceThenBlocks() {
        assertThat(lock.tryLock("k", "owner-A", Duration.ofMinutes(5))).isTrue();
        assertThat(lock.tryLock("k", "owner-B", Duration.ofMinutes(5))).isFalse();
    }

    @Test
    @DisplayName("만료된 락은 다른 토큰이 재획득할 수 있다")
    void expiredLockCanBeReacquired() {
        assertThat(lock.tryLock("k", "owner-A", Duration.ofMillis(-1))).isTrue(); // 즉시 만료
        assertThat(lock.tryLock("k", "owner-B", Duration.ofMinutes(5))).isTrue();
        assertThat(lock.tryLock("k", "owner-C", Duration.ofMinutes(5))).isFalse(); // 다시 살아있음
    }

    @Test
    @DisplayName("소유자 토큰만 해제할 수 있다(타 토큰 해제는 무시)")
    void onlyOwnerCanUnlock() {
        lock.tryLock("k", "owner-A", Duration.ofMinutes(5));
        lock.unlock("k", "intruder"); // 비소유 → 무시
        assertThat(lock.tryLock("k", "owner-B", Duration.ofMinutes(5))).isFalse(); // 여전히 A 보유
        lock.unlock("k", "owner-A"); // 소유자 해제
        assertThat(lock.tryLock("k", "owner-B", Duration.ofMinutes(5))).isTrue();
    }

    @Test
    @DisplayName("keepUntil 은 소유자일 때만 만료시각을 재설정한다")
    void keepUntilExtendsForOwnerOnly() {
        lock.tryLock("k", "owner-A", Duration.ofMillis(50));
        lock.keepUntil("k", "owner-A", Duration.ofMinutes(5)); // 연장
        assertThat(lock.tryLock("k", "owner-B", Duration.ofMinutes(5))).isFalse(); // 연장돼 살아있음
    }

    @Test
    @DisplayName("runIfLocked: 잡으면 실행 후 해제하고 true, 다음 호출도 다시 잡을 수 있다")
    void runIfLockedRunsAndReleases() {
        AtomicInteger runs = new AtomicInteger();
        assertThat(lock.runIfLocked("job", Duration.ofMinutes(5), runs::incrementAndGet))
                .isTrue();
        assertThat(runs.get()).isEqualTo(1);
        // 실행 후 해제됐으므로 재실행 가능.
        assertThat(lock.runIfLocked("job", Duration.ofMinutes(5), runs::incrementAndGet))
                .isTrue();
        assertThat(runs.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("runIfLocked: 이미 보유 중이면 실행하지 않고 false")
    void runIfLockedSkipsWhenHeld() {
        lock.tryLock("job", "owner-A", Duration.ofMinutes(5)); // 선점
        AtomicInteger runs = new AtomicInteger();
        assertThat(lock.runIfLocked("job", Duration.ofMinutes(5), runs::incrementAndGet))
                .isFalse();
        assertThat(runs.get()).isZero();
    }
}
