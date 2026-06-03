# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**기본기능 카탈로그 신설 + 카탈로그 #5 `framework-context` 신설·빌드 검증.** (1) `docs/BASELINE_FEATURES.md` — SI/MSA 공통 프레임워크가 갖춰야 할 기본기능 10항목을 레포 코드와 실측 대조(✅있음/🟡부분/🔴없음 + 위치 + 인수기준), 추가 요청은 §6 대기열로 수집. (2) **`framework-context`** — 요청마다 `RequestContext`(tenantId/userId/locale+확장 attributes)를 `ContextHolder`(정적 ThreadLocal, **상속형 아님**)에 바인딩/정리(+MDC), `@Async`·아웃바운드로 **명시 전파**. 3단 토글·기본 off, **신규 외부 의존성 0**(servlet/web compileOnly). 분산 락·PDF·캐시·로그마스킹에 이어 횡단 기반(컨텍스트)까지.

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
### A. docs/BASELINE_FEATURES.md (기본기능 카탈로그)
1. 사용자 제시 10항목(회복탄력성·페이징/정렬·PDF·리포트·멀티테넌시/컨텍스트·분산캐시·이미지처리·대용량/presigned·파일 메타 정합성·AV 훅)을 **레포 코드와 실측 대조**. 결과: ✅5(client·core/page·pdf·cache-redis + 이번 context) · 🟡2(리포트=pdf+excel 조합으로 보류 권장 · 메타정합성=Tika 검출까지만) · 🔴3(이미지·presigned/스트리밍·AV 훅).
2. 항목별 상태+위치+**인수기준**, 우선순위, 공통 설계원칙, **§6 추가 요청 대기열**(앞으로 떠오르는 기본기능을 여기 적어 승격).

### B. framework-context (요청 컨텍스트 / 멀티테넌시)
3. **값/홀더(순수 JDK)**: `RequestContext`(불변·빌더, tenantId/userId/locale + 확장용 attributes 맵, EMPTY/hasTenant/toBuilder, 방어적 복사) · `ContextHolder`(정적 `ThreadLocal`, get 은 절대 null 아님=EMPTY, **상속형 미사용** — 가상스레드/풀 누수 방지).
4. **해소(교체 가능)**: `ContextResolver`(SPI) · 기본 `HeaderContextResolver`(tenant/user 헤더 + Accept-Language 있을 때만 locale). 앱이 빈 정의 시 우선(`@ConditionalOnMissingBean`) → JWT/SecurityContext 로 대체.
5. **바인딩**: `ContextBindingFilter extends OncePerRequestFilter`, `@Order(HIGHEST_PRECEDENCE+10)`(MdcTraceFilter 바로 안쪽). 진입 시 set+MDC(tenantId/userId), finally 에서 MDC 키 제거+`ContextHolder.clear()`(예외 포함).
6. **전파(명시 2경로)**: `ContextTaskDecorator implements TaskDecorator`(@Async/풀에 컨텍스트+MDC 스냅샷 복원→종료 후 원복) · `ContextPropagationInterceptor implements ClientHttpRequestInterceptor`(아웃바운드에 헤더 전파, client 모듈 TracePropagation 패턴, 기존 헤더 비덮어쓰기).
7. **오토컨피그/프로퍼티**: `ContextAutoConfiguration`(`@AutoConfiguration`+`@ConditionalOnWebApplication(SERVLET)`+`@ConditionalOnProperty(framework.context.enabled=true)`+`@EnableConfigurationProperties`). 빈 resolver/filter/decorator(`@ConditionalOnMissingBean`)·interceptor(`@ConditionalOnClass(ClientHttpRequestInterceptor)`+`propagate-downstream` 토글 matchIfMissing=true). `ContextProperties`(enabled=false·tenant-header=X-Tenant-Id·user-header=X-User-Id·put-to-mdc=true·mdc 키·propagate-downstream=true).
8. build.gradle = `api framework-core` + **`compileOnly`+`testImplementation` spring-boot-starter-web**(secure-web 패턴) + config-processor + starter-test. 테스트 6종(RequestContext/ContextHolder 순수JDK · ContextTaskDecorator 전파+풀 누수 · HeaderContextResolver(MockHttpServletRequest) · ContextBindingFilter 바인딩/MDC off/예외정리 · ContextAutoConfiguration 토글·인터셉터 옵트아웃·앱 리졸버 우선(isSameAs)·**가드**).

