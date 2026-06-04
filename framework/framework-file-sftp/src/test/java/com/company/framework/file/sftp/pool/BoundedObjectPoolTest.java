package com.company.framework.file.sftp.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 바운디드 풀 핵심 정책(생성/재사용/cap·timeout/validate-on-borrow/maxIdle/maxLifetime/invalidate/close/대기-깨움).
 * 시간 의존 동작은 가짜 시계({@code AtomicLong})로 결정적으로 검증한다.
 */
class BoundedObjectPoolTest {

    /** 가짜 연결. */
    static final class Conn {
        final int id;
        volatile boolean open = true;

        Conn(int id) {
            this.id = id;
        }
    }

    private static BoundedObjectPool<Conn> pool(
            int maxTotal,
            long maxWaitNanos,
            long maxIdleNanos,
            long maxLifetimeNanos,
            AtomicInteger seq,
            Set<Conn> destroyed,
            java.util.function.LongSupplier clock) {
        return new BoundedObjectPool<>(
                maxTotal,
                maxWaitNanos,
                maxIdleNanos,
                maxLifetimeNanos,
                () -> new Conn(seq.incrementAndGet()),
                c -> c.open,
                destroyed::add,
                clock);
    }

    @Test
    @DisplayName("생성/재사용(LIFO) — 반납분이 다음 대여에서 재사용된다")
    void createAndReuse() {
        AtomicInteger seq = new AtomicInteger();
        BoundedObjectPool<Conn> p = pool(4, 0, 0, 0, seq, ConcurrentHashMap.newKeySet(), System::nanoTime);
        Conn a = p.borrow();
        assertThat(p.size()).isEqualTo(1);
        assertThat(p.idleCount()).isZero();
        p.release(a);
        assertThat(p.idleCount()).isEqualTo(1);
        Conn b = p.borrow();
        assertThat(b).isSameAs(a);
        assertThat(p.size()).isEqualTo(1);
        p.close();
    }

    @Test
    @DisplayName("cap + timeout — 초과 대여는 시간초과로 실패하고 슬롯을 누수시키지 않는다")
    void capAndTimeoutNoLeak() {
        AtomicInteger seq = new AtomicInteger();
        BoundedObjectPool<Conn> p = pool(2, 30_000_000L, 0, 0, seq, ConcurrentHashMap.newKeySet(), System::nanoTime);
        Conn a = p.borrow();
        p.borrow();
        assertThat(p.size()).isEqualTo(2);
        assertThatThrownBy(p::borrow).isInstanceOf(BoundedObjectPool.PoolTimeoutException.class);
        p.release(a);
        assertThat(p.borrow()).isSameAs(a); // 누수 없음
        p.close();
    }

    @Test
    @DisplayName("validate-on-borrow — 죽은 유휴는 파기·재생성된다")
    void validateOnBorrow() {
        AtomicInteger seq = new AtomicInteger();
        Set<Conn> destroyed = ConcurrentHashMap.newKeySet();
        BoundedObjectPool<Conn> p = pool(4, 0, 0, 0, seq, destroyed, System::nanoTime);
        Conn a = p.borrow();
        a.open = false;
        p.release(a);
        Conn b = p.borrow();
        assertThat(b).isNotSameAs(a);
        assertThat(destroyed).contains(a);
        assertThat(p.size()).isEqualTo(1);
        p.close();
    }

    @Test
    @DisplayName("maxIdle — 마지막 반납 후 만료된 유휴는 대여 시 교체된다")
    void maxIdleEviction() {
        AtomicLong t = new AtomicLong(0);
        AtomicInteger seq = new AtomicInteger();
        Set<Conn> destroyed = ConcurrentHashMap.newKeySet();
        BoundedObjectPool<Conn> p = pool(4, 0, 1000, 0, seq, destroyed, t::get);
        Conn a = p.borrow();
        p.release(a);
        t.set(2000);
        Conn b = p.borrow();
        assertThat(b).isNotSameAs(a);
        assertThat(destroyed).contains(a);
        p.release(b);
        t.set(2500);
        assertThat(p.borrow()).isSameAs(b); // 만료 이내 재사용
        p.close();
    }

    @Test
    @DisplayName("maxLifetime — 생성 후 수명이 지나면 유효해도 교체된다(키 회전 전파)")
    void maxLifetimeEviction() {
        AtomicLong t = new AtomicLong(0);
        AtomicInteger seq = new AtomicInteger();
        Set<Conn> destroyed = ConcurrentHashMap.newKeySet();
        BoundedObjectPool<Conn> p = pool(4, 0, 0, 5000, seq, destroyed, t::get);
        Conn a = p.borrow();
        p.release(a);
        t.set(6000);
        Conn b = p.borrow();
        assertThat(b).isNotSameAs(a);
        assertThat(destroyed).contains(a);
        p.close();
    }

    @Test
    @DisplayName("invalidate + close — 폐기/종료 시 슬롯·자원이 정리된다")
    void invalidateAndClose() {
        AtomicInteger seq = new AtomicInteger();
        Set<Conn> destroyed = ConcurrentHashMap.newKeySet();
        BoundedObjectPool<Conn> p = pool(4, 0, 0, 0, seq, destroyed, System::nanoTime);
        Conn a = p.borrow();
        p.invalidate(a);
        assertThat(p.size()).isZero();
        assertThat(destroyed).contains(a);

        Conn b = p.borrow();
        Conn c = p.borrow();
        p.release(b);
        p.close();
        assertThat(destroyed).contains(b); // 유휴 드레인
        assertThat(p.size()).isEqualTo(1); // 대여 중 c
        p.release(c);
        assertThat(destroyed).contains(c); // 닫힌 뒤 반납분 파기
        assertThatThrownBy(p::borrow).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("대기 중 대여가 반납으로 깨어난다(blocking handoff)")
    void blockingHandoff() throws Exception {
        AtomicInteger seq = new AtomicInteger();
        BoundedObjectPool<Conn> p = pool(1, 2_000_000_000L, 0, 0, seq, ConcurrentHashMap.newKeySet(), System::nanoTime);
        Conn a = p.borrow();
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Conn> got = new AtomicReference<>();
        Thread th = new Thread(() -> {
            started.countDown();
            got.set(p.borrow());
        });
        th.start();
        started.await();
        Thread.sleep(100);
        p.release(a);
        th.join(2000);
        assertThat(got.get()).isSameAs(a);
        p.close();
    }

    @Test
    @DisplayName("maxTotal<1 은 거부")
    void rejectsBadMaxTotal() {
        assertThatThrownBy(() -> new BoundedObjectPool<>(0, 0, 0, 0, Object::new, o -> true, o -> {}, System::nanoTime))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
