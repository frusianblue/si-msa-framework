package com.company.framework.audit.query;

import com.company.framework.audit.model.AuditEvent;
import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.core.page.PageRequest;
import com.company.framework.core.page.PageResponse;
import com.company.framework.core.response.ApiResponse;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 감사 로그 조회 표준 엔드포인트(jdbc 싱크 전용). /api 하위라 보안체인 인가 대상(관리자 권한 권장).
 * from/to 는 ISO-8601 instant("2026-05-01T00:00:00Z").
 * 예) GET /api/v1/audit/logs?actor=admin&eventType=LOGIN_FAILURE&from=2026-05-01T00:00:00Z&page=1&size=20
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditQueryService queryService;

    public AuditController(AuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/logs")
    public ApiResponse<PageResponse<AuditEvent>> logs(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        AuditQuery query = new AuditQuery(
                actor,
                eventType,
                result,
                parseInstant(from, "from"),
                parseInstant(to, "to"),
                PageRequest.of(page, size));
        return ApiResponse.ok(queryService.search(query));
    }

    private static Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new BusinessException(
                    ErrorCode.Common.INVALID_INPUT, field + " 는 ISO-8601 instant 형식이어야 합니다(예: 2026-05-01T00:00:00Z).");
        }
    }
}
