package com.company.framework.file.scan;

/**
 * 안티바이러스 스캔 결과.
 *
 * @param clean 감염되지 않았으면 true
 * @param signature 감염 시 탐지된 시그니처명(예: {@code Eicar-Test-Signature}), 정상이면 null
 * @param scannerType 스캔을 수행한 스캐너 종류(감사로그용, 예: {@code none}/{@code clamav})
 */
public record ScanResult(boolean clean, String signature, String scannerType) {

    /** 정상(감염 없음) 결과. */
    public static ScanResult clean(String scannerType) {
        return new ScanResult(true, null, scannerType);
    }

    /** 감염 결과(탐지 시그니처 포함). */
    public static ScanResult infected(String signature, String scannerType) {
        return new ScanResult(false, signature, scannerType);
    }

    /** 감염 여부(가독성용). */
    public boolean infected() {
        return !clean;
    }
}
