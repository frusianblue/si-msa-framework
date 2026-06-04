# framework-core

모든 서비스의 공통 바닥 **[코어]**. 표준 응답/예외·페이징·traceId·로깅·AOP·XSS·로컬 캐시·AES 암호화·설정값 암호화(`ENC(...)`)를 자동 적용한다. 다른 모듈이 모두 이 위에 선다.

## 켜는 법
`framework-core` 는 [코어]라 별도 토글 없이 **항상 탑재**된다(모든 서비스가 직접/전이 의존). 세부 기능만 `application.yml` 로 끈다.

```yaml
framework:
  core:
    trace: true                 # traceId(MDC) 부여 + 응답 헤더 노출 (MdcTraceFilter)
    http-logging: true          # 요청/응답 로깅 (HttpLoggingFilter)
    xss: true                   # XSS 입력 필터 (XssRequestFilter)
    execution-time-aspect: true # 실행시간 로깅 AOP
    audit-aspect: true          # 감사 로깅 AOP
  crypto:
    enabled: true
    aes-secret: ${AES_SECRET}   # AES-GCM 마스터키(운영은 평문 주입 — 자기 자신은 ENC() 불가)
    config-decryption:
      enabled: true             # yaml 의 ENC(...) 값 기동 시 자동 복호화
  cache:
    enabled: true
    spec: "maximumSize=10000,expireAfterWrite=10m"   # Caffeine 로컬 캐시(파드 간 공유는 framework-cache-redis)
```
모든 토글 기본값 `true`(crypto/cache 포함). 끄려는 것만 `false`.

## 쓰는 법

**표준 응답 / 예외**
```java
return ApiResponse.ok(dto);                     // { "data": ..., "traceId": ... }
throw new BusinessException(ErrorCode.NOT_FOUND); // GlobalExceptionHandler 가 통일 변환
```
**페이징**
```java
PageResponse<UserDto> page = ...;   // PageRequest / SearchCondition 로 표준 요청
```
**설정값 암호화** — 토큰 생성: `AES_SECRET=... java -cp <cp> com.company.framework.core.crypto.CryptoCli encrypt '평문'` → `ENC(...)` 출력. 상세 [`../../docs/reference/ENCRYPTION_GUIDE.md`](../../docs/reference/ENCRYPTION_GUIDE.md).
**비동기** — `spring.threads.virtual.enabled=true` + `@Async` 가상스레드 executor(`AsyncConfig`).

> `AesCryptoService`(AES-GCM)는 다른 모듈(mybatis 타입핸들러 등)이 재사용한다. `util` 패키지(검증·마스킹·날짜/영업일·금액·한글·해시·JSON·CSV·고정폭전문·CP949)는 토글 없는 정적 유틸.

## 끄는 법
[코어]라 통째로 끄지 않는다. 개별 기능만 위 토글로 비활성(런타임 비용 제거).

## 덮어쓰기(프로젝트 커스텀)
`JacksonConfig`·`CacheManager` 등은 프로젝트가 동일 타입 빈을 등록하면 `@ConditionalOnMissingBean` 으로 양보한다.

## 버전 관리
Caffeine·micrometer-tracing-bridge-otel 등은 Boot BOM/`gradle/libs.versions.toml` 로 관리. 신규 라이브러리 추가 시 `STACK.md` 갱신.
