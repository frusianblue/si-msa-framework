package com.company.admin.controller;

import com.company.admin.mapper.AdminMapper;
import com.company.framework.core.response.ApiResponse;
import com.company.framework.security.rbac.core.SecurityMetadataService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/security")
@PreAuthorize("hasRole('ADMIN')")
public class SecurityAdminController {

    private final AdminMapper adminMapper;
    private final SecurityMetadataService metadataService;

    public SecurityAdminController(AdminMapper adminMapper, SecurityMetadataService metadataService) {
        this.adminMapper = adminMapper;
        this.metadataService = metadataService;
    }

    @GetMapping("/roles")
    public ApiResponse<List<Map<String, Object>>> roles() {
        return ApiResponse.ok(adminMapper.listRoles());
    }

    /** URL-권한 매핑 캐시 강제 갱신 (운영 중 권한 변경 즉시 반영) */
    @PostMapping("/reload")
    public ApiResponse<Void> reload() {
        metadataService.reload();
        return ApiResponse.ok(null, "권한 캐시를 갱신했습니다.");
    }
}
