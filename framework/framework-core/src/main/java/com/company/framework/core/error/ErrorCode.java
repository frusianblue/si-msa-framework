package com.company.framework.core.error;

import org.springframework.http.HttpStatus;

/**
 * 전사 공통 에러 코드. 업무별 코드는 각 서비스에서 이 인터페이스를 구현해 확장한다.
 */
public interface ErrorCode {
    String code();
    String message();
    HttpStatus httpStatus();

    enum Common implements ErrorCode {
        INTERNAL_ERROR("E0000", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
        INVALID_INPUT("E0001", "입력값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
        UNAUTHORIZED("E0401", "인증이 필요합니다.", HttpStatus.UNAUTHORIZED),
        FORBIDDEN("E0403", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
        NOT_FOUND("E0404", "요청한 리소스를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
        CONFLICT("E0409", "리소스 상태가 충돌합니다.", HttpStatus.CONFLICT);

        private final String code;
        private final String message;
        private final HttpStatus httpStatus;

        Common(String code, String message, HttpStatus httpStatus) {
            this.code = code;
            this.message = message;
            this.httpStatus = httpStatus;
        }

        @Override public String code() { return code; }
        @Override public String message() { return message; }
        @Override public HttpStatus httpStatus() { return httpStatus; }
    }
}
