package com.company.framework.security.handler;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.time.Instant;

/** 보안 필터 단계에서 표준 ApiResponse(JSON)를 직접 기록. (컨트롤러 진입 전이라 수동 작성) */
final class ErrorResponseWriter {
    private ErrorResponseWriter() {}

    static void write(HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.setHeader("X-Trace-Id", MDC.get("traceId") == null ? "" : MDC.get("traceId"));
        String body = "{\"success\":false,\"code\":\"" + code + "\",\"message\":\"" + escape(message)
                + "\",\"timestamp\":\"" + Instant.now() + "\"}";
        res.getWriter().write(body);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
