# framework-client (외부 API 표준 호출)

Spring Boot 4 `RestClient` 기반 표준 호출. **타임아웃 · 재시도 · 서킷브레이커 · 연계로그 · 트레이스 전파**를
인터셉터로 묶어 `frameworkRestClientBuilder` 하나로 제공한다. **새 외부 의존성 없음**(서킷브레이커 자체 구현).

## 켜는 법
`build.gradle`:
```gradle
dependencies { implementation project(':framework:framework-client') }
```
`application.yml`:
```yaml
framework:
  client:
    enabled: true
    connect-timeout: 2s
    read-timeout: 5s
    retry:           { enabled: true, max-attempts: 2, backoff: 200ms, multiplier: 2.0 }
    circuit-breaker: { enabled: true, failure-threshold: 5, wait-duration: 10s }
    logging:         { enabled: true, include-headers: false }
    trace:           { enabled: true }
```

## 쓰는 법
```java
private final RestClient client;

public MyClient(RestClient.Builder frameworkRestClientBuilder) {
    this.client = frameworkRestClientBuilder.baseUrl("https://api.partner.com").build();
}

PartnerDto get(String id) {
    return client.get().uri("/items/{id}", id).retrieve().body(PartnerDto.class);
}
```
- 호출마다 타임아웃 적용, 멱등 메서드는 지정 5xx/IO 오류 시 백오프 재시도, 호스트별 서킷브레이커 동작.
- MDC `traceId` 가 `X-Trace-Id` 로 하위 호출에 전파됨(게이트웨이→서비스→외부 추적 일관).
- 연계로그: `framework.client.integration` 로거에 `[OUT] METHOD URI -> status (ms)`.

## 동작 상세
- **인터셉터 순서**(바깥→안): Trace → CircuitBreaker → Retry → Logging.
  서킷이 재시도를 감싸므로 "논리적 1회 호출" 단위로 실패가 집계되고, OPEN 이면 재시도 전에 즉시 차단.
- **재시도 대상 메서드**: GET/HEAD/PUT/DELETE/OPTIONS(기본). POST 는 부작용 방지로 기본 제외.
- **서킷 차단 시**: `CircuitOpenException`(IOException 계열) 발생 → 호출부에서 폴백 처리.
- **민감 헤더**(Authorization/Cookie 등)는 로그에서 `***` 로 마스킹.

## 끄기 / override
- 전체: `framework.client.enabled:false` 또는 의존성 제거.
- 기능별: `retry.enabled` / `circuit-breaker.enabled` / `logging.enabled` / `trace.enabled` 개별 off.
- 더 정교한 서킷이 필요하면 프로젝트가 `frameworkRestClientBuilder` 빈을 직접 등록(예: Resilience4j 인터셉터 장착)하면
  `@ConditionalOnMissingBean` 으로 프레임워크 기본이 양보한다.
- 새 외부 라이브러리 없음 → `libs.versions.toml`/`STACK.md` 변경 불필요.