### C. 공통 등록/문서
9. **등록/배선**: `settings.gradle` include(log-masking 다음, archtest 앞) · `.imports`(`ContextAutoConfiguration`) · `framework-archtest/build.gradle` 에 project 의존(arch 스캔). 신규 슬라이스 `context`→core 단방향(순환 없음).
10. **문서 5종 동기화**: 모듈 `README.md` 신설 · 루트 `README.md`(요약/의존스니펫/섹션) · `HANDOFF.md`(모듈·함정 3종·최신세션·우선순위) · `docs/FRAMEWORK_MODULES.md`(완료/표/트리/로드맵) · `STACK.md`(의존성 0 명시). + `docs/BASELINE_FEATURES.md` 신설.

## 현재 상태 (적용/검증)
- ✅ **사용자 환경 컴파일 BUILD 통과 확인(2026-06-03)** — "컴파일 이상없이 잘된다".
- 설계상 arch 규칙 통과 예상: `ContextAutoConfiguration`→`@AutoConfiguration` ✓ / top-level `ContextProperties`→`@ConfigurationProperties` ✓ / 필드주입 0(`ContextHolder` 정적필드는 `@Autowired` 아님) / Jackson2 이동패키지 0 / 슬라이스 context→core 단방향.
- **신규 외부 의존성 0**: servlet/web 은 Boot BOM(compileOnly 비노출) → 카탈로그/ext/STACK 버전 무변경.

## 켜는 법
```yaml
framework:
  context:
    enabled: true               # 끄면(기본) 빈 미등록
    tenant-header: X-Tenant-Id
    user-header: X-User-Id
    put-to-mdc: true            # tenantId/userId → MDC(로그 자동 노출)
    propagate-downstream: true  # 아웃바운드 헤더 전파 인터셉터 등록
```
```java
RequestContext ctx = ContextHolder.get();        // 어디서든(미바인딩=EMPTY, null 아님)
executor.setTaskDecorator(contextTaskDecorator);  // @Async 전파(core 가상스레드 실행기에 연결)
RestClient.builder().requestInterceptor(contextPropagationInterceptor).build(); // 아웃바운드 전파
// 해소 전략 교체: ContextResolver 빈 정의(JWT 등) → 기본 헤더 리졸버 대체
```
- 테넌트 필수 검증은 **서비스 레이어**에서 `ContextHolder.get().hasTenant()`(필터는 GlobalExceptionHandler 밖 → 거부 JSON 수기 회피).
- 서블릿 웹 한정. `@Async` 전파는 실행기에 데코레이터를 직접 연결해야 동작(자동 재배선 안 함).

