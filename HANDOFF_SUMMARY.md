# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**framework-secure-web** (선택형 신규 모듈) 을 만들었다 — 보안 응답 헤더·경로조작 차단·인젝션 스크리닝·CSRF 더블서브밋을 **필터 계층**에서 표준 제공. XSS 본문 이스케이프는 기존대로 framework-core 담당. **새 외부 의존성 0**(core 의 starter-web/Jackson 만 사용), 3단 토글 규약 그대로.

## 최종 갱신
- 일자: 2026-05-31 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1

## 무엇을 했나 (Done)
- **신규 모듈 `framework/framework-secure-web/`** ([선택], `framework.secure-web.enabled=false` 기본). 의존성 `api project(':framework:framework-core')` 뿐 — core 가 spring-web(OncePerRequestFilter)+Jackson(ObjectMapper)+ApiResponse/ErrorCode 를 이미 노출.
- **필터 4종**(모두 `@Order(HIGHEST_PRECEDENCE+n)`, core XSS(+5)보다 앞):
  - `PathTraversalFilter`(+1, 기본 on) — URI/쿼리의 `..`·널바이트·백슬래시·(다중)인코딩 우회 탐지 시 400.
  - `InjectionScreeningFilter`(+2, **기본 off**) — 쿼리/파라미터 값의 SQLi 시그니처 스크리닝. `mode=BLOCK|LOG_ONLY`, `exclude-paths`, `additional-patterns`. JSON 본문은 의도적 제외(파라미터화 쿼리가 본 방어).
  - `DoubleSubmitCsrfFilter`(+3, **기본 off**) — Spring CSRF 와 독립적인 더블서브밋 쿠키. 안전메서드엔 쿠키 발급(HttpOnly 미설정), 비안전메서드엔 헤더==쿠키 검증, 불일치 403. `protect-paths`/`exclude-paths`, SameSite/Secure 설정. `constantTimeEquals` 사용.
  - `SecurityHeadersFilter`(+4, 기본 on) — X-Frame-Options/X-Content-Type-Options(nosniff)/Referrer-Policy/CSP/Permissions-Policy/HSTS(HTTPS 한정).
- **표준 거부 응답**: 필터는 디스패처 이전이라 GlobalExceptionHandler 가 못 잡으므로 `SecureWebResponder` 가 `ApiResponse.fail(code,msg)` JSON 을 직접 기록(컨트롤러 에러와 동일 포맷).
- **보조**: `PathSupport`(컨텍스트패스 제거 상대경로·AntPathMatcher 매칭·로그 CRLF 방지).

## 현재 상태 (적용/검증)
- 변경/신규 파일 모두 repo 반영. `framework-secure-web` 를 `settings.gradle` 에 등록 완료(누락 시 `project not found`).
- 정합성 점검 통과: core API 시그니처 일치, 괄호/중괄호 균형 OK(InjectionScreeningFilter 의 paren +3 은 정규식 리터럴 `\(` 때문, 코드 자체는 균형).
- ⚠️ **실제 gradle 컴파일 미검증**(작성 환경 Maven Central 차단). 받는 쪽에서:
  - `./gradlew :framework:framework-secure-web:compileJava`
  - 권장: `./gradlew spotlessApply`.
- DB/인프라 변경 없음(순수 서블릿 필터). 새 SQL 없음.

## 켜는 법 (application.yml)
```yaml
framework:
  secure-web:
    enabled: true                 # 2단 토글(선택형)
    headers: { enabled: true, frame-options: DENY, content-security-policy: "default-src 'self'" }
    path-traversal: { enabled: true }
    injection: { enabled: true, mode: log-only }      # 우선 log-only 로 오탐 관찰 후 block 전환 권장
    csrf: { enabled: true, protect-paths: [/api/admin/**] }  # 쿠키인증/폼 보호 경로만
```

