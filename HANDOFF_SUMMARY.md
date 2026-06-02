# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**`framework-idempotency` 응답 재생(replay) 확장** — 기존 인터셉터(중복=409)에 **재생 모드**(`framework.idempotency.replay.enabled=true`, 기본 off=하위호환)를 얹음. 완료된 동일 키 요청은 **저장된 응답(상태/콘텐츠타입/본문)을 그대로 재생**, 처리중은 409, 최초 요청은 통과 후 `afterCompletion`에서 응답 캡처→`saveResult`. 캡처를 위해 **`IdempotencyResponseFilter`**(재생 모드에서만 등록, `@Order(LOWEST_PRECEDENCE)`)가 헤더 있는 요청만 `ContentCachingResponseWrapper`로 감쌈(헤더 없으면 버퍼링 0). 저장 포맷은 **`status\ncontentType\nbase64(body)` 고정 셰이프**(`ResponseSnapshot`, Jackson/문자셋 가정 없음). **실패(예외/5xx/캡처불가)는 캐시 안 하고 선점 해제**. SPI·imports·settings 무변경, 새 외부 의존성 0.

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
- **신규 `web/ResponseSnapshot`**(record): `encode/decode/writeTo`. 포맷 `status\ncontentType\nbase64(body)` — status/ct 가 앞서고 body 는 개행 없는 Base64 라 앞 두 개행만 끊어 무손실 복원(임의 바이너리/문자셋 안전, 이스케이프 불필요).
- **신규 `web/IdempotencyResponseFilter`**(`OncePerRequestFilter`, `@Order(LOWEST_PRECEDENCE)`): Idempotency-Key 헤더 있는 요청만 `ContentCachingResponseWrapper` 로 감싸고 `finally`에서 `copyBodyToResponse()`. 헤더 없으면 그대로 통과(버퍼링 0).
- **개정 `web/IdempotencyInterceptor`**: 2모드. 레거시(기본)=기존 409 동작 그대로. 재생=① `findResult` 있으면 `ResponseSnapshot.decode().writeTo()` 재생 ② `putIfAbsent` 성공 시 통과+캡처표시(req attr) ③ 선점 실패 시 재확인 후 재생/409. `afterCompletion`: 캡처표시 있으면 `WebUtils.getNativeResponse(...ContentCachingResponseWrapper)` 로 본문 읽어 저장. **`ex!=null||status>=500||wrapper==null` → `remove`(실패 미캐시·선점 해제)**.
- **개정 `config/IdempotencyProperties`**: `Replay{enabled=false}` 추가(`framework.idempotency.replay.enabled`).
- **개정 `config/IdempotencyAutoConfiguration`**: `idempotencyResponseFilter` 빈 추가 — `@ConditionalOnClass(ContentCachingResponseWrapper)` + `@ConditionalOnWebApplication(SERVLET)` + `@ConditionalOnProperty(replay.enabled=true)` + `@ConditionalOnMissingBean`. secure-web 처럼 평범한 `@Bean`(Boot 자동 등록).
- **build.gradle**: 테스트가 main 의 `compileOnly` 클래스를 쓰므로 `testImplementation spring-boot-starter-web` 추가(jdbc 분과 동일 사유).
- **테스트**: `ResponseSnapshotTest`(4: 라운드트립·개행·null-CT·바이너리) + `IdempotencyInterceptorTest`(8: 비대상 통과·헤더없음400·레거시409·재생 선점/재생/처리중409·2xx 캡처·5xx 해제, MockHttpServletRequest/Response + HandlerMethod + ContentCachingResponseWrapper).
- **문서**: 모듈 `README.md`(재생 모드 절 + replay 토글).

## 현재 상태 (적용/검증)
- 정적 점검 통과: 패키지=디렉터리, 괄호 균형, **`com.fasterxml` 0건**, Boot4/spring FQCN(`ConditionalOnWebApplication`·`ContentCachingResponseWrapper`·`WebUtils`·`core.annotation.Order`·`OncePerRequestFilter` — secure-web 와 동일).
- **순수 JDK(JDK21) 실행검증 10/10**: 코덱 무손실(기본·개행·null-CT·바이너리) + 인터셉터 분기(선점통과·처리중409·완료재생·재생본문보존·5xx해제·예외해제).
- ⚠️ **gradle 컴파일 미검증**(작성 환경 Maven Central 차단). 받는 쪽: `./gradlew :framework:framework-idempotency:compileJava :test +spotlessApply`. 특히 새 `testImplementation spring-boot-starter-web` 로 `IdempotencyInterceptorTest` 컴파일 통과 확인.

## 켜는 법
- 모듈/기능 켜기는 기존과 동일 + `framework.idempotency.replay.enabled=true`.
- 컨트롤러 메서드 `@Idempotent` + 클라이언트 `Idempotency-Key` 헤더. 재생 모드: 최초 통과/완료 재생, 처리중 409, 헤더없음 400.
- 다중 인스턴스/재기동 간 재생 유지하려면 `store.type=jdbc` 또는 `redis`(memory 는 인스턴스 로컬). 본문 메모리 버퍼링이라 **작은 JSON 응답** 권장(스트리밍 부적합).

