# framework-secure-web

선택형 **웹 보안 필터** 모듈. 서블릿 필터 계층에서 시큐어코딩/보안성 심의 공통 항목을 보강한다.
(XSS 본문 이스케이프는 `framework-core` 담당. 본 모듈은 그 외 항목.)

> 거부 응답 JSON 은 Jackson 에 의존하지 않고 `SecureWebResponder` 가 수기로 표준
> `ApiResponse.fail` 형태(`{success,code,message,timestamp}`)를 직접 기록한다. 필터에서 던진 예외는
> `GlobalExceptionHandler`(@RestControllerAdvice)가 처리하지 못하기 때문이다.

## 토글 (3단)

1. **모듈**: 의존성 추가 + 서블릿 웹앱(`@ConditionalOnClass(OncePerRequestFilter)`).
2. **기능**: `framework.secure-web.enabled=true`.
3. **필터별 세부 토글**(아래).

| 필터 | 기본 | 토글 | `@Order` |
|---|---|---|---|
| CORS | off | `cors.enabled` | HIGHEST |
| RateLimit | off | `rate-limit.enabled` | HIGHEST+1 |
| PathTraversal | **on** | `path-traversal.enabled` | HIGHEST+1 |
| Injection | off | `injection.enabled` | HIGHEST+2 |
| CSRF | off | `csrf.enabled` | HIGHEST+3 |
| SecurityHeaders | **on** | `headers.enabled` | HIGHEST+4 |
| (core) XSS | — | — | HIGHEST+5 |

CORS 프리플라이트(OPTIONS)는 CORS 필터가 가장 먼저 처리하므로 RateLimit/스크리닝 필터에 막히지 않는다.

## CORS (직접 노출 서비스 보조)

**브라우저 진입점이 게이트웨이라면 CORS 는 게이트웨이 `globalcors` 에서 1차로 처리**하고 이 모듈은 켜지
않는다(중복/충돌 방지). 게이트웨이를 거치지 않고 서비스를 직접 노출하는 경우에만 보조로 켠다.

구현은 Spring 의 `CorsFilter`(+`UrlBasedCorsConfigurationSource`)에 위임한다(표준 기능 재발명 금지).

```yaml
framework:
  secure-web:
    enabled: true
    cors:
      enabled: true
      path-pattern: /**
      allowed-origin-patterns: ["https://*.yourdomain.com"]  # credentials 시 origins 대신 이쪽
      allowed-methods: [GET, POST, PUT, PATCH, DELETE, OPTIONS]
      allowed-headers: ["*"]
      allow-credentials: true
      max-age-seconds: 3600
```

주의: `allow-credentials: true` 일 때 origin 에 `"*"` 사용 불가 → `allowed-origin-patterns` 를 쓴다.

## RateLimit (인스턴스 로컬 안전망)

**전역 레이트리밋은 게이트웨이의 Redis 기반 `RequestRateLimiter` 가 담당**한다. 이 필터는 토큰버킷을
JVM 메모리에 두는 **파드 단위** 보조 방어선이다. K8s 다중 레플리카에서는 한도가 파드 수만큼 곱해지므로
"전역 한도" 용도로 쓰지 말 것.

```yaml
framework:
  secure-web:
    enabled: true
    rate-limit:
      enabled: true
      capacity: 20              # 버스트 허용량
      refill-per-second: 10     # 초당 평균 허용률
      requested-tokens: 1       # 요청당 소비 토큰(가중치)
      key-strategy: PRINCIPAL_OR_IP   # IP | PRINCIPAL | PRINCIPAL_OR_IP
      trust-forwarded-for: false      # 프록시/게이트웨이 뒤에서만 true(스푸핑 방지)
      include-paths: []         # 비어 있으면 전체. Ant 패턴
      exclude-paths: ["/actuator/**"]
      max-entries: 100000       # 키별 버킷 상한(메모리 가드)
      idle-eviction-seconds: 600
      include-retry-after: true # 429 시 Retry-After 헤더
```

초과 시 표준 `ApiResponse.fail` 형태의 **429**(`code=E0429`)를 반환한다. 메모리 가드: 버킷 수가
`max-entries` 를 넘으면 유휴 버킷을 정리하고, 그래도 넘치면 새 키는 **fail-open**(허용)으로 처리해
가용성을 우선한다(보조 방어선이므로). 일반 API 레이트리밋이며, 로그인 시도 제한(ISMS-P)과는 별개 계층이다.

## 나머지 필터

- **SecurityHeaders**(기본 on): X-Frame-Options, X-Content-Type-Options, Referrer-Policy, CSP,
  Permissions-Policy, HSTS(HTTPS 한정). Spring Security 보안헤더와 중복 주의.
- **PathTraversal**(기본 on): `../`, 인코딩 우회, 널바이트 차단.
- **Injection**(기본 off): SQLi 시그니처 스크리닝. 오탐 가능 → 진짜 방어는 파라미터화 쿼리(MyBatis `#{}`).
  `mode=block|log-only`.
- **CSRF**(기본 off): 더블서브밋 쿠키. stateless JWT 면 보통 불필요.


## 실전 사용 예 (코드)

직접 노출되는 서비스에 보안 필터(주입 스크리닝·경로조작 차단·CSRF·보안헤더·레이트리밋)를 더한다. 보통 코드 변경 없이 토글로 켜고 **차단 동작은 curl 로 확인**한다.
```yaml
framework.secure-web:
  enabled: true
  injection: { mode: BLOCK }     # 의심 패턴 차단(LOG 로 관찰만도 가능)
  path-traversal: { enabled: true }
  rate-limit: { enabled: true, permits-per-minute: 120 }
  headers: { enabled: true }
```
```bash
# 주입 패턴 → 차단(400/403)
curl -i "http://localhost:8080/api/v1/search?q=1';DROP%20TABLE%20users;--"
# 경로조작 → 차단
curl -i "http://localhost:8080/api/v1/files/..%2f..%2fetc%2fpasswd"
# 레이트리밋 초과 → 429
for i in $(seq 1 200); do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/ping; done | sort | uniq -c
```

## 끄는 법
```yaml
framework.secure-web.enabled: false   # 마스터 off(기본값, opt-in) — 모든 보안 필터 미등록
# 또는 필요한 필터만 개별 off
framework.secure-web.injection.enabled:      false
framework.secure-web.path-traversal.enabled: false
framework.secure-web.rate-limit.enabled:     false
framework.secure-web.cors.enabled:           false
framework.secure-web.csrf.enabled:           false
framework.secure-web.headers.enabled:        false
```
마스터를 끄면 이 모듈의 필터가 전부 미등록된다(게이트웨이 1선 방어만 남음). 직접 노출 서비스는 마스터 on + 필요한 서브 토글만 조정한다.

## 의존성

새 외부 의존성 0. CORS/RateLimit 모두 호스트가 제공하는 web 스택만 사용(`compileOnly` web).
RateLimit 은 순수 JVM 토큰버킷이라 Redis 등 외부 의존이 없다(분산 한도가 필요하면 게이트웨이/Redis 사용).
