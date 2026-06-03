package com.company.framework.file.storage;

import java.time.Instant;
import java.util.Map;

/**
 * 사전서명(presigned) URL 발급 결과. 클라이언트는 이 URL 로 저장소(S3)에 <b>서버를 거치지 않고</b> 직접
 * 업로드(PUT)하거나 다운로드(GET)한다 — 대용량 전송 시 애플리케이션 메모리/대역을 절약한다.
 *
 * @param method HTTP 메서드(GET/PUT)
 * @param url 서명된 URL(만료 시각까지 유효)
 * @param storedPath 대상 저장 키(PUT 발급 시 이후 메타 등록에 사용)
 * @param expiresAt 만료 시각
 * @param requiredHeaders PUT 시 클라이언트가 반드시 동일하게 보내야 하는 헤더(예: Content-Type). 비어 있을 수 있음
 */
public record PresignedUrl(
        String method, String url, String storedPath, Instant expiresAt, Map<String, String> requiredHeaders) {

    public PresignedUrl {
        requiredHeaders = requiredHeaders == null ? Map.of() : Map.copyOf(requiredHeaders);
    }
}
