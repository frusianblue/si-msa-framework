# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**모듈별 최소 테스트 + 아키텍처 규칙 강제 도입.** (1) 핵심 알고리즘 단위테스트 — JWT(`JwtProviderTest`), TOTP/Base32(`TotpTest`/`Base32Test`, RFC4648 벡터), RBAC 판정(`DynamicAuthorizationManagerTest`), 마스킹(`MaskingUtilsTest`). (2) 오토컨피그 로딩 — `MfaAutoConfigurationTest`(enabled/disabled 빈 등록), client 로딩은 WireMock 테스트에 포함. (3) **신규 테스트전용 모듈 `framework-archtest`** — ArchUnit 7규칙(모듈 순환금지·Jackson3 규약·mapper/domain 레이어 격리·*AutoConfiguration/*Properties 네이밍·필드주입 금지). (4) **WireMock 연동테스트 `ClientResilienceWireMockTest`** — 503 재시도→200·서킷 OPEN 차단·POST 비재시도. **새 런타임 의존성 0**(archunit/wiremock 은 test 전용).

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
- **카탈로그**(`gradle/libs.versions.toml`): `archunit=1.4.2`(Java21 OK), `wiremock=3.13.2`(standalone/shaded) 버전 + `archunit-junit5`·`wiremock-standalone` 라이브러리 추가. **둘 다 test 전용 → 배포 산출물·런타임 무영향.**
- **신규 모듈 `framework:framework-archtest`**(테스트 전용, `settings.gradle` 등록): 모든 라이브러리 모듈을 `testImplementation project(...)` 로 끌어와 ArchUnit 으로 검사. `FrameworkArchitectureTest`(@AnalyzeClasses, DoNotIncludeTests) 7규칙:
  1. `slices().matching("com.company.framework.(*)..").beFreeOfCycles()` — 21 슬라이스 순환 없음(검증된 DAG).
  2. Jackson3: `noClasses().dependOnClassesThat().resideInAnyPackage(databind/core/dataformat/datatype/module ..)` — **이동된 패키지만 금지, `com.fasterxml.jackson.annotation` 은 예외**(3에서도 유지).
  3. mapper → web/service 의존 금지. 4. domain → web/service/mapper 의존 금지.
  5. `*AutoConfiguration` → `@AutoConfiguration`. 6. top-level `*Properties` → `@ConfigurationProperties`.
  7. `NO_CLASSES_SHOULD_USE_FIELD_INJECTION`(생성자 주입 강제).
- **알고리즘/오토컨피그 단위테스트**:
  - `framework-core`: `MaskingUtilsTest`(7 마스커 + 엣지; 기존 CoreUtilsTest 보강).
  - `framework-security`: `JwtProviderTest`(발급/파싱 라운드트립·roles→ROLE_ 권한·키불일치/변조 거부), `DynamicAuthorizationManagerTest`(6케이스: 역할 매칭/거부·메서드 한정 매핑·미매핑→인증만·null→거부, StubSecurityMapper).
  - `framework-mfa`: `Base32Test`(RFC4648 벡터·20바이트 무손실 왕복·관대 디코드·잘못된 문자 예외), `TotpTest`(SHA1/256/512·6/8자리 결정적 왕복·공백/하이픈 정규화·형식 거부), `MfaAutoConfigurationTest`(enabled→Totp/MfaService/MfaGate/인메모리 스토어 빈·disabled→빈 없음, stub `CurrentUserProvider`).
- **WireMock 연동테스트** `framework-client/.../ClientResilienceWireMockTest`: (a) 오토컨피그 로딩(enabled 빈 유무), (b) GET 503→재시도→200 "ok"(업스트림 2회), (c) 재시도 OFF·임계치2 → 3번째 호출 서킷 OPEN 차단(`CircuitOpenException` 체인·업스트림 정확히 2회), (d) POST 503 비재시도(업스트림 1회). 단일 RestClient 재사용으로 호스트 단위 브레이커 공유.
- **build.gradle**: `framework-client` test 에 `spring-boot-starter-test`+`-web`(compileOnly 비전이라 재선언)+`wiremock-standalone`. `framework-archtest` 는 archunit-junit5 + 전 모듈 project 의존 + `spring-boot-autoconfigure`(애너테이션 타입).

## 현재 상태 (적용/검증)
- **순수 로직/벡터 JDK 검증 완료**(작성 환경 javac 부재·Maven Central 차단 → JRE+Python 포팅으로 교차검증): Base32 RFC4648 전 벡터, 마스킹 7종 출력(예: 홍길동→홍*동, 010-1234-5678→010-****-5678, 1234567812345678→1234-****-****-5678), 모듈 의존 그래프 = **순환 없는 DAG**(21노드).
- **ArchUnit 7규칙 사전 정적검증 ALL PASS**: 이동된 `com.fasterxml.jackson.*` **0건**(유일한 jackson import 는 `.annotation` 1건=허용), 모든 `*AutoConfiguration`=@AutoConfiguration·top-level `*Properties`=@ConfigurationProperties, 필드주입 0건.
- ⚠️ **gradle 컴파일·테스트 실행은 받는 쪽에서**: `./gradlew :framework:framework-archtest:test :framework:framework-client:test :framework:framework-mfa:test :framework:framework-security:test :framework:framework-core:test (+spotlessApply)`.

