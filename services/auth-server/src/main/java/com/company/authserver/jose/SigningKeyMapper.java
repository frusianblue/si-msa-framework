package com.company.authserver.jose;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 서명키 저장소(MyBatis). 다중 파드가 같은 테이블을 공유한다. */
@Mapper
public interface SigningKeyMapper {

    /** 검증용으로 노출할 키 전체(ACTIVE + RETIRED). 최신순. JWKS 셋 구성에 사용. */
    List<SigningKey> findAllUsable();

    /** 현재 서명에 쓸 키(가장 최신 ACTIVE 1건). 없으면 null. */
    SigningKey findNewestActive();

    /** 키 1건 삽입(신규 ACTIVE 는 retired_at=null). */
    int insert(SigningKey key);

    /** 상태 전이(ACTIVE → RETIRED 등). 단건. 회전은 {@link #retireAllActive} 를 쓴다. */
    int updateStatus(@Param("kid") String kid, @Param("status") String status);

    /**
     * 현재 ACTIVE 인 키를 전부 RETIRED 로 전이하고 {@code retiredAt} 을 기록한다(회전 시 직전 키 폐기). 반환=전이된 행 수.
     * 회전은 "RETIRE 먼저 → 새 ACTIVE INSERT" 순서라 직전 ACTIVE 들을 일괄 처리한다.
     */
    int retireAllActive(@Param("retiredAt") Instant retiredAt);

    /**
     * RETIRE 된 지 grace 가 지난(=retired_at &lt; cutoff) RETIRED 키 삭제. 반환=삭제 행 수.
     * ⚠️ 기준은 retired_at(폐기 시각)이다 — created_at(생성 시각) 기준이면 grace &lt; 회전주기일 때 직전 키가 즉시 삭제돼 오버랩이 깨진다.
     */
    int deleteRetiredOlderThan(@Param("cutoff") Instant cutoff);
}
