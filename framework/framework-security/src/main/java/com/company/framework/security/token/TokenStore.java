package com.company.framework.security.token;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * refresh token 보관/검증 + access token 블랙리스트(로그아웃). 구현은 상황별 선택:
 *  - memory : 로컬 개발(인프라 0)
 *  - jdbc   : 폐쇄망/공공 SI (기존 DB 재사용)
 *  - redis  : 운영 표준(MSA, TTL 자동만료)
 */
public interface TokenStore {

    void saveRefresh(String refreshToken, RefreshEntry entry, Duration ttl);

    Optional<RefreshEntry> findRefresh(String refreshToken);

    void removeRefresh(String refreshToken);

    /** access token 의 jti 를 만료시점까지 블랙리스트 처리 */
    void blacklist(String jti, Duration ttl);

    boolean isBlacklisted(String jti);

    /** refresh token 에 묶인 사용자/권한 (재발급 시 새 access 생성에 사용) */
    record RefreshEntry(String userId, List<String> roles) {}
}
