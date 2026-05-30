package com.company.framework.core.error;

import com.company.framework.core.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 전사 공통 전역 예외 처리기. 모든 예외를 표준 ApiResponse 포맷으로 변환한다.
 * 흔한 클라이언트 오류(잘못된 JSON/타입/파라미터/404/업로드초과)와 서버 오류를 구분 처리.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        log.warn("[Business] traceId={} code={} msg={}", MDC.get("traceId"), ec.code(), e.getMessage());
        return build(ec.httpStatus(), ec.code(), e.getMessage());
    }

    // ===== 검증 실패 (@Valid 바디 / @Validated 파라미터) =====
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError).collect(Collectors.joining(", "));
        return common(ErrorCode.Common.INVALID_INPUT, detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException e) {
        return common(ErrorCode.Common.INVALID_INPUT, e.getMessage());
    }

    // ===== 잘못된 요청 형식 =====
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        return common(ErrorCode.Common.INVALID_INPUT, "요청 본문을 해석할 수 없습니다(JSON 형식 확인).");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return common(ErrorCode.Common.INVALID_INPUT, "파라미터 타입이 올바르지 않습니다: " + e.getName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        return common(ErrorCode.Common.INVALID_INPUT, "필수 파라미터 누락: " + e.getParameterName());
    }

    // ===== 라우팅/메서드 =====
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e) {
        return common(ErrorCode.Common.NOT_FOUND, "요청 경로를 찾을 수 없습니다.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "E0405", "지원하지 않는 HTTP 메서드입니다: " + e.getMethod());
    }

    // ===== 업로드 =====
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException e) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "E0413", "업로드 가능한 파일 크기를 초과했습니다.");
    }

    // ===== 최종 폴백 =====
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        ErrorCode ec = ErrorCode.Common.INTERNAL_ERROR;
        log.error("[Unexpected] traceId={}", MDC.get("traceId"), e);
        return build(ec.httpStatus(), ec.code(), ec.message());
    }

    private ResponseEntity<ApiResponse<Void>> common(ErrorCode ec, String message) {
        return build(ec.httpStatus(), ec.code(), (message == null || message.isBlank()) ? ec.message() : message);
    }

    private ResponseEntity<ApiResponse<Void>> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.fail(code, message));
    }

    private String formatFieldError(FieldError fe) {
        return "%s: %s".formatted(fe.getField(), fe.getDefaultMessage());
    }
}
