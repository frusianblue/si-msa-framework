package com.company.framework.client.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 외부 API 표준 호출 토글/설정.
 * framework:
 *   client:
 *     enabled: true
 *     connect-timeout: 2s
 *     read-timeout: 5s
 *     retry: { enabled: true, max-attempts: 2, backoff: 200ms, multiplier: 2.0 }
 *     circuit-breaker: { enabled: true, failure-threshold: 5, wait-duration: 10s, half-open-max-calls: 1 }
 *     logging: { enabled: true, include-headers: false }
 *     trace: { enabled: true }
 */
@ConfigurationProperties(prefix = "framework.client")
public class ClientProperties {

    private boolean enabled = false;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);

    private final Retry retry = new Retry();
    private final CircuitBreakerProps circuitBreaker = new CircuitBreakerProps();
    private final Logging logging = new Logging();
    private final Trace trace = new Trace();

    public static class Retry {
        private boolean enabled = true;
        private int maxAttempts = 2; // 최초 1회 외 추가 재시도 횟수
        private Duration backoff = Duration.ofMillis(200);
        private double multiplier = 2.0;
        private List<String> methods = List.of("GET", "HEAD", "PUT", "DELETE", "OPTIONS");
        private List<Integer> retryOnStatus = List.of(502, 503, 504);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean v) {
            this.enabled = v;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int v) {
            this.maxAttempts = v;
        }

        public Duration getBackoff() {
            return backoff;
        }

        public void setBackoff(Duration v) {
            this.backoff = v;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(double v) {
            this.multiplier = v;
        }

        public List<String> getMethods() {
            return methods;
        }

        public void setMethods(List<String> v) {
            this.methods = v;
        }

        public List<Integer> getRetryOnStatus() {
            return retryOnStatus;
        }

        public void setRetryOnStatus(List<Integer> v) {
            this.retryOnStatus = v;
        }
    }

    public static class CircuitBreakerProps {
        private boolean enabled = true;
        private int failureThreshold = 5;
        private Duration waitDuration = Duration.ofSeconds(10);
        private int halfOpenMaxCalls = 1;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean v) {
            this.enabled = v;
        }

        public int getFailureThreshold() {
            return failureThreshold;
        }

        public void setFailureThreshold(int v) {
            this.failureThreshold = v;
        }

        public Duration getWaitDuration() {
            return waitDuration;
        }

        public void setWaitDuration(Duration v) {
            this.waitDuration = v;
        }

        public int getHalfOpenMaxCalls() {
            return halfOpenMaxCalls;
        }

        public void setHalfOpenMaxCalls(int v) {
            this.halfOpenMaxCalls = v;
        }
    }

    public static class Logging {
        private boolean enabled = true;
        private boolean includeHeaders = false;
        private List<String> maskedHeaders = List.of("Authorization", "Cookie", "Set-Cookie", "Proxy-Authorization");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean v) {
            this.enabled = v;
        }

        public boolean isIncludeHeaders() {
            return includeHeaders;
        }

        public void setIncludeHeaders(boolean v) {
            this.includeHeaders = v;
        }

        public List<String> getMaskedHeaders() {
            return maskedHeaders;
        }

        public void setMaskedHeaders(List<String> v) {
            this.maskedHeaders = v;
        }
    }

    public static class Trace {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean v) {
            this.enabled = v;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration v) {
        this.connectTimeout = v;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration v) {
        this.readTimeout = v;
    }

    public Retry getRetry() {
        return retry;
    }

    public CircuitBreakerProps getCircuitBreaker() {
        return circuitBreaker;
    }

    public Logging getLogging() {
        return logging;
    }

    public Trace getTrace() {
        return trace;
    }
}
