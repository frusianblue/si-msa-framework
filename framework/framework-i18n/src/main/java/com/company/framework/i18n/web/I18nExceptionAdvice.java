package com.company.framework.i18n.web;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.core.response.ApiResponse;
import com.company.framework.i18n.core.ErrorMessageResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * BusinessException 을 로케일별 메시지로 변환. 기존 core GlobalExceptionHandler 를 수정하지 않고,
 * 더 높은 우선순위(@Order HIGHEST)로 같은 예외를 가로채 메시지만 i18n 처리한다.
 * (코드/HTTP 상태/응답 포맷은 core 와 동일하게 유지)
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class I18nExceptionAdvice {

    private final ErrorMessageResolver resolver;

    public I18nExceptionAdvice(ErrorMessageResolver resolver) {
        this.resolver = resolver;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        String message = resolver.resolve(ec, e.getMessage());
        return ResponseEntity.status(ec.httpStatus()).body(ApiResponse.fail(ec.code(), message));
    }
}
