package com.company.framework.secureweb.support;

import com.company.framework.core.error.ErrorCode;
import com.company.framework.core.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 필터 계층(디스패처 이전)에서 거부 응답을 표준 {@link ApiResponse} 포맷으로 직접 기록한다.
 * 필터에서 던진 예외는 GlobalExceptionHandler(@RestControllerAdvice)가 처리하지 못하므로,
 * 컨트롤러 에러와 동일한 JSON 모양({success:false, code, message, timestamp})을 직접 써준다.
 */
public class SecureWebResponder {

    private final ObjectMapper objectMapper;

    public SecureWebResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeError(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(errorCode.httpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        ApiResponse<Void> body = ApiResponse.fail(errorCode.code(), message);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
