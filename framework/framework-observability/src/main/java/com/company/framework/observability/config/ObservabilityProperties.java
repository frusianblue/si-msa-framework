package com.company.framework.observability.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 관측 모듈 토글/설정.
 *
 * <pre>
 * framework:
 *   observability:
 *     enabled: false           # 마스터 토글 (기본 off)
 *     service:                 # 미설정 시 spring.application.name 사용
 *     env:                     # 미설정 시 활성 프로파일 첫 항목, 없으면 "unknown"
 *     version:                 # 미설정 시 "unknown"
 *     metrics:
 *       common-tags-enabled: true       # 모든 레지스트리에 service/env/version 공통 태그 부여
 *       extra-tags: { region: kr }      # 추가 공통 태그(표준 키 override 가능)
 *       otlp:
 *         enabled: false                # 메트릭을 OTLP(Collector)로 push
 *         url: http://localhost:4318/v1/metrics
 *     logging:
 *       structured:
 *         enabled: false                # Boot 네이티브 구조화(JSON) 로그
 *         format: ecs                   # ecs | logstash | gelf
 *         target: console               # console | file | both
 *     tracing:
 *       otlp:
 *         enabled: false                # 스팬을 OTLP(Collector)로 export (브리지 방식)
 *         endpoint: http://localhost:4318/v1/traces
 *     endpoints:
 *       expose: [health, info, metrics, prometheus]
 *       probes-enabled: true            # k8s liveness/readiness 그룹
 * </pre>
 *
 * <p>대부분의 값은 {@code ObservabilityEnvironmentPostProcessor} 가 로깅/액추에이터 초기화 전에
 * 표준 Boot 프로퍼티(낮은 우선순위)로 풀어준다 — 앱이 직접 설정한 값이 항상 우선한다.
 */
@ConfigurationProperties(prefix = "framework.observability")
public class ObservabilityProperties {

    private boolean enabled = false;
    private String service;
    private String env;
    private String version;

    private final Metrics metrics = new Metrics();
    private final Logging logging = new Logging();
    private final Tracing tracing = new Tracing();
    private final Endpoints endpoints = new Endpoints();

    public static class Metrics {
        private boolean commonTagsEnabled = true;
        private Map<String, String> extraTags = new LinkedHashMap<>();
        private final Otlp otlp = new Otlp();

        public boolean isCommonTagsEnabled() {
            return commonTagsEnabled;
        }

        public void setCommonTagsEnabled(boolean v) {
            this.commonTagsEnabled = v;
        }

        public Map<String, String> getExtraTags() {
            return extraTags;
        }

        public void setExtraTags(Map<String, String> v) {
            this.extraTags = v;
        }

        public Otlp getOtlp() {
            return otlp;
        }

        public static class Otlp {
            private boolean enabled = false;
            private String url = "http://localhost:4318/v1/metrics";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean v) {
                this.enabled = v;
            }

            public String getUrl() {
                return url;
            }

            public void setUrl(String v) {
                this.url = v;
            }
        }
    }

    public static class Logging {
        private final Structured structured = new Structured();

        public Structured getStructured() {
            return structured;
        }

        public static class Structured {
            /** console | file | both */
            public enum Target {
                CONSOLE,
                FILE,
                BOTH
            }

            private boolean enabled = false;
            private String format = "ecs"; // ecs | logstash | gelf
            private Target target = Target.CONSOLE;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean v) {
                this.enabled = v;
            }

            public String getFormat() {
                return format;
            }

            public void setFormat(String v) {
                this.format = v;
            }

            public Target getTarget() {
                return target;
            }

            public void setTarget(Target v) {
                this.target = v;
            }
        }
    }

    public static class Tracing {
        private final Otlp otlp = new Otlp();

        public Otlp getOtlp() {
            return otlp;
        }

        public static class Otlp {
            private boolean enabled = false;
            private String endpoint = "http://localhost:4318/v1/traces";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean v) {
                this.enabled = v;
            }

            public String getEndpoint() {
                return endpoint;
            }

            public void setEndpoint(String v) {
                this.endpoint = v;
            }
        }
    }

    public static class Endpoints {
        private List<String> expose = List.of("health", "info", "metrics", "prometheus");
        private boolean probesEnabled = true;

        public List<String> getExpose() {
            return expose;
        }

        public void setExpose(List<String> v) {
            this.expose = v;
        }

        public boolean isProbesEnabled() {
            return probesEnabled;
        }

        public void setProbesEnabled(boolean v) {
            this.probesEnabled = v;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public Logging getLogging() {
        return logging;
    }

    public Tracing getTracing() {
        return tracing;
    }

    public Endpoints getEndpoints() {
        return endpoints;
    }
}
