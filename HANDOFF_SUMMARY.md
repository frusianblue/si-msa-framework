# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**잔여 무테스트 모듈 14개에 오토컨피그 로딩/토글 스모크 추가 — gradle BUILD 통과 확인.** 직전 세션 Next #1 완료. `ApplicationContextRunner`(필요 시 `WebApplicationContextRunner`)로 `framework.<module>.enabled` 토글 켜짐/꺼짐을 빈 등록 유무로 검증. 실제 레포의 `@Bean` 시그니처·`build.gradle` 을 직접 읽어 **introspection 함정(compileOnly 타입 전부 test 재선언)** 을 모듈별로 반영. 신규 테스트 14개 + `build.gradle` 7개 변경 — **전부 `testImplementation` → 런타임/배포 영향 0**. 발견 2건: redis 레지스트레이션 갭·MapperScan 결합 모듈(commoncode/file)은 백오프-only.

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
- **오토컨피그 로딩/토글 스모크 14개 신규**(각 모듈 `src/test/.../<config>Test.java`):

  | 모듈 | 검증축 | introspection 대응(test 재선언) |
  |---|---|---|
  | audit | enabled→logging 싱크(LoggingAuditEventSink), jdbc 조회 API off / disabled→none | **jdbc + web + messaging** |
  | batch | enabled(+JobOperator mock)→JobLaunchSupport+LoggingJobExecutionListener / disabled→none | 불필요 |
  | excel | enabled→ExcelExporter/ExcelImporter / disabled→none | 불필요(POI=implementation, 자기 test 에 존재) |
  | idgen | enabled→IdGenerator만(DataSource無→CodeGenerator off) / disabled→none | **jdbc**(중첩 CodeGeneratorConfiguration 시그니처) |
  | i18n | 코어 enabled→MessageResolver/ErrorMessageResolver / 웹(서블릿)→LocaleResolver+I18nExceptionAdvice | **web**(+starter-test); 웹은 `WebApplicationContextRunner` |
  | messaging | 발행 enabled→OutboxRepository/Publisher(Relay off) / 소비 enabled(+IdempotencyStore mock)→IdempotentEventProcessor | 기존 test 의존(jdbc/kafka-test/idempotency) 충분 |
  | mybatis | 로딩→PersistenceExceptionHandler/ConfigurationCustomizer/CurrentUserProvider/AuditFieldInterceptor / audit-injection=false→인터셉터만 off | 불필요 |
  | notification | enabled→NotificationService / +sms→LoggingSmsClient+SmsNotificationSender / disabled→none | 불필요(mail=api) |
  | openapi | 기본(matchIfMissing)→OpenAPI / false→none | **starter-test 신규**(test 의존 0이었음) |
  | redis | token-store.type=redis→TokenStore / login-attempt.type=redis→LoginAttemptService(+StringRedisTemplate mock) | **starter-test 신규** |
  | secure-web | enabled→SecureWebResponder+헤더+경로필터, 나머지 off / 전부 on→인젝션/CSRF/rate-limit | **web**; `WebApplicationContextRunner` |
  | file-s3 | type=s3(+S3Client mock+FileStorageProperties deep-stub)→S3FileStorage / 미지정→none | **starter-test 신규** |
  | commoncode·file | **disabled 백오프만**(enabled=false→빈 없음·컨텍스트 정상) | 불필요 |

- **build.gradle 7개 보정**(introspection 함정·test 의존 결손): audit(+jdbc/web/messaging), file-s3(+starter-test), i18n(+starter-test/web), idgen(+starter-test/jdbc), openapi(+starter-test), redis(+starter-test), secure-web(+web). 나머지 7개 모듈은 기존 test 의존으로 충분(batch/excel/messaging/mybatis/notification/commoncode/file).
- **mybatis 는 보너스 포함**: 직전 Next 의 13개 목록에서 빠져 있었음 — `MyBatisConfig` 가 `*AutoConfiguration` 네이밍이 아니라 ArchUnit 네이밍 규칙·목록에서 누락. 0개 테스트라 함께 스모크 추가.
- **패턴 출처**: `MfaAutoConfigurationTest`(JUnit5+AssertJ, `@DisplayName` 한국어, `assertThat(context).hasNotFailed()/hasSingleBean/doesNotHaveBean`).

