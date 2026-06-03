package com.company.framework.file.scan;

import java.io.IOException;
import java.io.InputStream;

/**
 * 기본 스캐너 — 실제 스캔 없이 항상 통과시킨다(스트림은 끝까지 비워 호출 측 자원 정리를 단순화).
 * {@code framework.file.scan.enabled=false}(기본) 또는 {@code type=none} 일 때 등록된다.
 */
public class NoOpFileScanner implements FileScanner {

    @Override
    public ScanResult scan(InputStream content, long size, String filename) {
        // 스트림을 소비하지 않는다 — 저장 단계에서 별도 스트림을 사용하므로 굳이 읽을 필요 없음.
        return ScanResult.clean("none");
    }

    @Override
    public String type() {
        return "none";
    }

    /** 호출 측이 스캔용 스트림을 명시적으로 닫지 못한 경우 대비(현재 미사용, 향후 확장 여지). */
    static void drainQuietly(InputStream in) {
        try (InputStream ignored = in) {
            in.readAllBytes();
        } catch (IOException ignored) {
            // 무시
        }
    }
}