## 바로 다음 할 일 (Next)
1. 받는 쪽에서 **secure-web 컴파일 확인** + `spotlessApply`. injection 은 운영 전 `log-only` 로 오탐 튜닝.
2. **금융 핵심** — **framework-messaging**(Kafka + Outbox; audit 의 kafka 싱크는 여기 도착 후 연결), **framework-datasource**(읽기/쓰기 분리).
3. 이후: excel · batch · notification → 규제특화(pki/mfa/hsm/recon/egov) → observability → 게이트웨이/k8s/CI-CD 멀티서비스화. (상세 순서는 `docs/FRAMEWORK_MODULES.md` 4절)

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **⚠️ 이 스택은 Jackson 3 (`tools.jackson.*`) 이다.** `com.fasterxml.jackson.core/databind` import 금지(클래스패스에 없음) — 특히 `com.fasterxml.jackson.databind.ObjectMapper` 는 컴파일 에러. 예외: 애너테이션(`@JsonInclude` 등)은 Jackson 3 에서도 여전히 `com.fasterxml.jackson.annotation` 패키지라 OK. Jackson 3 쓸 일 있으면 `tools.jackson.databind.*`(매퍼는 `JsonMapper`, 커스터마이저는 `JsonMapperBuilderCustomizer`) 사용. **필터/인터셉터 등 인프라 레벨의 단순 JSON 응답은 Jackson 빈 주입 대신 수기 직렬화**(SecureWebResponder 처럼)가 버전·빈주입에 안 묶여 견고함. (STACK.md 71 참조)
- **"core 가 노출하니 전이로 다 된다" 가정 주의.** spring-web/servlet/spring-core 는 core 의 `api` 로 전이되지만, 모듈이 직접 쓰는 web 계열은 `compileOnly 'spring-boot-starter-web'` 로 명시하는 편이 안전(audit/secure-web 패턴).
- **필터에서 BusinessException 던지지 말 것.** 디스패처 이전 필터의 예외는 `@RestControllerAdvice`(GlobalExceptionHandler)가 처리 못 함 → `SecureWebResponder` 로 표준 JSON 을 직접 기록한다.
- **CSRF 는 Spring Security 와 독립.** 보안 체인이 `csrf().disable()`(stateless JWT)이므로, 본 모듈의 더블서브밋은 그것과 충돌하지 않는 자체 구현. Spring CSRF 를 다시 켜서 중복시키지 말 것.
- **CSRF 쿠키는 HttpOnly 금지.** 더블서브밋은 JS 가 쿠키를 읽어 헤더로 재전송해야 하므로 의도적으로 HttpOnly 를 안 건다. SameSite=None 쓸 거면 Secure 필수.
- **InjectionScreeningFilter 는 오탐 가능 → 기본 off, 운영은 log-only 부터.** JSON 본문은 검사 안 함(스트림 버퍼링 회피). 파라미터 조회(getParameterMap)는 컨테이너 캐시라 다운스트림 @RequestBody/getParameter 에 영향 없음.
- **보안 헤더 중복 주의.** Spring Security 기본 헤더 기능과 본 모듈을 동시에 켜면 일부 헤더가 중복될 수 있음 → 둘 중 하나만.
- **필터 순서**: path(+1)→injection(+2)→csrf(+3)→headers(+4)→core xss(+5). 모두 `@Order(Ordered.HIGHEST_PRECEDENCE+n)`. Spring Security 체인(~-100)보다 앞서 실행됨.
- (기존) **새 모듈은 `settings.gradle` 등록 필수** · **새 외부 의존성 0 유지**(libs.versions.toml/STACK.md 무변경) · bash 중괄호 확장 `{a,b}` 미동작 → `for` 루프.

## 모듈 추가 레시피 (검증된 반복 절차)
1. `framework/framework-<X>/` 생성: `config`(Properties+AutoConfiguration) · 도메인 패키지 · `resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`(FQCN 등록).
2. `build.gradle`: `api project(':framework:framework-core')`(+필요 시 다른 framework 모듈) + starter 는 `compileOnly`(이미 core 가 노출하면 생략). 새 버전 의존성 지양.
3. **`settings.gradle` 에 include 추가**(잊지 말 것).
4. 코드 작성 전 **Boot 4/Spring 7 변경 API 확인**(특히 `HttpHeaders`, `boot.http.client`, `RestClient`/`RestTemplate` 위치, starter-aop→starter-aspectj, **Jackson 3 `tools.jackson.*`**).
5. 오토컨피그: `@AutoConfiguration` + `@ConditionalOnClass(모듈마커)` + `@ConditionalOnProperty(framework.<x>.enabled=true)` + 빈은 `@ConditionalOnMissingBean`. 3단 impl 은 `store.type`/세부 토글로 분기.
6. 검증: `./gradlew :framework:framework-<X>:compileJava` (Configuration Cache 꼬이면 `--no-configuration-cache` 또는 `clean`).
7. 드롭인 배포: 모듈 폴더 + 변경 파일 + **완성 `settings.gradle`** 을 한 zip 에 담아 루트에서 `unzip -o`.

<!-- 갱신 끝 -->
