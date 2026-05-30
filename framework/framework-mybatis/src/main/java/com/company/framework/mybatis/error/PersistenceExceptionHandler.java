package com.company.framework.mybatis.error;

import com.company.framework.core.error.ErrorCode;
import com.company.framework.core.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * DB 영속성 예외 표준화. (framework-core 는 JDBC 의존이 없어 이 모듈에서 분리 처리)
 * 코어 폴백(Exception) 보다 먼저 매칭되도록 우선순위를 높인다.
 */
@Order(0)
@RestControllerAdvice
public class PersistenceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PersistenceExceptionHandler.class);

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateKeyException e) {
        log.warn("[DuplicateKey] traceId={} msg={}", MDC.get("traceId"), e.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, ErrorCode.Common.CONFLICT.code(), "이미 존재하는 데이터입니다.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("[DataIntegrity] traceId={} msg={}", MDC.get("traceId"), e.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, ErrorCode.Common.CONFLICT.code(), "데이터 무결성 제약을 위반했습니다.");
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.fail(code, message));
    }
}
