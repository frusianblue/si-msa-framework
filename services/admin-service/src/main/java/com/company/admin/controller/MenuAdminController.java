package com.company.admin.controller;

import com.company.admin.domain.MenuRow;
import com.company.admin.dto.MenuForm;
import com.company.admin.dto.RoleMapForm;
import com.company.admin.mapper.AdminMapper;
import com.company.framework.core.aspect.AuditLog;
import com.company.framework.core.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/menus")
@PreAuthorize("hasRole('ADMIN')")
public class MenuAdminController {

    private final AdminMapper adminMapper;

    public MenuAdminController(AdminMapper adminMapper) {
        this.adminMapper = adminMapper;
    }

    @GetMapping
    public ApiResponse<List<MenuRow>> list() {
        return ApiResponse.ok(adminMapper.listMenus());
    }

    @PostMapping
    @AuditLog(action = "MENU_CREATE", target = "MENU")
    public ApiResponse<MenuRow> create(@Valid @RequestBody MenuForm form) {
        MenuRow row = new MenuRow();
        row.setParentId(form.parentId());
        row.setName(form.name());
        row.setUrl(form.url());
        row.setIcon(form.icon());
        row.setSortOrder(form.sortOrder() == null ? 0 : form.sortOrder());
        adminMapper.insertMenu(row);
        return ApiResponse.ok(row, "메뉴가 등록되었습니다.");
    }

    @PutMapping("/{id}")
    @AuditLog(action = "MENU_UPDATE", target = "MENU")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody MenuForm form) {
        MenuRow row = new MenuRow();
        row.setId(id);
        row.setParentId(form.parentId());
        row.setName(form.name());
        row.setUrl(form.url());
        row.setIcon(form.icon());
        row.setSortOrder(form.sortOrder() == null ? 0 : form.sortOrder());
        adminMapper.updateMenu(row);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @AuditLog(action = "MENU_DELETE", target = "MENU")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminMapper.deleteMenu(id);
        return ApiResponse.ok();
    }

    @PostMapping("/role-map")
    public ApiResponse<Void> mapRole(@RequestBody RoleMapForm form) {
        adminMapper.mapRoleMenu(form.roleId(), form.targetId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/role-map")
    public ApiResponse<Void> unmapRole(@RequestBody RoleMapForm form) {
        adminMapper.unmapRoleMenu(form.roleId(), form.targetId());
        return ApiResponse.ok();
    }
}
