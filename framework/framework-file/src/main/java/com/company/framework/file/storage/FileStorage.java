package com.company.framework.file.storage;

import java.io.InputStream;

/**
 * 파일 저장 백엔드 추상화. 구현 선택은 framework.file.storage.type 으로:
 *  - local : 로컬 디스크 (기본)
 *  - nas   : 마운트된 NAS 경로 (로컬 구현 공유, base-path 만 NAS 마운트로)
 *  - s3    : AWS S3 (framework-file-s3 모듈)
 */
public interface FileStorage {
    StoredFile store(InputStream content, String originalName, String contentType, long size);
    InputStream load(String storedPath);
    void delete(String storedPath);
    String type();
}
