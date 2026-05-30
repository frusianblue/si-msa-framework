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
}
