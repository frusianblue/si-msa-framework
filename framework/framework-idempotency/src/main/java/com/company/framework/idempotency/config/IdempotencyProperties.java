package com.company.framework.idempotency.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 멱등성 모듈 토글.
 * application.yml:
 *   framework:
 *     idempotency:
 *       enabled: true              # 2단 토글: 기본 false(선택형이라 명시적 on 필요)
 *       header: Idempotency-Key    # 멱등키를 읽을 요청 헤더
 *       ttl: PT10M                 # 처리 결과 보관 시간
 *       store:
 *         type: redis              # 3단 토글: memory | redis | jdbc (상호배제)
 */
@ConfigurationProperties(prefix = "framework.idempotency")
public class IdempotencyProperties {

    /** 모듈 활성 여부(선택형 기본값 = false). */
    private boolean enabled = false;

    /** 멱등키를 담는 요청 헤더명. */
    private String header = "Idempotency-Key";

    /** 동일 키 결과 보관 기간(TTL). */
    private Duration ttl = Duration.ofMinutes(10);

    private final Store store = new Store();

    public static class Store {
        /** memory(기본·단일인스턴스) | redis(다중인스턴스 공유) | jdbc(영속). */
        private String type = "memory";

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Store getStore() {
        return store;
    }
}