## 켜는 법
- 테스트/규칙은 토글 아님 — `./gradlew test` 에 자동 포함. ArchUnit 규칙 위반 시 빌드 실패(의도). 새 모듈 추가 시 `framework-archtest/build.gradle` 에 `testImplementation project(':framework:framework-<신규>')` 한 줄 추가해야 검사 대상에 포함됨.
- WireMock 연동테스트는 `framework-client` 의 `compileOnly` web 을 test 에서 재선언해야 RestClient 런타임이 생김(레포 표준).

## 바로 다음 할 일 (Next)
1. 받는 쪽에서 위 gradle 명령으로 그린 확인(특히 archtest 7규칙·wiremock 4케이스). 실패 규칙 있으면 위반 클래스 정리 or 규칙 예외 명문화.
2. 테스트 0개였던 잔여 모듈(audit/commoncode/file/redis/messaging/saga/excel/batch/notification/observability/idgen/i18n/openapi/secureweb/datasource는 일부 보유)로 오토컨피그 로딩 스모크 확대.
3. (devops) CI 에 `:framework-archtest:test` 게이트 + jacoco 집계, ArchUnit 규칙 위반을 PR 차단으로.
4. **그릇 정비** 잔여: 게이트웨이 런타임 점검(CORS preflight/rate-limit 429) · k8s 멀티서비스/CI-CD · (선택) 규제특화 잔여(pki/hsm/recon/egov).

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **ArchUnit Jackson 규칙은 이동된 패키지만 금지.** `jackson-annotations` 는 Jackson 3 에서도 `com.fasterxml.jackson.annotation` 에 그대로 남는다 → `ApiResponse` 의 `com.fasterxml.jackson.annotation.JsonInclude` 는 **정상**. 규칙에서 `...annotation` 을 금지하면 오탐. 금지 대상은 `databind/core/dataformat/datatype/module` 뿐.
- **인터셉터 합성 순서 Trace→CircuitBreaker→Retry→Logging**(바깥→안): 서킷이 재시도를 감싸므로 재시도 내부 중간 5xx 는 서킷에 집계 안 됨. **서킷 단독 검증은 재시도 OFF** 로. 서킷 차단은 `CircuitOpenException`(IOException 계열)→RestClient 가 `ResourceAccessException` 으로 래핑 → 원인 체인 탐색으로 단언.
- **WireMock 은 standalone(shaded) 픽스.** Boot4 가 끌어오는 Jetty 12.1(EE10)·Jackson 3 과 일반 wiremock-jetty12 가 충돌 → `org.wiremock:wiremock-standalone` 으로 내부 의존 셰이딩. 공개 API 는 `com.github.tomakehurst.wiremock.*` 유지.
- **archtest 는 전 모듈 project 의존이 필수.** ArchUnit 은 바이트코드를 읽으므로 검사 대상 모듈 main 이 test 클래스패스에 있어야 임포트됨. 각 모듈 compileOnly 3rd-party 는 안 올라와도, 규칙이 `com.company.framework` 의존만 보므로 미해소 외부 타입 무방. **새 모듈 추가 시 의존 한 줄 누락하면 그 모듈은 검사 사각지대.**
- **조건부 규칙엔 `.allowEmptyShould(true)`** — 모듈 부분 빌드/필터 결과가 비어도 ArchUnit 1.4 의 "empty should" 실패를 막음.
- (지난) `compileOnly` 비전이(테스트 재선언) · 모듈 toggle 3단 기본 off · 순수 로직 분리 JDK 검증 · Jackson3(`tools.jackson.*`) · `[this-escape]` 생성자 메서드참조 금지 · JUnit Platform launcher 명시(Gradle 9).

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. 순수 로직은 Spring 무의존 코어로 분리해 JDK 단독 검증.
2. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`. **테스트가 그 compileOnly 클래스를 쓰면 재선언.**
3. `settings.gradle`/`imports` 등록. **신규 모듈이면 `framework-archtest/build.gradle` 에 project 의존 추가**(아키텍처 검사 포함).
4. Boot4/Spring7/Jackson3 + 통합 대상 실제 시그니처를 레포 내 동일 사용처로 교차확인.
5. 오토컨피그 3단 토글 + 빈 `@ConditionalOnMissingBean`. 런타임 개수 가변 빈은 `ImportBeanDefinitionRegistrar`.
6. **테스트**: 핵심 알고리즘 단위 + 오토컨피그 로딩(ApplicationContextRunner enabled/disabled). 외부연동은 WireMock(standalone). 검증 `./gradlew :…:test (+:framework-archtest:test) (+spotlessApply)`.
7. 드롭인: 변경 파일 전부 → 한 zip, 루트에서 `unzip -o`. 문서 5종 동기화.

<!-- 갱신 끝 -->
