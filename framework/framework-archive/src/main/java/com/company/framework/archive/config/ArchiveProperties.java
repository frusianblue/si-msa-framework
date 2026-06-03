package com.company.framework.archive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 아카이빙 모듈 설정. 접두사 {@code framework.archive}. 선택형 모듈 컨벤션대로 기본 비활성.
 *
 * <pre>
 * framework:
 *   archive:
 *     enabled: true                  # 모듈 전체 토글(기본 false)
 *     max-entries: 10000             # 해제 허용 최대 엔트리 수(압축폭탄 방지)
 *     max-entry-size: 104857600      # 단일 엔트리 해제 최대 바이트(기본 100MB)
 *     max-total-bytes: 1073741824    # 총 해제 최대 바이트(기본 1GB) — gunzip 단일 스트림 상한으로도 재사용
 * </pre>
 */
@ConfigurationProperties(prefix = "framework.archive")
public class ArchiveProperties {

    /** 모듈 전체 활성 여부(기본 false). */
    private boolean enabled = false;

    /** 해제 허용 최대 엔트리 수(기본 10,000). */
    private int maxEntries = 10_000;

    /** 단일 엔트리 해제 최대 바이트(기본 100MB). */
    private long maxEntrySize = 100L * 1024 * 1024;

    /** 총 해제 최대 바이트(기본 1GB). */
    private long maxTotalBytes = 1024L * 1024 * 1024;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public long getMaxEntrySize() {
        return maxEntrySize;
    }

    public void setMaxEntrySize(long maxEntrySize) {
        this.maxEntrySize = maxEntrySize;
    }

    public long getMaxTotalBytes() {
        return maxTotalBytes;
    }

    public void setMaxTotalBytes(long maxTotalBytes) {
        this.maxTotalBytes = maxTotalBytes;
    }
}
