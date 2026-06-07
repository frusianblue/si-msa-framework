package com.company.framework.security.rbac.mybatis;

import com.company.framework.security.rbac.domain.Menu;
import com.company.framework.security.rbac.mapper.SecurityMapper;
import com.company.framework.security.rbac.spi.MenuProvider;
import java.util.List;

/**
 * {@link MenuProvider} 의 MyBatis 구현. 역할별 메뉴를 {@link SecurityMapper} 로 조회한다.
 * 트리 변환은 코어 {@code MenuService} 가 수행하고, 본 어댑터는 평면 조회만 담당한다.
 */
public class MyBatisMenuProvider implements MenuProvider {

    private final SecurityMapper securityMapper;

    public MyBatisMenuProvider(SecurityMapper securityMapper) {
        this.securityMapper = securityMapper;
    }

    @Override
    public List<Menu> findMenusByRoles(List<String> roles) {
        return securityMapper.findMenusByRoles(roles);
    }
}
