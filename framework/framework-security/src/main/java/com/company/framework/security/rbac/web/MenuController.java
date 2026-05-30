package com.company.framework.security.rbac.web;

import com.company.framework.core.response.ApiResponse;
import com.company.framework.security.rbac.core.MenuService;
import com.company.framework.security.rbac.dto.MenuDto;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 현재 로그인 사용자의 권한에 맞는 메뉴 트리를 반환.
 * 프론트는 이 응답으로 사이드바/네비게이션을 동적으로 구성한다.
 */
@RestController
@RequestMapping("/api/v1/menus")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/me")
    public ApiResponse<List<MenuDto>> myMenus(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return ApiResponse.ok(menuService.getMenuTree(roles));
    }
}
