package com.company.framework.secureweb.support;

import com.company.framework.core.error.ErrorCode;
import com.company.framework.core.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * 필터 계층(디스패처 이전)에서 거부 응답을 표준 {@link ApiResponse} 포맷으로 직접 기록한다.
 * 필터에서 던진 예외는 GlobalExceptionHandler(@RestControllerAdvice)가 처리하지 못하므로,
 * 컨트롤러 에러와 동일한 JSON 모양({success:false, code, message, timestamp})을 직접 써준다.
 *
 * <p>JSON 직렬화는 Jackson 에 의존하지 않고 수기로 만든다. 이유:
 * 본 스택은 Jackson 3(tools.jackson.*) 을 쓰므로 com.fasterxml.jackson(2) 의 ObjectMapper 가 없고,
 * 버전/빈주입에 묶이지 않도록 고정 형태(아주 단순)인 거부 응답은 직접 직렬화하는 편이 견고하다.
 */
public class SecureWebResponder {

    public void writeError(HttpServletResponse response, ErrorCode errorCode, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(errorCode.httpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        // ApiResponse.fail(code, message) 과 동일한 형태. data 는 null 이라 NON_NULL 정책상 생략.
        String json = "{\"success\":false,\"code\":\""
                + escape(errorCode.code())
                + "\",\"message\":\""
                + escape(message)
                + "\",\"timestamp\":\""
                + Instant.now()
                + "\"}";
        response.getWriter().write(json);
    }

    /** JSON 문자열 값 이스케이프(따옴표/역슬래시/제어문자). 한글은 UTF-8 그대로 출력. */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
