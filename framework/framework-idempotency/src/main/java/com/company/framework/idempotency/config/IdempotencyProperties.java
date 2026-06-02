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

    private final Replay replay = new Replay();

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

    public static class Replay {
        /**
         * 응답 재생 활성(기본 false=레거시 409). true 면 완료된 동일 키 요청에 저장된 응답을 그대로 재생하고,
         * 처리중이면 409. 활성 시 IdempotencyResponseFilter 가 등록되어 응답을 버퍼링(헤더 있는 요청만).
         */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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

    public Replay getReplay() {
        return replay;
    }
}
