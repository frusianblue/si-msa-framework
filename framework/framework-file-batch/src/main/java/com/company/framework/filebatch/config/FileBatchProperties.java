package com.company.framework.filebatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 파일 일괄처리 모듈 설정. 접두사 {@code framework.file-batch}. 선택형 모듈 컨벤션대로 기본 비활성.
 *
 * <pre>
 * framework:
 *   file-batch:
 *     enabled: true              # 모듈 전체 토글(기본 false)
 *     default-parallelism: 16    # 기본 동시 처리 상한(가상스레드 + Semaphore) — BatchOptions 기본값
 * </pre>
 */
@ConfigurationProperties(prefix = "framework.file-batch")
public class FileBatchProperties {

    /** 모듈 전체 활성 여부(기본 false). */
    private boolean enabled = false;

    /** 기본 동시 처리 상한(기본 16). 호출 시 BatchOptions 로 재정의 가능. */
    private int defaultParallelism = 16;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDefaultParallelism() {
        return defaultParallelism;
    }

    public void setDefaultParallelism(int defaultParallelism) {
        this.defaultParallelism = defaultParallelism;
    }
}
