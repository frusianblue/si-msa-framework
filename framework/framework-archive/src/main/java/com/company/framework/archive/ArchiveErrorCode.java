package com.company.framework.archive;

import com.company.framework.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 아카이빙/압축 표준 에러 코드. 안전 위반·한도 초과·IO 실패를 core {@code BusinessException} + 이 코드로 던져
 * {@code GlobalExceptionHandler} 가 표준 응답으로 변환하도록 한다.
 *
 * <p>코드 영역 {@code ARC****}.
 */
public enum ArchiveErrorCode implements ErrorCode {
    /** 입력 스트림/엔트리가 비었거나 null. */
    EMPTY_INPUT("ARC0001", "압축 대상 데이터가 비어 있습니다.", HttpStatus.BAD_REQUEST),
    /** 엔트리 경로가 안전하지 않음(zip-slip: 절대경로/상위경로 탈출/드라이브 등). */
    UNSAFE_ENTRY_PATH("ARC0002", "안전하지 않은 엔트리 경로입니다.", HttpStatus.BAD_REQUEST),
    /** 아카이브 엔트리 수가 허용 상한을 초과(압축폭탄 방지). */
    TOO_MANY_ENTRIES("ARC0003", "아카이브 엔트리 수가 허용 범위를 초과했습니다.", HttpStatus.CONTENT_TOO_LARGE),
    /** 단일 엔트리 해제 크기가 허용 상한을 초과(압축폭탄 방지). */
    ENTRY_TOO_LARGE("ARC0004", "엔트리 크기가 허용 범위를 초과했습니다.", HttpStatus.CONTENT_TOO_LARGE),
    /** 총 해제 바이트가 허용 상한을 초과(압축폭탄 방지). */
    ARCHIVE_TOO_LARGE("ARC0005", "압축 해제 크기가 허용 범위를 초과했습니다.", HttpStatus.CONTENT_TOO_LARGE),
    /** 읽기/해제 단계 IO 실패(손상된 아카이브 등). */
    READ_FAILED("ARC0006", "아카이브를 읽을 수 없습니다.", HttpStatus.BAD_REQUEST),
    /** 쓰기/생성 단계 IO 실패. */
    WRITE_FAILED("ARC0007", "아카이브 생성에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ArchiveErrorCode(String code, String message, HttpStatus httpStatus) {
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
