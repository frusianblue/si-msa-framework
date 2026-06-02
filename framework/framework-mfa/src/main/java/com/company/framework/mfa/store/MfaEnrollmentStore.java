package com.company.framework.mfa.store;

import com.company.framework.mfa.core.MfaMethod;
import java.util.List;
import java.util.Optional;

/**
 * MFA 등록(영속) 저장소 SPI. 구현은 상황별 선택:
 *
 * <ul>
 *   <li>memory : 로컬 개발/테스트(인프라 0, 재기동 휘발)
 *   <li>jdbc   : 폐쇄망/공공·운영(기존 DataSource 재사용, 영속). 표준 운영 권장값.
 * </ul>
 *
 * 등록은 영속이 본질이므로 운영에서는 jdbc 를 쓴다. 프로젝트가 빈을 직접 등록하면 그 구현이 우선한다.
 */
public interface MfaEnrollmentStore {

    Optional<MfaEnrollment> find(String userId, MfaMethod method);

    List<MfaEnrollment> findByUser(String userId);

    /** upsert(동일 userId+method 면 덮어씀). */
    void save(MfaEnrollment enrollment);

    void delete(String userId, MfaMethod method);
}
