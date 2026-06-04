package com.company.framework.file.sftp.pool;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 범용 바운디드 객체 풀(순수 JDK — SSHD/Spring 무의존). SFTP {@code ClientSession} 풀링에 쓰지만, 생성/검증/파기를
 * 함수 훅으로 주입받아 어떤 타입에도 쓸 수 있고 그래서 <b>JDK 단독으로 동작/경합/만료 로직을 단위검증</b>할 수 있다.
 *
 * <p>정책:
 * <ul>
 *   <li><b>cap</b>(maxTotal): 동시 보유(대여+유휴) 총량 상한. 초과 요청은 maxWait 까지 대기 후 timeout.
 *   <li><b>validate-on-borrow</b>: 유휴에서 꺼낸 객체가 {@code validator} 를 통과 못하면 파기하고 다시 시도(끊긴 연결 회피).
 *   <li><b>maxIdle</b>: 마지막 반납 후 이 시간을 넘긴 유휴 객체는 대여 시점에 만료·파기(유휴 누적 방지). 0 이하면 비활성.
 *   <li><b>maxLifetime</b>: 생성 후 이 시간을 넘긴 객체는 (유휴에서 꺼낼 때) 파기·재생성. 키 회전 전파에 필수
 *       — 옛 키로 인증된 장수 세션을 강제로 교체해 신규 세션이 현재 자격증명으로 재인증되게 한다. 0 이하면 비활성.
 * </ul>
 *
 * <p>IO 가 발생할 수 있는 훅({@code creator}/{@code validator}/{@code destroyer})은 <b>락 밖</b>에서 호출한다.
 */
public final class BoundedObjectPool<T> implements AutoCloseable {

