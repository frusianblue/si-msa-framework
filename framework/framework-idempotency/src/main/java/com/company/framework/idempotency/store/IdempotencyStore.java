package com.company.framework.idempotency.store;

import java.time.Duration;
import java.util.Optional;

/**
 * 멱등 결과 저장소(추상). 구현은 store.type 으로 선택(memory/redis/jdbc).
 * 프로젝트가 동일 타입 빈을 등록하면 @ConditionalOnMissingBean 으로 그쪽이 우선.
 */
public interface IdempotencyStore {

    /** 키 선점 시도. 최초면 true(이번 요청이 처리 주체), 이미 있으면 false(중복). */
    boolean putIfAbsent(String key, Duration ttl);

    /** 처리 완료 후 결과 스냅샷 저장(재요청에 동일 응답 반환용). */
    void saveResult(String key, String resultJson, Duration ttl);

    /** 저장된 결과 조회. */
    Optional<String> findResult(String key);

    /**
     * 선점한 키를 해제(삭제)한다. 소비자 멱등 처리에서 핸들러가 실패해 재배달/재처리가 필요할 때,
     * {@link #putIfAbsent} 로 잡은 키를 풀어 다음 배달이 다시 처리하도록 한다.
     */
    void remove(String key);
}
