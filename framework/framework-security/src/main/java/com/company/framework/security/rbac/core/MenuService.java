package com.company.framework.security.rbac.core;

import com.company.framework.security.rbac.domain.Menu;
import com.company.framework.security.rbac.dto.MenuDto;
import com.company.framework.security.rbac.mapper.SecurityMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 로그인 사용자의 역할에 매핑된 메뉴를 조회해 계층형 트리로 반환한다.
 */
public class MenuService {

    private final SecurityMapper securityMapper;

    public MenuService(SecurityMapper securityMapper) {
        this.securityMapper = securityMapper;
    }

    public List<MenuDto> getMenuTree(List<String> roles) {
        if (roles == null || roles.isEmpty()) return List.of();
        List<Menu> menus = securityMapper.findMenusByRoles(roles);

        Map<Long, MenuDto> map = new LinkedHashMap<>();
        for (Menu m : menus) {
            map.put(m.getId(), new MenuDto(m.getId(), m.getName(), m.getUrl(), m.getIcon()));
        }
        List<MenuDto> roots = new ArrayList<>();
        for (Menu m : menus) {
            MenuDto node = map.get(m.getId());
            if (m.getParentId() == null || !map.containsKey(m.getParentId())) {
                roots.add(node);
            } else {
                map.get(m.getParentId()).getChildren().add(node);
            }
        }
        return roots;
    }
}
