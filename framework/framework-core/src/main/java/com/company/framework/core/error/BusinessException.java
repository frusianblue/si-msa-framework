package com.company.framework.core.error;

/**
 * 업무 로직에서 의도적으로 던지는 예외. GlobalExceptionHandler가 ErrorCode를 그대로 응답으로 변환한다.
 */
public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final transient ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
