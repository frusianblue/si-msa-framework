# framework-context — 요청 컨텍스트 / 멀티테넌시

요청 단위로 **tenantId / userId / locale**(및 임의 부가속성)를 컨텍스트로 바인딩하고, 비동기 작업과
아웃바운드 호출로 **명시적으로 전파**하는 선택형 모듈. 선택형 모듈 컨벤션대로 **기본 비활성**이며 서블릿 웹 앱에서만 동작.

새 외부 의존성 0 — servlet/web 은 호스트 앱이 제공(`compileOnly`).

## 구성요소

| 클래스 | 역할 |
|--------|------|
| `RequestContext` | 불변 값 객체(JDK 단독). 고정필드 + 확장용 `attributes` 맵. |
| `ContextHolder` | 정적 ThreadLocal 다리. **상속형 아님** — 전파는 항상 명시적. |
| `ContextResolver` / `HeaderContextResolver` | 해소 전략(기본=헤더). 앱이 빈 재정의 시 우선. |
| `ContextBindingFilter` | 요청마다 바인딩/정리(+MDC). `MdcTraceFilter` 바로 안쪽 순서. |
| `ContextTaskDecorator` | `@Async`/풀에 컨텍스트+MDC 전파. |
| `ContextPropagationInterceptor` | 아웃바운드 호출에 컨텍스트 헤더 전파. |

## 켜는 법

```yaml
framework:
  context:
    enabled: true                 # 기본 false
    tenant-header: X-Tenant-Id
    user-header: X-User-Id
    put-to-mdc: true              # tenantId/userId 를 MDC 에 → 로그 자동 노출
    mdc-tenant-key: tenantId
    mdc-user-key: userId
    propagate-downstream: true    # 아웃바운드 헤더 전파 인터셉터 등록
```

## 사용

```java
// 읽기: 어디서든 현재 요청의 식별정보(미바인딩이면 EMPTY, null 아님)
RequestContext ctx = ContextHolder.get();
String tenant = ctx.tenantId();

// 해소 전략 교체(예: JWT/SecurityContext) — 빈만 정의하면 기본 헤더 리졸버를 대체
@Bean
ContextResolver contextResolver() {
    return req -> RequestContext.builder()
            .tenantId(jwt.getClaim("tenant"))
            .userId(jwt.getSubject())
            .build();
}

// @Async 전파: core 의 가상 스레드 실행기에 데코레이터 연결
//   (core AsyncConfig 의 TaskExecutorAdapter 에 setTaskDecorator)
executor.setTaskDecorator(contextTaskDecorator);

// 아웃바운드 전파: RestClient/RestTemplate 인터셉터에 추가
RestClient.builder().requestInterceptor(contextPropagationInterceptor).build();
```


## 실전 사용 예 (코드)

요청 단위 컨텍스트(테넌트/사용자/로케일)는 어디서든 `ContextHolder.get()` 으로 읽는다(필터가 비동기·하위호출까지 전파).
```java
// com.company.framework.context.{ContextHolder, RequestContext}
public List<Order> myTenantOrders() {
    RequestContext ctx = ContextHolder.get();
    String tenant = ctx.tenantId();          // 멀티테넌시 분기
    String user   = ctx.userId();
    return orderMapper.findByTenant(tenant);
}
```
헤더 매핑이 아닌 커스텀 추출이 필요하면 `ContextResolver` 를 구현해 빈으로 등록(예: JWT 클레임에서 테넌트 추출):
```java
@Component
public class JwtTenantContextResolver implements ContextResolver {
    @Override public RequestContext resolve(HttpServletRequest req) {
        String tenant = (String) req.getAttribute("jwt.tenant");
        return RequestContext.builder().tenantId(tenant).build();
    }
}
```

## 주의 / 함정

- **상속형 ThreadLocal 을 쓰지 않는다.** 가상 스레드/풀에서 누수가 나므로 전파는 데코레이터·인터셉터로 명시한다.
  필터는 요청 종료 시(예외 포함) 컨텍스트와 자기 MDC 키를 반드시 정리한다.
- **헤더 신뢰 경계**: `HeaderContextResolver` 는 신뢰 헤더를 읽기만 한다. 외부 경계의 위조 방지는
  게이트웨이/인증 계층 책임 — 내부 서비스 간 신뢰 헤더 전제.
- **Boot 구조화 로깅(JSON)**: MDC 키는 그대로 실리지만, 패턴 기반 출력이 아니면 키 노출 방식은 로깅 설정에 따른다.
- `framework.context.enabled=false`(기본)면 빈이 전혀 등록되지 않아 무비용.