## 현재 상태 (적용/검증)
- ✅ **gradle BUILD 통과 확인(사용자 환경, 2026-06-03)**: 14개 모듈 `:test` 전부 통과(컴파일·컨텍스트 기동·어서션). 작성 환경은 Maven Central 차단으로 빌드 불가 → 참조 클래스/패키지 존재를 `find/grep` 으로 정적 교차검증(MISSING 0), `JsonMapper.builder()` 는 레포 `JsonUtils` 와 동일 사용 확인 후 사용자 환경에서 최종 통과.
- 이제 **테스트 보유 = 전 라이브러리 모듈**(기존 core·datasource·idempotency·observability·saga·mfa·security·client·archtest + 이번 14개). 무테스트 라이브러리 모듈 0.

## 켜는 법
- 스모크는 토글 아님 — `./gradlew test` 자동 포함. 부분 실행: `./gradlew :framework:framework-{audit,batch,commoncode,excel,file,file-s3,i18n,idgen,messaging,mybatis,notification,openapi,redis,secure-web}:test spotlessApply`(셸 중괄호 미동작 시 모듈 나열).
- 신규 오토컨피그에 스모크 붙일 때: **그 오토컨피그(중첩 @Configuration 포함)의 `@Bean` 시그니처에 나타나는 compileOnly 타입을 전부 `testImplementation` 재선언**. `@ConditionalOnWebApplication(SERVLET)` 이면 `WebApplicationContextRunner`. `@MapperScan`+matchIfMissing(기본 ON)+MyBatis 결합 모듈은 enabled 가 DataSource 를 요구 → 순수 스모크는 **disabled 백오프**까지만.

