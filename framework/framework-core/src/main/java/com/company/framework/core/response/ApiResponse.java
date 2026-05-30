package com.company.framework.core.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * 모든 REST API의 표준 성공 응답 래퍼.
 * 프로젝트 전 서비스가 동일 포맷을 사용하도록 강제하는 것이 목적.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String code, String message, T data, Instant timestamp) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "정상 처리되었습니다.", data, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, "OK", message, data, Instant.now());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, "OK", "정상 처리되었습니다.", null, Instant.now());
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, code, message, null, Instant.now());
    }
}