    /** 대여 대기가 maxWait 를 초과했을 때 던지는 예외. 호출 측이 표준 예외로 변환한다. */
    public static final class PoolTimeoutException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public PoolTimeoutException(String message) {
            super(message);
        }
    }

    private record Entry<T>(T obj, long lastReturnedNanos) {}

    private final int maxTotal;
    private final long maxWaitNanos;
    private final long maxIdleNanos;
    private final long maxLifetimeNanos;
    private final Supplier<T> creator;
    private final Predicate<T> validator;
    private final Consumer<T> destroyer;
    private final LongSupplier clock;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();
    private final Deque<Entry<T>> idle = new ArrayDeque<>();
    private final Map<T, Long> createdAt = new IdentityHashMap<>();
    private int total = 0; // 대여 중 + 유휴
    private boolean closed = false;

    /**
     * @param maxTotal 동시 보유 총량 상한(>=1)
     * @param maxWaitNanos 대여 대기 상한(ns, <=0 이면 무한 대기)
     * @param maxIdleNanos 유휴 만료(ns, <=0 비활성)
     * @param maxLifetimeNanos 생성 후 수명(ns, <=0 비활성)
     * @param creator 새 객체 생성(락 밖 호출, 실패 시 예외 전파)
     * @param validator 대여 직전 유효성 검사(true 면 사용, false 면 파기·재시도)
     * @param destroyer 파기 훅(best-effort, 예외 무시 권장)
     * @param clock 단조 시계(보통 {@code System::nanoTime}; 테스트에선 가짜 시계 주입)
     */
    public BoundedObjectPool(
            int maxTotal,
            long maxWaitNanos,
            long maxIdleNanos,
            long maxLifetimeNanos,
            Supplier<T> creator,
            Predicate<T> validator,
            Consumer<T> destroyer,
            LongSupplier clock) {
        if (maxTotal < 1) {
            throw new IllegalArgumentException("maxTotal 은 1 이상이어야 합니다: " + maxTotal);
        }
        this.maxTotal = maxTotal;
        this.maxWaitNanos = maxWaitNanos;
        this.maxIdleNanos = maxIdleNanos;
        this.maxLifetimeNanos = maxLifetimeNanos;
        this.creator = creator;
        this.validator = validator;
        this.destroyer = destroyer;
        this.clock = clock;
    }

    /**
     * 사용 가능한 객체를 빌린다. 유휴가 있으면 검증 후 재사용, 없고 cap 미만이면 새로 생성, 둘 다 아니면 maxWait 까지 대기.
     *
     * @throws PoolTimeoutException 대기 시간 초과
     * @throws IllegalStateException 풀이 닫힘
     */
    public T borrow() {
        long deadline = (maxWaitNanos <= 0) ? Long.MAX_VALUE : clock.getAsLong() + maxWaitNanos;
        while (true) {
            T candidate = null;
            boolean create = false;
            List<T> toDestroy = new ArrayList<>();

            lock.lock();
            try {
                if (closed) {
                    throw new IllegalStateException("풀이 닫혀 있습니다.");
                }
                long now = clock.getAsLong();
                evictExpiredIdle(now, toDestroy);

                if (!idle.isEmpty()) {
                    candidate = idle.pollLast().obj(); // LIFO: 최근 반납분 우선(warm)
                } else if (total < maxTotal) {
                    total++; // 슬롯 선점(생성은 락 밖)
                    create = true;
                } else {
                    long remaining = deadline - now;
                    if (remaining <= 0) {
                        throw new PoolTimeoutException("풀에서 객체를 얻지 못했습니다(대기 시간 초과).");
                    }
                    try {
                        available.awaitNanos(remaining);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("풀 대기 중 인터럽트되었습니다.");
                    }
                    continue; // 깨어나면 재평가
                }
            } finally {
                lock.unlock();
            }

            destroyAll(toDestroy);

            if (create) {
                try {
                    T created = creator.get();
                    lock.lock();
                    try {
                        createdAt.put(created, clock.getAsLong());
                    } finally {
                        lock.unlock();
                    }
                    return created;
                } catch (RuntimeException e) {
                    releaseSlotOnFailure();
                    throw e;
                }
            }

            // 유휴에서 꺼낸 후보: 검증(락 밖). 통과 못하면 파기·재시도.
            if (candidate != null) {
                if (safeValidate(candidate)) {
                    return candidate;
                }
                discard(candidate);
                // 재시도(continue)
            }
        }
    }

    /** 사용을 마친 객체를 반납한다. 풀이 닫혔거나 검증 실패면 파기한다. */
    public void release(T obj) {
        if (obj == null) {
            return;
        }
        boolean destroy = false;
        lock.lock();
        try {
            if (closed) {
                destroy = true;
            } else {
                idle.addLast(new Entry<>(obj, clock.getAsLong()));
                available.signal();
            }
        } finally {
            lock.unlock();
        }
        if (destroy) {
            discard(obj);
        }
    }

    /** 손상된(대여 중) 객체를 회수 없이 폐기한다. 작업 중 오류로 세션이 깨졌을 때 사용. */
    public void invalidate(T obj) {
        if (obj != null) {
            discard(obj);
        }
    }

    /** 풀을 닫고 모든 유휴 객체를 파기한다. 이후 borrow 는 실패. 대여 중 객체는 반납 시 파기된다. */
    @Override
    public void close() {
        List<T> drain = new ArrayList<>();
        lock.lock();
        try {
            closed = true;
            for (Entry<T> e : idle) {
                drain.add(e.obj());
            }
            idle.clear();
            total -= drain.size();
            createdAt.keySet().removeAll(drain);
            available.signalAll();
        } finally {
            lock.unlock();
        }
        destroyAll(drain);
    }

    /** 현재 보유 총량(대여 중 + 유휴). 테스트/메트릭용. */
    public int size() {
        lock.lock();
        try {
            return total;
        } finally {
            lock.unlock();
        }
    }

    /** 현재 유휴 수. 테스트/메트릭용. */
    public int idleCount() {
        lock.lock();
        try {
            return idle.size();
        } finally {
            lock.unlock();
        }
    }

    // ---- 내부 ----

    /** 유휴 큐의 만료(maxIdle/maxLifetime) 항목을 제거하고 파기 목록에 모은다. 락 보유 상태에서 호출. */
    private void evictExpiredIdle(long now, List<T> toDestroy) {
        // 가장 오래된 유휴는 head. maxIdle 위반은 head 부터 연속 제거.
        while (!idle.isEmpty()) {
            Entry<T> head = idle.peekFirst();
            boolean idleExpired = maxIdleNanos > 0 && (now - head.lastReturnedNanos()) > maxIdleNanos;
            if (!idleExpired) {
                break;
            }
            idle.pollFirst();
            total--;
            createdAt.remove(head.obj());
            toDestroy.add(head.obj());
        }
        // maxLifetime 위반은 위치 무관 — 전체 스캔.
        if (maxLifetimeNanos > 0 && !idle.isEmpty()) {
            idle.removeIf(e -> {
                Long born = createdAt.get(e.obj());
                if (born != null && (now - born) > maxLifetimeNanos) {
                    total--;
                    createdAt.remove(e.obj());
                    toDestroy.add(e.obj());
                    return true;
                }
                return false;
            });
        }
    }

    /** 생성 실패 시 선점한 슬롯을 반납하고 대기자를 깨운다. */
    private void releaseSlotOnFailure() {
        lock.lock();
        try {
            total--;
            available.signal();
        } finally {
            lock.unlock();
        }
    }

    /** 객체를 폐기(파기 훅 호출) + 슬롯 반납 + 대기자 깨움. */
    private void discard(T obj) {
        lock.lock();
        try {
            total--;
            createdAt.remove(obj);
            available.signal();
        } finally {
            lock.unlock();
        }
        safeDestroy(obj);
    }

    private void destroyAll(List<T> objs) {
        for (T o : objs) {
            safeDestroy(o);
        }
    }

    private boolean safeValidate(T obj) {
        try {
            return validator == null || validator.test(obj);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void safeDestroy(T obj) {
        if (destroyer != null) {
            try {
                destroyer.accept(obj);
            } catch (RuntimeException ignore) {
                // best-effort
            }
        }
    }
}
