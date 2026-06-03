package com.company.authserver.jose;

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

    /** 키 1건 삽입. */
    int insert(SigningKey key);

    /** 상태 전이(ACTIVE → RETIRED 등). */
    int updateStatus(@Param("kid") String kid, @Param("status") String status);
}
