package com.company.framework.file.storage;

import java.time.Duration;

/**
 * 사전서명(presigned) URL 발급을 지원하는 저장소의 <b>선택(capability) 인터페이스</b>.
 *
 * <p>S3 류 객체 저장소만 구현한다({@code S3FileStorage}). 로컬/NAS 파일시스템은 직접 URL 개념이 없어 미구현 —
 * 컨트롤러는 {@code storage instanceof PresignedUrlStorage} 로 판단해 미지원 시 적절히 거부한다.
 *
 * <p>대용량 업로드의 표준 해법: 서버는 presigned PUT URL 만 발급하고, 클라이언트가 S3 로 직접(멀티파트 포함)
 * 전송한 뒤 완료 키로 메타 등록을 요청한다 → 서버는 본문 바이트를 경유하지 않는다.
 */
public interface PresignedUrlStorage {

    /**
     * 기존 객체 다운로드용 presigned GET URL.
     *
     * @param storedPath 저장 키
     * @param ttl 유효 기간
     */
    PresignedUrl presignGet(String storedPath, Duration ttl);

    /**
     * 신규 업로드용 presigned PUT URL. 저장 키는 구현이 생성해 결과에 담아 돌려준다.
     *
     * @param originalName 원본 파일명(키의 확장자 결정용)
     * @param contentType 업로드 시 강제할 Content-Type(클라이언트가 동일하게 보내야 함)
     * @param ttl 유효 기간
     */
    PresignedUrl presignPut(String originalName, String contentType, Duration ttl);
}
