package com.company.framework.security.rbac.mapper;

import com.company.framework.security.rbac.domain.Menu;
import com.company.framework.security.rbac.domain.Resource;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SecurityMapper {

    /** 로그인 사용자의 역할 목록 (ROLE_*) */
    List<String> findRolesByLoginId(@Param("loginId") String loginId);

    /** URL-역할 매핑 전체 (동적 인가용) */
    List<Resource> findAllResources();

    /** 사용자 역할들이 접근 가능한 메뉴 목록 */
    List<Menu> findMenusByRoles(@Param("roles") List<String> roles);
}
