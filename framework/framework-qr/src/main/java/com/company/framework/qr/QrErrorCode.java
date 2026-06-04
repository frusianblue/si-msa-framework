package com.company.framework.qr;

import com.company.framework.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * QR 생성 표준 에러 코드. 생성 실패는 core {@code BusinessException} + 이 코드로 던져
 * {@code GlobalExceptionHandler} 가 표준 응답으로 변환하도록 한다.
 *
 * <p>코드 영역 {@code QR****}.
 */
public enum QrErrorCode implements ErrorCode {
    /** 인코딩할 내용이 비었거나 null. */
    EMPTY_CONTENT("QR0001", "QR 로 만들 내용이 비어 있습니다.", HttpStatus.BAD_REQUEST),
    /** 내용 길이가 설정 상한을 초과(QR 용량 한계 방어 — 인코딩 전 차단). */
    CONTENT_TOO_LONG("QR0002", "QR 로 만들 내용이 허용 길이를 초과했습니다.", HttpStatus.BAD_REQUEST),
    /** 스펙 값이 허용 범위를 벗어남(크기/여백 등). */
    INVALID_SPEC("QR0003", "QR 생성 옵션이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    /** ZXing 인코딩(용량 초과·문자셋 등) 실패. */
    ENCODE_FAILED("QR0004", "QR 인코딩에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    /** PNG 쓰기(ImageIO) 단계 실패. */
    RENDER_FAILED("QR0005", "QR 이미지 렌더링에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    QrErrorCode(String code, String message, HttpStatus httpStatus) {
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
