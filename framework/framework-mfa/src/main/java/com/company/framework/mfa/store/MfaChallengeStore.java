package com.company.framework.mfa.store;

import java.time.Duration;
import java.util.Optional;

/**
 * 로그인 2단계 대기 인증(PendingAuth) 저장소 SPI. 구현은 상황별 선택:
 *
 * <ul>
 *   <li>memory : 단일 인스턴스/로컬(인프라 0). <b>다중 인스턴스에서는 무력</b>(인스턴스별 맵).
 *   <li>redis  : 운영 표준(MSA 다중 파드). TTL 네이티브 만료, 인스턴스 간 공유.
 * </ul>
 *
 * 로그인 1단계와 2단계 요청이 서로 다른 파드로 갈 수 있으므로 <b>다중 인스턴스 환경에서는 redis 가 사실상 필수</b>.
 */
public interface MfaChallengeStore {

    void save(String ticket, PendingAuth pending, Duration ttl);

    Optional<PendingAuth> find(String ticket);

    void remove(String ticket);
}
