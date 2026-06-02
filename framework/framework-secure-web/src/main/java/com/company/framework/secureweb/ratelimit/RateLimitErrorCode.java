package com.company.framework.secureweb.ratelimit;

import com.company.framework.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 레이트리밋 거부 에러 코드. core 의 {@link ErrorCode} 확장점을 모듈에서 구현한 것
 * (core 의 Common 에 코드를 추가하지 않고 모듈 안에서 자급).
 *
 * <p>로그인 시도 제한(ISMS-P, core Common.LOGIN_LOCKED) 과는 별개 계층의 일반 API 레이트리밋이다.
 */
public enum RateLimitErrorCode implements ErrorCode {
    RATE_LIMITED("E0429", "요청이 너무 많습니다. 잠시 후 다시 시도하세요.", HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    RateLimitErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
