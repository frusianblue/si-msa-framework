package com.company.framework.security.password;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 비밀번호 이력/마지막 변경시각 보관. (재사용 금지 + 만료 판정의 백엔드)
 * 구현은 상황별 교체: memory(로컬) | jdbc(폐쇄망/공공·영속). TokenStore 와 동일 사상.
 * 저장하는 비밀번호는 항상 인코딩(BCrypt)된 값이다. 평문을 넘기지 말 것.
 */
public interface PasswordHistoryStore {

    /** 최근 비밀번호(인코딩값)를 이력에 추가하고 변경시각을 now 로 기록한다. count 초과분은 폐기. */
    void record(String userId, String encodedPassword, int keepCount);

    /** 비교 대상이 되는 최근 인코딩 비밀번호 목록(최신순). */
    List<String> recentEncoded(String userId, int count);

    /** 마지막 변경시각. 이력이 없으면 empty(만료 판정에서 '미설정'으로 처리). */
    Optional<Instant> lastChangedAt(String userId);
}
