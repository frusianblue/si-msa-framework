package com.company.admin.controller;

import com.company.admin.domain.ResourceRow;
import com.company.admin.dto.ResourceForm;
import com.company.admin.dto.RoleMapForm;
import com.company.admin.mapper.AdminMapper;
import com.company.framework.core.aspect.AuditLog;
import com.company.framework.core.response.ApiResponse;
import com.company.framework.security.rbac.core.SecurityMetadataService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * URL-권한(리소스) 관리 API. 변경 후 동적 인가 캐시를 즉시 갱신해 무중단 반영.
 * 관리자(ROLE_ADMIN)만 접근 가능 (@PreAuthorize).
 */
@RestController
@RequestMapping("/api/v1/admin/resources")
@PreAuthorize("hasRole('ADMIN')")
public class ResourceAdminController {

    private final AdminMapper adminMapper;
    private final SecurityMetadataService metadataService;

    public ResourceAdminController(AdminMapper adminMapper, SecurityMetadataService metadataService) {
        this.adminMapper = adminMapper;
        this.metadataService = metadataService;
    }

    @GetMapping
    public ApiResponse<List<ResourceRow>> list() {
        return ApiResponse.ok(adminMapper.listResources());
    }

    @PostMapping
    @AuditLog(action = "RESOURCE_CREATE", target = "RESOURCE")
    public ApiResponse<ResourceRow> create(@Valid @RequestBody ResourceForm form) {
        ResourceRow row = new ResourceRow();
        row.setUrlPattern(form.urlPattern());
        row.setHttpMethod(form.httpMethod() == null ? "ALL" : form.httpMethod());
        row.setDescr(form.descr());
        row.setSortOrder(form.sortOrder() == null ? 0 : form.sortOrder());
        adminMapper.insertResource(row);   // 감사필드는 인터셉터가 자동 주입
        metadataService.reload();
        return ApiResponse.ok(row, "리소스가 등록되었습니다.");
    }

    @PutMapping("/{id}")
    @AuditLog(action = "RESOURCE_UPDATE", target = "RESOURCE")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody ResourceForm form) {
        ResourceRow row = new ResourceRow();
        row.setId(id);
        row.setUrlPattern(form.urlPattern());
        row.setHttpMethod(form.httpMethod() == null ? "ALL" : form.httpMethod());
        row.setDescr(form.descr());
        row.setSortOrder(form.sortOrder() == null ? 0 : form.sortOrder());
        adminMapper.updateResource(row);
        metadataService.reload();
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @AuditLog(action = "RESOURCE_DELETE", target = "RESOURCE")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        adminMapper.deleteResource(id);
        metadataService.reload();
        return ApiResponse.ok();
    }

    @PostMapping("/role-map")
    public ApiResponse<Void> mapRole(@RequestBody RoleMapForm form) {
        adminMapper.mapRoleResource(form.roleId(), form.targetId());
        metadataService.reload();
        return ApiResponse.ok();
    }

    @DeleteMapping("/role-map")
    public ApiResponse<Void> unmapRole(@RequestBody RoleMapForm form) {
        adminMapper.unmapRoleResource(form.roleId(), form.targetId());
        metadataService.reload();
        return ApiResponse.ok();
    }
}
