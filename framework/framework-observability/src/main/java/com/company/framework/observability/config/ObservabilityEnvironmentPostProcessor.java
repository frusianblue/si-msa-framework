package com.company.framework.observability.config;

import com.company.framework.observability.config.ObservabilityProperties.Logging.Structured;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 관측 표준값을 "로깅/액추에이터 초기화 이전"에 Boot 표준 프로퍼티로 풀어주는 EnvironmentPostProcessor.
 *
 * <p>왜 빈이 아니라 EPP 인가: 구조화 로그({@code logging.structured.*})는 로깅 시스템이
 * ApplicationContext 보다 먼저 읽으므로 빈으로는 늦다. EPP 는 {@code ConfigData}(application.yml) 적재
 * 이후·로깅 초기화 이전에 실행되므로 여기서 기본값을 심는다.
 *
 * <p>안전장치: {@code framework.observability.enabled=true} 일 때만 동작하고, 기본값은
 * {@code addLast}(최저 우선순위)로 추가하므로 <b>앱이 직접 설정한 값이 항상 우선</b>한다.
 *
 * <p>등록은 {@code META-INF/spring.factories} 의 {@code EnvironmentPostProcessor} 키로 한다
 * (오토컨피그 {@code .imports} 가 아님 — 컨텍스트 이전에 동작하므로).
 */
public class ObservabilityEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String SOURCE_NAME = "frameworkObservabilityDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        ObservabilityProperties props = Binder.get(environment)
                .bind("framework.observability", ObservabilityProperties.class)
                .orElseGet(ObservabilityProperties::new);

        if (!props.isEnabled()) {
            return;
        }

        Map<String, Object> defaults = new LinkedHashMap<>();

        // 1) 구조화(JSON) 로그 — Boot 네이티브 (ecs|logstash|gelf)
        Structured structured = props.getLogging().getStructured();
        if (structured.isEnabled()) {
            String format = structured.getFormat();
            Structured.Target target = structured.getTarget();
            if (target == Structured.Target.CONSOLE || target == Structured.Target.BOTH) {
                defaults.put("logging.structured.format.console", format);
            }
            if (target == Structured.Target.FILE || target == Structured.Target.BOTH) {
                defaults.put("logging.structured.format.file", format);
            }
            // ECS 전용 service 필드. service.name 은 spring.application.name 에서 Boot 가 자동 채움.
            if ("ecs".equalsIgnoreCase(format)) {
                if (notBlank(props.getEnv())) {
                    defaults.put("logging.structured.ecs.service.environment", props.getEnv());
                }
                if (notBlank(props.getVersion())) {
                    defaults.put("logging.structured.ecs.service.version", props.getVersion());
                }
            }
        }

        // 2) 액추에이터 노출 + k8s liveness/readiness 프로브 그룹
        List<String> expose = props.getEndpoints().getExpose();
        if (expose != null && !expose.isEmpty()) {
            defaults.put("management.endpoints.web.exposure.include", String.join(",", expose));
        }
        if (props.getEndpoints().isProbesEnabled()) {
            defaults.put("management.endpoint.health.probes.enabled", Boolean.TRUE);
        }

        // 3) 메트릭 OTLP push (기본 off). 켜면 호스트가 micrometer-registry-otlp 를 runtimeOnly 로 추가해야 함.
        if (props.getMetrics().getOtlp().isEnabled()) {
            defaults.put("management.otlp.metrics.export.enabled", Boolean.TRUE);
            defaults.put(
                    "management.otlp.metrics.export.url",
                    props.getMetrics().getOtlp().getUrl());
        }

        // 4) 트레이스 OTLP export (기본 off, 브리지 방식 키).
        //    core 의 micrometer-tracing-bridge-otel 에 대응. 호스트가 opentelemetry-exporter-otlp 를 추가해야 export 됨.
        //    신규 공식 스타터(spring-boot-starter-opentelemetry)를 쓰면 키가
        //    management.opentelemetry.tracing.export.otlp.endpoint 이므로 그 경우엔 이 토글 대신 직접 설정(README 참고).
        if (props.getTracing().getOtlp().isEnabled()) {
            defaults.put(
                    "management.otlp.tracing.endpoint",
                    props.getTracing().getOtlp().getEndpoint());
        }

        if (!defaults.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, defaults));
        }
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    @Override
    public int getOrder() {
        // ConfigData(application.yml) 적재 이후에 실행 → 앱 값 읽기/우선 보장. (로깅 초기화 이전은 리스너 단계가 보장)
        return Ordered.LOWEST_PRECEDENCE;
    }
}
