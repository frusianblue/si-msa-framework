package com.company.framework.image;

import com.company.framework.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 이미지 처리 표준 에러 코드. 처리 실패는 core {@code BusinessException} + 이 코드로 던져
 * {@code GlobalExceptionHandler} 가 표준 응답으로 변환하도록 한다.
 *
 * <p>코드 영역 {@code IMG****}.
 */
public enum ImageErrorCode implements ErrorCode {
    /** 입력 바이트가 비었거나 null. */
    EMPTY_IMAGE("IMG0001", "이미지 데이터가 비어 있습니다.", HttpStatus.BAD_REQUEST),
    /** ImageIO 가 읽을 수 없는 형식이거나 손상된 데이터. */
    DECODE_FAILED("IMG0002", "이미지를 해석할 수 없습니다.", HttpStatus.BAD_REQUEST),
    /** 화이트리스트 밖 출력 포맷 요청 등. */
    UNSUPPORTED_FORMAT("IMG0003", "지원하지 않는 이미지 포맷입니다.", HttpStatus.BAD_REQUEST),
    /** 픽셀 수가 안전 상한(디컴프레션 폭탄 방지)을 초과. */
    IMAGE_TOO_LARGE("IMG0004", "이미지 크기가 허용 범위를 초과했습니다.", HttpStatus.PAYLOAD_TOO_LARGE),
    /** 인코딩(쓰기) 단계 실패. */
    ENCODE_FAILED("IMG0005", "이미지 인코딩에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ImageErrorCode(String code, String message, HttpStatus httpStatus) {
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
