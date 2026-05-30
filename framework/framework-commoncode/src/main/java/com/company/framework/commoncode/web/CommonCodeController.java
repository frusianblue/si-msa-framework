package com.company.framework.commoncode.web;

import com.company.framework.commoncode.dto.CommonCodeDto;
import com.company.framework.commoncode.dto.CommonCodeForm;
import com.company.framework.commoncode.service.CommonCodeService;
import com.company.framework.core.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 공통코드 API. 조회는 인증 사용자, 변경(CRUD)은 ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/common-codes")
public class CommonCodeController {

    private final CommonCodeService service;

    public CommonCodeController(CommonCodeService service) {
        this.service = service;
    }

    @GetMapping("/{groupCode}")
    public ApiResponse<List<CommonCodeDto>> byGroup(@PathVariable String groupCode) {
        return ApiResponse.ok(service.getByGroup(groupCode));
    }

    @GetMapping("/groups")
    public ApiResponse<List<String>> groups() {
        return ApiResponse.ok(service.getAllGroups());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> create(@Valid @RequestBody CommonCodeForm form) {
        service.create(form);
        return ApiResponse.ok(null, "공통코드가 등록되었습니다.");
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> update(@Valid @RequestBody CommonCodeForm form) {
        service.update(form);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{groupCode}/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable String groupCode, @PathVariable String code) {
        service.delete(groupCode, code);
        return ApiResponse.ok();
    }
}
