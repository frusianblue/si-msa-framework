package com.company.framework.file.storage;

/** 저장 결과. storedPath 는 저장소 내 상대 경로/키(다운로드/삭제에 사용). */
public record StoredFile(String storedPath, String originalName, String contentType, long size) {}