## 바로 다음 할 일 (Next)
1. 받는 쪽 `:framework:framework-idempotency:compileJava :test (+spotlessApply)` — 12케이스(jdbc 6 + snapshot 4 + interceptor 8 중 신규 12) 그린 확인. 특히 filter `@Order`/`@ConditionalOnWebApplication` 활성·재생 e2e.
2. (선택) user-service 에 `@Idempotent` + `replay.enabled=true` + `store.type=jdbc` 실적용, 타임아웃 재시도 시나리오로 동일 응답 재생 e2e(devops). 전체 만료 청소 잡(스케줄러 `DELETE WHERE expires_at<=now`) 동반.
3. (선택·정합성 강화) 동일 키+다른 페이로드 충돌 시 IETF 권고대로 422 반환하려면 요청 지문(payload hash) 저장/비교 필요 — 현재는 미구현(키만으로 판정). 별도 토글로 후속 검토.
4. **누적 문서 동기화 완료**: `HANDOFF.md`(모듈 항목·학습/함정 3건·7절 완료/다음 우선순위)·`docs/FRAMEWORK_MODULES.md`(현황·완료 항목·다음 후보·토글 표·금융 프리셋)·`STACK.md`(의존성 무변경 노트·H2 test-scope)·루트 `README.md`(idempotency 절: store=memory\|redis\|jdbc + replay) 전부 반영. 5개 문서 모두 최신.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **응답 캡처는 필터 래핑 전제**: 인터셉터는 응답을 *교체*할 수 없다(디스패처가 받은 응답 고정). 본문 캡처/재생하려면 **필터**가 `ContentCachingResponseWrapper`로 감싸고 `finally`에서 `copyBodyToResponse()` 해야 한다. 인터셉터는 `WebUtils.getNativeResponse(res, ContentCachingResponseWrapper.class)`로 래퍼를 찾는다. 필터는 secure-web 처럼 평범한 `@Bean`+`@Order`(Boot 자동 등록)이며 응답을 가장 안쪽에서 감싸야 하므로 **`@Order(LOWEST_PRECEDENCE)`**(요청 스크리닝 필터들보다 안쪽).
- **재생 저장은 고정 셰이프 + Base64**: `status\ncontentType\nbase64(body)`. body 를 Base64(개행 없는 `getEncoder()`)로 인코딩해 앞 두 개행만으로 분리 → 임의 본문/문자셋/바이너리 무손실. JSON 직렬화·이스케이프·문자셋 추정 전부 불필요(프레임워크의 "수기 고정 셰이프" 원칙 일관).
- **실패는 캐시 금지·선점 해제**: 캡처 시 `ex!=null||status>=500||wrapper==null` 이면 `saveResult` 대신 `remove`. 안 그러면 5xx 응답이 재생돼 재시도가 영영 실패하거나, 캡처 불가로 키가 TTL 까지 묶여 409 고착. GlobalExceptionHandler 가 처리한 예외는 `afterCompletion`의 `ex`가 null 이라 **상태코드(status>=500)로 판정**.
- **재생은 옵트인(하위호환)**: `replay.enabled` 기본 false → 기존 409 동작 보존. 필터도 재생 모드에서만 등록. (전 모듈 공통 "기본 off" 원칙.)
- (지난) **`compileOnly` 는 test 클래스패스로 전이 안 됨** — 테스트가 main 의 compileOnly 클래스(web/jdbc) import 하면 `testImplementation` 재선언(이번: web 추가). · JDBC 멱등 선점=PK충돌 상호배제·만료행만 정리 · 벤더 UPSERT 금지 · `result NULL`→empty · H2 실DB 테스트.
- (기존) Boot4 패키지 이동 · 구조화로그=EPP · OTLP 이중키 · 새 의존성 0 · 테스트 API 모듈마다 선언 · util vs support · JsonUtils=Jackson3 · JUnit launcher 루트 · 범용 유틸 재발명 금지 · 신규 모듈 settings/imports 등록 · BOM 밖만 카탈로그 핀.

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈 또는 기존 확장. 기존 모듈에 **웹 응답 캡처/재생**을 더할 땐(이번 사례): 필터(`OncePerRequestFilter`+`@Order`)로 응답 래핑 + 인터셉터 `afterCompletion` 캡처 + 고정 셰이프 코덱. 빈은 세부 토글(`@ConditionalOnProperty`)로 옵트인.
2. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`(web/jdbc/redis). **테스트가 그 compileOnly 클래스를 import 하면 `testImplementation` 재선언**(web/jdbc). 실DB 검증은 `testRuntimeOnly h2`.
3. `settings.gradle`/`imports` 등록 — **기존 모듈 확장이면 무변경**(이번: 변경 없음).
4. 코드 전 Boot4/Spring7/Jackson3 + 외부 API 공식 소스 확정. 조용히 틀리는 로직(멱등 선점·코덱·재생 분기)은 순수 JDK 또는 H2 로 실제 실행 검증.
5. 오토컨피그: `@AutoConfiguration` + `@ConditionalOnClass/Property(+WebApplication)` + 빈 `@ConditionalOnMissingBean`. 필터는 평범한 `@Bean`(Boot 자동 등록)+`@Order`(secure-web 컨벤션).
6. 검증: `./gradlew :...:compileJava (+:test) (+spotlessApply)`.
7. 드롭인: 변경 파일 전부 → 한 zip, 루트에서 `unzip -o`.


<!-- 갱신 끝 -->