## 바로 다음 할 일 (Next)
1. **redis 레지스트레이션 갭 해소(소)**: `RedisLoginAttemptAutoConfiguration` 이 코드로 존재하나 `META-INF/.../AutoConfiguration.imports` 에 **미등록**(현재 `RedisTokenStoreAutoConfiguration` 만). `login-attempt.type=redis` 설정해도 자동활성 안 됨 → imports 에 한 줄 추가 필요. (테스트는 두 클래스 직접 로드로 의도 동작을 이미 문서화/검증.)
2. **commoncode/file enabled 경로 테스트(중)**: 두 모듈은 클래스레벨 `@MapperScan`+matchIfMissing(기본 ON)+MyBatis 결합이라 enabled 풀 와이어링이 `SqlSessionFactory`/DataSource 를 요구 → **DB 있는 서비스 슬라이스(@MybatisTest 또는 Testcontainers-H2/PG)** 에서 다룬다. 현재는 백오프만 검증됨.
3. (devops) CI 게이트: `:framework-archtest:test` + 전 모듈 `:test` 를 PR 차단 게이트로 + **멀티모듈 jacoco 집계 리포트**(루트 aggregate).
4. **그릇 정비**: 게이트웨이 런타임 점검(CORS preflight `Access-Control-*`·rate-limit 429) · k8s 멀티서비스/CI-CD(redis/secret/observability ServiceMonitor 실배포).
5. (선택) 규제특화 잔여(pki/hsm/recon/egov) · saga 단계별 타임아웃/보상 재시도 · 멱등 재생 페이로드 지문(payload hash).

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **redis 오토컨피그 2개 중 1개만 등록됨**: `RedisTokenStoreAutoConfiguration`(imports 등록) + `RedisLoginAttemptAutoConfiguration`(**미등록**). 후자는 코드·테스트는 있으나 자동활성 경로가 없다 — Next #1. 새 오토컨피그 작성 시 `.imports` 등록을 항상 같이 확인(클래스만 있고 미등록이면 죽은 코드).
- **`@MapperScan` 은 클래스레벨이라 enabled 경로 스모크가 DB 를 끌어온다**: `@ConditionalOnProperty(matchIfMissing=true)` 로 기본 ON 인 commoncode/file 은 enabled 시 `@MapperScan` 이 매퍼 빈을 `SqlSessionFactory` 로 생성하려 해 DataSource 없으면 컨텍스트 실패. **단, 클래스레벨 조건이 false(enabled=false)면 설정 클래스가 제외돼 @MapperScan 도 미처리** → 백오프 스모크는 안전. 순수 로딩 스모크의 한계 = MapperScan 결합 모듈은 disabled 까지.
- **`@ConditionalOnWebApplication(SERVLET)` 오토컨피그는 `WebApplicationContextRunner`**: 일반 `ApplicationContextRunner` 는 웹앱이 아니라 그 설정이 통째로 백오프(빈 0). i18n 웹/secure-web 이 해당. 또한 web 필터/`FilterRegistrationBean`/`LocaleResolver` 등 web 타입 introspection 위해 `testImplementation spring-boot-starter-web` 필요(main 은 compileOnly).
- **`MyBatisConfig` 는 `*AutoConfiguration` 네이밍이 아니다**: `@AutoConfiguration` 이지만 이름이 규칙(`*AutoConfiguration`)과 달라 ArchUnit 네이밍 규칙 적용 대상이 아니고, 직전 세션의 "0개 테스트 13개 목록"에서도 누락됐다. 0-test 모듈을 셀 땐 이름이 아니라 `imports` 등록분으로 세야 빠지지 않음.
- **introspection 함정은 중첩 @Configuration·@Bean 반환타입까지**: idgen 의 중첩 `CodeGeneratorConfiguration` @Bean 이 `JdbcTemplate`/`PlatformTransactionManager` 를 받음 → jdbc 재선언. audit 의 `auditController()` 반환타입 `AuditController`(web import) → web 재선언. "@Bean 파라미터" 뿐 아니라 **반환 타입 클래스가 끌고 오는 import** 도 로드된다.
- (지난·유효) 오토컨피그 introspection = compileOnly 타입 전부 test 재선언 / `compileOnly` 비전이 / 모듈 toggle 3단 기본 off(단 commoncode·file·openapi 는 `matchIfMissing=true` 기본 ON·예외) / Jackson3(`tools.jackson.*`, `.annotation` 만 예외) / 콘솔 UTF-8 3계층 / JUnit Platform launcher·starter-test 모듈마다 / ArchUnit Jackson 은 이동 패키지만 금지.

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. 순수 로직은 Spring 무의존 코어로 분리해 JDK 단독 검증.
2. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`. **테스트가 그 compileOnly 클래스를 쓰면 재선언.**
3. `settings.gradle`/`imports` 등록. **신규 모듈이면 `framework-archtest/build.gradle` 에 project 의존 추가**(아키텍처 검사 포함). 새 오토컨피그는 `.imports` 등록을 반드시 확인(미등록=죽은 코드).
4. Boot4/Spring7/Jackson3 + 통합 대상 실제 시그니처를 레포 내 동일 사용처로 교차확인.
5. 오토컨피그 3단 토글 + 빈 `@ConditionalOnMissingBean`. 런타임 개수 가변 빈은 `ImportBeanDefinitionRegistrar`.
6. **테스트**: 핵심 알고리즘 단위 + 오토컨피그 로딩(ApplicationContextRunner enabled/disabled, 서블릿 한정이면 WebApplicationContextRunner). compileOnly 타입(중첩/반환타입 포함) 전부 test 재선언. MapperScan+MyBatis 결합은 백오프까지. 외부연동은 WireMock(standalone). 검증 `./gradlew :…:test (+:framework-archtest:test) (+spotlessApply)`.
7. 드롭인: 변경 파일 전부 → 한 zip, 루트에서 `unzip -o`. 문서 5종 동기화.

<!-- 갱신 끝 -->