## 바로 다음 할 일 (Next)
1. **파일 하드닝 묶음**(카탈로그 #8+#9+#10, framework-file* 표면 공유): 대용량/스트리밍(HTTP Range·S3 presigned·멀티파트) · 메타 정합성 강화(확장자↔실제 MIME allowlist·EXIF) · 안티바이러스 훅(FileScanner SPI + ClamAV 어댑터 옵트인).
2. **이미지 처리**(#7, framework-image): 썸네일/리사이즈·EXIF orientation 보정·민감 EXIF(GPS) 제거.
3. (devops) **CI 게이트**: `:framework-archtest:test` + 전 모듈 `:test` PR 차단 + 멀티모듈 jacoco 집계.
4. **추가 기본기능**: 떠오르면 `docs/BASELINE_FEATURES.md` §6 대기열에 적어 다음 세션에 승격.
5. (선택) 컨텍스트 심화: core AsyncConfig 가상스레드 실행기에 데코레이터 자동 연결 옵션 · reactive(게이트웨이) 컨텍스트 전파 검토.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **요청 컨텍스트는 상속형 ThreadLocal 금지**: `ContextHolder` 는 평범한 `ThreadLocal`. 가상 스레드/풀에서 `InheritableThreadLocal` 은 누수 → 전파는 **항상 명시적**(`ContextTaskDecorator` 스냅샷 복원 + 작업 후 원복, `ContextPropagationInterceptor` 헤더). 필터는 종료(예외 포함) 시 `clear()`+자기 MDC 키 제거. 정적상태라 테스트 `@AfterEach ContextHolder.clear()`+`MDC.clear()`.
- **테넌트 필수 검증은 필터 아님**: 필터는 디스패처 이전이라 `GlobalExceptionHandler` 밖(secure-web `SecureWebResponder` 와 같은 결). `ContextBindingFilter` 는 **바인딩만**, 필수 여부는 서비스에서 `hasTenant()` 검사 후 `BusinessException`. `HeaderContextResolver` 는 신뢰 헤더 읽기만(외부 위조 방지는 게이트웨이/인증 책임).
- **컨텍스트 필터 순서 = MdcTraceFilter 안쪽**: trace=HIGHEST_PRECEDENCE(최외곽, finally MDC.clear), context=HIGHEST_PRECEDENCE+10. traceId 가 이미 있는 상태에서 tenantId/userId 덧붙임, 외곽 clear 와 충돌 없음.
- **리포트는 별도 모듈 보류**: pdf+excel 조합으로 커버. 멀티포맷 오케스트레이션이 실제 필요할 때만 신설(카탈로그 #4).
- **파일 메타 정합성은 아직 부분**: 현 Tika 는 매직넘버 검출+차단목록까지. 확장자↔실제 MIME allowlist 미스매치 거부·EXIF 정합은 파일 하드닝에서 보강(카탈로그 #9).
- (지난·유효) compileOnly 타입은 test 재선언 / 레지스트레이션 가드(`.imports` union 직접 단언) / 토글 3단 기본 off / Jackson3(`tools.jackson.*`, `.annotation` 만 예외) / 필터·로그컨버터 등 컨테이너 밖은 GlobalExceptionHandler/DI 밖 / 계좌 정규식은 dash-grouped 숫자열 광범위 매칭(log-masking, 기본 off) / Boot 구조화 로깅엔 %mmsg 미적용 / Boot4 패키지·외부 라이브러리 리네임 추측 금지 / JUnit launcher·starter-test 모듈마다 / 콘솔 UTF-8(미해결 보류).

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. 순수 로직은 Spring 무의존 코어로 분리해 JDK 단독 검증(예: `RequestContext`/`ContextHolder`/`ContextTaskDecorator`).
2. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`(+테스트가 그 타입 참조 시 `testImplementation` 재선언), BOM 밖 내부 라이브러리=`implementation`(비노출). 이번도 의존성 0(servlet/web=Boot BOM).
3. `settings.gradle`/`imports` 등록. 신규 모듈이면 `framework-archtest/build.gradle` 에 project 의존 추가. **새 오토컨피그는 `.imports` 등록 + 등록 가드 테스트**. 신규 top-level 패키지는 슬라이스 충돌/순환 확인(context→core 단방향).
4. 기존 패턴 교차확인 후 미러링(이번: `MdcTraceFilter` 순서/MDC, client `TracePropagationInterceptor`, secure-web build.gradle compileOnly web, log-masking 가드 테스트, core AsyncConfig 가상스레드 실행기).
5. 오토컨피그 3단 토글 + 빈 `@ConditionalOnMissingBean`(+필요 시 `@ConditionalOnWebApplication`/`@ConditionalOnClass`). 컨테이너 밖/타 스레드 전파는 정적 다리·데코레이터·인터셉터로 명시 연결.
6. **테스트**: 핵심 알고리즘 단위(JDK) + 오토컨피그 로딩(enabled/disabled, 서블릿은 `WebApplicationContextRunner`) + 등록 가드. 정적/MDC 상태 쓰는 테스트는 `@AfterEach` 격리.
7. 드롭인: 변경 파일 전부 → 한 zip, 루트에서 `unzip -o`. 문서 5종 + 필요 시 카탈로그 동기화. 사용자 환경에서 `./gradlew :…:test :framework-archtest:test spotlessApply` 검증.

<!-- 갱신 끝 -->
