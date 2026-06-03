package com.company.framework.filebatch;

import com.company.framework.core.error.BusinessException;

/**
 * 일괄처리 대상 파일명 안전 유틸(순수 JDK — 위임 모듈 없이도 항상 동작).
 *
 * <p>rename/변환의 결과 이름은 <b>단일 파일명</b>이어야 한다(경로 세그먼트 금지). 경로 구분자·상위경로(..)·
 * 절대경로·Windows 드라이브 표기를 거부해 디렉토리 밖으로의 기록(경로조작)을 차단한다. 디렉토리 단위 안전이
 * 필요하면 archive 모듈 {@code ArchiveSafety.resolveSafely} 또는 secure-web {@code PathSupport} 를 추가로 쓴다.
 */
public final class BatchSafety {

    private BatchSafety() {}

    /** 안전한 단일 파일명인지 검증하고 그대로 돌려준다. 위반 시 {@link FileBatchErrorCode} 로 던진다. */
    public static String requireSimpleName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(FileBatchErrorCode.INVALID_INPUT, "대상 파일명이 비어 있습니다.");
        }
        if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            throw unsafe("경로 구분자를 포함할 수 없습니다", name);
        }
        if (name.equals(".") || name.equals("..")) {
            throw unsafe("상위/현재 경로 표기는 파일명이 될 수 없습니다", name);
        }
        if (name.length() >= 2 && name.charAt(1) == ':') {
            throw unsafe("드라이브 표기를 포함할 수 없습니다", name);
        }
        return name;
    }

    private static BusinessException unsafe(String why, String name) {
        // 원본 전체를 메시지에 싣지 않는다(로그 인젝션/정보노출 방지) — 사유 + 길이 힌트만.
        return new BusinessException(FileBatchErrorCode.UNSAFE_TARGET_NAME, why + "(length=" + name.length() + ").");
    }
}
