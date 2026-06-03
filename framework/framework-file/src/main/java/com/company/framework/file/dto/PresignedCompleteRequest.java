package com.company.framework.file.dto;

/**
 * presigned PUT 업로드 완료 후 메타 등록 요청.
 *
 * @param storedPath presigned 발급 시 받은 저장 키
 * @param originalName 원본 파일명
 * @param contentType 콘텐츠 타입
 * @param size 바이트 크기
 */
public record PresignedCompleteRequest(String storedPath, String originalName, String contentType, long size) {}
