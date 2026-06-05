package com.company.authserver.user;

import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * auth-server 사용자 조회 매퍼. {@code @Mapper} 필수 — AuthServerApplication 의
 * {@code @MapperScan(annotationClass = Mapper.class)} 가 이 애너테이션 있는 인터페이스만 스캔한다.
 */
@Mapper
public interface AppUserMapper {

    Optional<AppUser> findByLoginId(@Param("loginId") String loginId);
}
