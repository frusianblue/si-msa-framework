package com.company.framework.file.scan;

import java.io.InputStream;

/**
 * 업로드 본문 안티바이러스 스캔 전략(SPI).
 *
 * <p>저장 전에 호출되는 게이트. 기본 구현은 {@link NoOpFileScanner}(항상 통과)이며,
 * {@code framework.file.scan.type=clamav} 로 {@link ClamavFileScanner}(ClamAV INSTREAM) 를 옵트인할 수 있다.
 * 다른 백엔드가 필요하면 이 인터페이스를 구현한 빈을 등록하면 자동설정이 양보한다({@code @ConditionalOnMissingBean}).
 *
 * <p>구현은 전달된 스트림을 처음부터 끝까지 소비할 수 있다. 호출 측({@code FileService})은 스캔용으로 별도의
 * {@code MultipartFile#getInputStream()} 을 열어 전달하므로, 저장 단계에서 다시 새 스트림을 연다.
 */
public interface FileScanner {

    /**
     * 본문을 스캔한다.
     *
     * @param content 업로드 본문 스트림(구현이 끝까지 읽어도 됨 — 호출 측이 닫는다)
     * @param size 본문 바이트 길이(프로토콜상 미리 알아야 하는 백엔드용; 모르면 음수 허용)
     * @param filename 원본 파일명(로깅/일부 백엔드 힌트용)
     * @return 스캔 결과(정상/감염). 백엔드 장애 시 동작은 {@code fail-open} 설정에 따른다(구현 책임).
     */
    ScanResult scan(InputStream content, long size, String filename);

    /** 스캐너 종류 식별자(감사로그/결과 표기용). */
    String type();
}
