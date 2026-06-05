# framework-observability

관측(Observability) 공통 모듈 — **공통 메트릭 태그/네이밍 · 구조화(JSON) 로그 · OTel 익스포터 표준**을
한 군데서 켜고 끈다. 분산추적 토대(`micrometer-tracing-bridge-otel` · `MdcTraceFilter` 의 traceId)는
이미 `framework-core` 에 있으므로, 이 모듈은 그 위에 **표준화 + 익스포트**를 얹는다.

- **새 외부 의존성 0** — 코드가 참조하는 건 micrometer-core/actuator/boot 뿐(전부 core 가 `api` 전이).
  메트릭 레지스트리(prometheus/otlp)·OTel 스팬 익스포터는 **호스트 서비스가 runtimeOnly 로 opt-in**.
- **기본 전부 off** — `framework.observability.enabled=true` 로만 활성(3단 토글 규약).

## 켜기

```gradle
dependencies {
    implementation project(':framework:framework-observability')

    // (선택) Prometheus 스크레이프 → /actuator/prometheus
    runtimeOnly 'io.micrometer:micrometer-registry-prometheus'
    // (선택) 메트릭을 OTLP 로 push 할 때
    // runtimeOnly 'io.micrometer:micrometer-registry-otlp'
    // (선택) 트레이스를 OTLP 로 export 할 때(브리지 방식)
    // runtimeOnly 'io.opentelemetry:opentelemetry-exporter-otlp'
}
```

```yaml
framework:
  observability:
    enabled: true
    env: prod            # 미설정 시 활성 프로파일 첫 항목
    version: "1.0.0"     # 미설정 시 unknown
    metrics:
      common-tags-enabled: true     # 모든 레지스트리에 service/env/version 공통 태그
      extra-tags: { region: kr }
    logging:
      structured:
        enabled: true
        format: ecs                 # ecs | logstash | gelf
        target: console             # console | file | both
    endpoints:
      expose: [health, info, metrics, prometheus]
      probes-enabled: true          # /actuator/health/{liveness,readiness}
```

## 3가지 표준

### 1) 메트릭 공통 태그
`frameworkObservabilityCommonTags`(`MeterRegistryCustomizer`)가 **모든** `MeterRegistry` 에
`service`/`env`/`version`(+`extra-tags`)를 공통 태그로 부여한다. `service` 는 `spring.application.name`,
`env` 는 활성 프로파일에서 자동 채움(명시 값 우선). k8s 라벨/OTel 시맨틱 컨벤션과 정렬되도록 키를 고정.
같은 이름 빈을 직접 등록하면 양보한다(`@ConditionalOnMissingBean`).

### 2) 구조화(JSON) 로그
Boot 4 **네이티브** 구조화 로그(`logging.structured.format`)를 토글로 켠다 — 별도 인코더 라이브러리 불필요.
`target` 에 따라 console/file/둘 다로 출력. ECS 포맷이면 `service.name`(=app name)·`service.environment`·
`service.version` 이 채워지고, `MdcTraceFilter` 의 `traceId`(+ tracing 의 `spanId`)가 MDC 로 함께 실린다.
이 표준값은 로깅 초기화 이전에 `ObservabilityEnvironmentPostProcessor` 가 심으며 **앱이 직접 준 값이 우선**.

### 3) OTel 익스포터 (기본 off)
- **메트릭 → OTLP**: `framework.observability.metrics.otlp.{enabled,url}` →
  `management.otlp.metrics.export.{enabled,url}`. `micrometer-registry-otlp` 필요.
  ⚠️ OTel **메트릭** 브리지를 함께 쓰면 메트릭이 중복될 수 있으니 한쪽만 사용.
- **트레이스 → OTLP (브리지 방식, core 와 일치)**: `framework.observability.tracing.otlp.{enabled,endpoint}` →
  `management.otlp.tracing.endpoint`. `opentelemetry-exporter-otlp` 필요.
  - 신규 공식 스타터(`spring-boot-starter-opentelemetry`)를 쓰는 프로젝트라면 키가
    `management.opentelemetry.tracing.export.otlp.endpoint` 이므로, 그 경우엔 이 토글 대신 직접 설정한다.


## 실전 사용 예 (코드)

공통 태그(서비스/환경)는 자동 부착된다. **커스텀 메트릭**은 표준 Micrometer `MeterRegistry` 를 주입해 만든다.
```java
// io.micrometer.core.instrument.{MeterRegistry, Counter}
private final Counter loginFailures;
public AuthMetrics(MeterRegistry registry) {
    this.loginFailures = registry.counter("auth.login.failures", "reason", "bad_credentials");
}
public void onFail() { loginFailures.increment(); }
```
공통 태그를 추가/덮어쓰려면 `MeterRegistryCustomizer` 빈을 등록:
```java
@Bean MeterRegistryCustomizer<MeterRegistry> teamTag() {
    return registry -> registry.config().commonTags("team", "payments");
}
```
로그 MDC 태그는 `MDC.put("orderId", id)` 로 추가하면 구조화(JSON) 로그 필드로 출력된다.

## k8s
`deploy/k8s/observability.yaml` 에 ServiceMonitor(Prometheus Operator)·annotation 스크레이프·
액추에이터 노출·health 프로브 샘플이 있다.

## 토글 한눈에
| 프로퍼티 | 기본 | 효과 |
|---|---|---|
| `framework.observability.enabled` | `false` | 마스터 |
| `…metrics.common-tags-enabled` | `true` | 공통 태그 부여 |
| `…metrics.otlp.enabled` | `false` | 메트릭 OTLP push |
| `…logging.structured.enabled` | `false` | JSON 로그 |
| `…tracing.otlp.enabled` | `false` | 트레이스 OTLP export |
| `…endpoints.probes-enabled` | `true` | k8s 프로브 그룹 |
