package com.company.framework.oauthclient.store;

import java.time.Duration;
import java.util.Optional;

/**
 * OAuth state(CSRF 방지 토큰) 저장소 SPI. authorize 단계에서 발급한 state 를 저장하고, callback 단계에서
 * 1회 소비(consume)하며 검증한다. state → 공급자 id 매핑을 담는다.
 *
 * <ul>
 *   <li>memory : 단일 인스턴스/로컬(인프라 0). authorize/callback 이 다른 파드로 가면 무력.
 *   <li>redis  : 다중 파드 공유 + TTL 네이티브 만료(운영 권장).
 * </ul>
 */
public interface OAuthStateStore {

    /** state 와 공급자 id 를 ttl 동안 저장. */
    void save(String state, String providerId, Duration ttl);

    /** state 를 검증하며 1회 소비(존재 시 즉시 삭제). 유효하면 공급자 id 를 반환. */
    Optional<String> consume(String state);
}
