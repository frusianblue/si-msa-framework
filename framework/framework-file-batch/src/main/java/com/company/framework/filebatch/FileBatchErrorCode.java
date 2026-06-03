package com.company.framework.filebatch;

import com.company.framework.core.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 파일 일괄처리 표준 에러 코드. 입력 오류·경로 안전 위반·이름 충돌·작업 실패를 core
 * {@code BusinessException} + 이 코드로 던져 {@code GlobalExceptionHandler} 가 표준 응답으로 변환하게 한다.
 *
 * <p>코드 영역 {@code FBAT****}.
 */
public enum FileBatchErrorCode implements ErrorCode {
    /** 아이템/옵션 등 입력이 올바르지 않음(이름 누락, 본문 소스 부재 등). */
    INVALID_INPUT("FBAT0001", "일괄처리 입력값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
    /** 대상 이름이 안전하지 않음(경로 구분자/상위경로/절대경로/드라이브 등). */
    UNSAFE_TARGET_NAME("FBAT0002", "안전하지 않은 대상 파일명입니다.", HttpStatus.BAD_REQUEST),
    /** 이름 변경 결과가 서로 충돌(중복). */
    NAME_COLLISION("FBAT0003", "이름 변경 결과가 충돌합니다.", HttpStatus.CONFLICT),
    /** 개별 작업(op) 실행 실패 — 부분 실패로 수집되거나 fail-fast 로 중단된다. */
    OPERATION_FAILED("FBAT0004", "파일 작업에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
    /** 읽기/쓰기 단계 IO 실패. */
    IO_FAILED("FBAT0005", "파일 입출력에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    FileBatchErrorCode(String code, String message, HttpStatus httpStatus) {
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
