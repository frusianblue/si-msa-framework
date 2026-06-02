package com.company.framework.lock;

import java.time.Duration;
import java.util.UUID;

/**
 * 분산 락(추상). k8s 다중 파드/다중 인스턴스에서 "한 번에 한 주체"를 강제한다. 구현은 {@code framework.lock.type}
 * 으로 선택(memory/redis/jdbc). 프로젝트가 동일 타입 빈을 등록하면 {@code @ConditionalOnMissingBean} 으로 그쪽이 우선.
 *
 * <p><b>리스(lease) 기반</b>: 모든 락은 TTL 을 가지며, 보유 인스턴스가 죽어도 TTL 후 자동 해제되어 영구 교착을 막는다.
 * 따라서 보호 구간의 예상 실행시간보다 TTL 을 충분히 크게 잡아야 한다(짧으면 실행 도중 만료 → 타 인스턴스 동시 진입 가능).
 *
 * <p><b>소유자 토큰</b>: {@link #unlock}/{@link #keepUntil} 은 {@code token} 이 현재 소유자와 일치할 때만 동작한다.
 * 이는 "내 락이 TTL 로 만료된 뒤 다른 인스턴스가 재획득한 락을, 뒤늦게 끝난 내가 잘못 해제/연장"하는 사고를 막는다
 * (Redis 는 Lua CAS, JDBC 는 {@code WHERE ... AND lock_owner=?} 로 원자 보장).
 */
public interface DistributedLock {

    /**
     * 락 획득 시도(비차단). 획득하면 {@code true}(이번 인스턴스가 소유), 이미 살아있는 락이 있으면 {@code false}.
     *
     * @param key 락 이름(업무 단위로 유니크하게)
     * @param token 소유자 식별자. 보통 인스턴스마다 새 {@link UUID}. {@link #unlock}/{@link #keepUntil} 에 동일 값을 넘긴다.
     * @param ttl 리스 기간(이 시간 후 자동 만료). 보호 구간 예상 실행시간보다 넉넉히.
     */
    boolean tryLock(String key, String token, Duration ttl);

    /** 보유한 락 해제. {@code token} 이 현재 소유자와 일치할 때만 삭제한다(원자적). 불일치/이미 만료면 무시. */
    void unlock(String key, String token);

    /**
     * 보유한 락의 만료 시각을 "지금부터 {@code ttl} 후"로 재설정한다(소유자일 때만). 두 용도:
     *
     * <ul>
     *   <li><b>연장(heartbeat)</b>: 장기 작업 중 TTL 만료를 막기 위해 주기적으로 늘림.
     *   <li><b>최소 보유(atLeastFor)</b>: 작업이 빨리 끝나도 일정 시간 락을 유지해, 클럭 스큐로 인한 직후 재실행을 막음
     *       ({@code SchedulerLockAspect} 가 사용).
     * </ul>
     */
    void keepUntil(String key, String token, Duration ttl);

    /**
     * 편의: 락을 잡으면 {@code task} 를 실행하고 해제한 뒤 {@code true}, 못 잡으면(타 인스턴스가 처리 중) 즉시 {@code false}.
     * k8s 다중 파드에서 단발 작업을 "여러 파드 중 한 곳에서만 1회" 실행할 때 쓴다.
     *
     * <p>주의: {@code task} 실행이 {@code ttl} 을 넘기면 도중에 락이 만료되어 다른 인스턴스가 진입할 수 있다 → {@code ttl} 을 넉넉히.
     */
    default boolean runIfLocked(String key, Duration ttl, Runnable task) {
        String token = UUID.randomUUID().toString();
        if (!tryLock(key, token, ttl)) {
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            unlock(key, token);
        }
    }
}
