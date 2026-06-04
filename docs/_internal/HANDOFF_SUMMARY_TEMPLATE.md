# HANDOFF_SUMMARY_TEMPLATE.md — 세션 인계 요약 템플릿 (복사해서 쓰는 양식)

> **쓰는 법**: 새 세션을 마칠 때 이 파일을 복사해 `HANDOFF_SUMMARY.md` 의 `<!-- 갱신 시작 --> … <!-- 갱신 끝 -->`
> 구간을 통째로 교체한다. `〈…〉` placeholder 만 채우면 된다. **A. 고정 베이스라인**(완료 현황·다음 후보·고정 함정·레시피)은
> 구조가 바뀐 세션에만 손대고, 평소엔 그대로 둔다. 구조/원칙/함정이 바뀌면 `HANDOFF.md`(1·6·7절)도 같이 갱신.
> 문서 지도: `HANDOFF.md`(누적 정본) · `README.md`(사용법) · `STACK.md`(버전) · `docs/FRAMEWORK_MODULES.md`(모듈 카탈로그).

---

## A. 고정 베이스라인 (현재까지 완료 — 2026-06-03 기준)

> 새 세션 시작 시 "지금 어디까지 왔는지" 즉시 파악용. 새 모듈을 끝낼 때마다 이 목록에 한 줄 추가.

**완료 모듈 (전부 선택형, 기본 off `framework.<module>.enabled=false`)**
- 코어/기본: `framework-core`(+ **SI 공통 유틸 `core/util`**: 검증·마스킹·날짜/영업일·금액·한글·해시·JSON — 빈 없는 정적) · `framework-mybatis` · `framework-security`(JWT/RBAC/비번정책·만료·이력·동시로그인) · `framework-openapi` · `framework-redis` · `framework-commoncode` · `framework-file`(+`framework-file-s3`)
- 토대 4종: `framework-idempotency`(memory|redis|**jdbc**, **SPI 에 `remove` 있음**, **응답 재생 replay 모드**) · `framework-i18n` · `framework-idgen` · `framework-client`
- 보안 완성(ISMS-P): `framework-audit`(logging|jdbc|kafka) · `framework-secure-web`
- 데이터/연계(금융): `framework-datasource`(읽기/쓰기 분리 **+ 독립 다중 DB** — 둘은 상호배타) · `framework-messaging`(Outbox 발행+릴레이 **+ 소비자측 멱등 소비**) · `framework-saga`(경량 오케스트레이션 — 중앙 상태 + 역순 보상, messaging Outbox 재사용)
- 업무 생산성: `framework-excel`(POI 스트리밍/양식검증) · `framework-batch`(Batch6+Quartz) · `framework-notification`(메일/SMS/알림톡)
- 규제특화: `framework-mfa`(2단계 인증 — TOTP/OTP + 복구코드, **외부 의존성 0**, security 에 `MfaGate` nullable SPI)
- 운영/관측: `framework-observability`(공통 메트릭 태그 `MeterRegistryCustomizer` · Boot4 네이티브 구조화 JSON 로그 · 메트릭/트레이스 OTLP 익스포터 표준 — 전부 토글·기본 off, **외부 의존성 0**: 레지스트리/익스포터는 호스트 `runtimeOnly` opt-in)

**다음 후보** (택1): 규제특화 잔여(pki/hsm/recon/egov, 해당 사업만) · **그릇 정비**(게이트웨이 폴백·CORS·rate-limit / k8s 멀티서비스·observability ServiceMonitor 실배포 / CI-CD 멀티모듈 파이프라인) · (선택) datasource multi 후속(보조 DB Flyway 자동화·DB별 health/metric 태깅·실DB e2e).

**절대 되돌리지 말 것(고정 함정)**
- **Jackson 3**: `tools.jackson.*` 사용, `com.fasterxml.jackson.databind/core` import 금지(애너테이션 `com.fasterxml.jackson.annotation.*` 만 OK). 정적 JSON 유틸은 `JsonUtils`(`JacksonConfig` 규칙 미러).
- **JUnit Platform launcher**: `spring-boot-starter-test` 가 `junit-platform-launcher` 를 전이하지 않음 + Gradle 9 자동주입 없음 → 루트 `subprojects` 에 `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'` 일괄(없으면 테스트 있는 모듈에서 "OutputDirectoryCreator not available … unaligned versions" 로 발견 단계 실패). **새 모듈에 첫 테스트 넣을 때 재확인.** 런처와 별개로 **테스트 API 는 모듈마다 `testImplementation 'org.springframework.boot:spring-boot-starter-test'`**(JUnit5+AssertJ, BOM 버전) 선언 필수 — 누락 시 `package org.junit.jupiter.api does not exist`/`org.assertj … does not exist` 로 테스트 컴파일 실패(메인 컴파일과 무관).
- **Boot 4 autoconfigure 패키지 분리**: jdbc/batch/quartz/mail 등 `org.springframework.boot.<module>.autoconfigure.*`. `@AutoConfiguration(afterName=…)` 에 정확한 FQCN(공식 소스로 확인).
- **Spring Batch 6**: 패키지 이동(`core.job` / `core.job.parameters` / `core.launch` / `core.listener`), `JobLauncher`→`JobOperator`. 배치 메타테이블 필요.
- **Spring 7 메일 = `jakarta.mail.*`**(javax 아님). JavaMailSender 빈은 `spring.mail.host` 설정 시에만.
- **POI**: BOM 밖(`libs.versions.toml` 핀), `implementation`(비노출), 종료는 `close()`(dispose deprecated).
- **소비자 멱등**: 인메모리는 멀티 인스턴스에서 무력 → **redis 필수**. `x-event-id` 단일 소스(`MessagingHeaders`). 키 선점~핸들러 완료 전 크래시 시 TTL 까지 재배달 스킵 가능(at-least-once 한계).
- **MFA**: security 에 `MfaGate` nullable SPI · `LoginService` 9-arg(기존 생성자 유지) · `AuthController#login` 반환형 `ApiResponse<Object>` · 챌린지 store 멀티=redis 필수.
- **관측(observability)**: `MeterRegistryCustomizer` = Boot4 `org.springframework.boot.micrometer.metrics.autoconfigure`(3.x `actuate.autoconfigure.metrics` 아님; `MeterRegistry/Tag` 는 `io.micrometer.core.instrument.*` 유지). 구조화 로그는 빈 아님 → `EnvironmentPostProcessor`(ConfigData 이후·로깅 초기화 전, `order=LOWEST`, `addLast`=앱 값 우선; 등록은 `META-INF/spring.factories` 의 `EnvironmentPostProcessor` 키, `.imports` 아님). OTLP 트레이스 키 이중: 브리지(core)=`management.otlp.tracing.endpoint`, 신규 스타터=`management.opentelemetry.tracing.export.otlp.endpoint`; 메트릭=`management.otlp.metrics.export.{enabled,url}`(메트릭 OTLP 레지스트리+OTel 메트릭 브리지 동시=중복). 레지스트리/익스포터는 클래스 직접 참조 없이 런타임 classpath → 호스트 runtimeOnly opt-in(모듈 새 의존성 0).
- **SI 유틸 주의**: 외국인등록번호 2020-10 이후 체크섬 폐지(형식만) · 음력/대체공휴일은 `HolidayUtils` 주입식(`Set<LocalDate> extraHolidays`) · 범용 `StringUtils/CollectionUtils` 재발명 금지(Spring 표준).
- 필터에서 `BusinessException` 금지(디스패처 이전) → 수기 JSON. 트랜잭션매니저 새로 정의 금지(Boot 위임). 모듈 간 의존 단방향(이벤트로 디커플). bash `{a,b}` 미동작→`for`(기본 셸 sh).

**검증 한계**: 작성 환경은 Maven Central/Gradle 배포 차단 → **gradle 컴파일 미검증**. 받는 쪽에서 항상 `./gradlew :framework:framework-<X>:compileJava` + `./gradlew spotlessApply`. (정적 점검: 괄호 균형·패키지=디렉터리·Jackson2 import 0 는 작성 시 수행.) 단, **틀리면 조용히 잘못되는 알고리즘**(체크섬/포맷/한글 조합 등)은 이 환경 순수 JDK(source-launch)로 실제 실행 검증 후 박는다.

**모듈 추가/확장 레시피**
1. 신규 `framework/framework-<X>/`(config: Properties+AutoConfiguration · 도메인 패키지 · `META-INF/spring/…AutoConfiguration.imports` FQCN). **컨텍스트 이전(로깅/액추에이터 초기화 전) 동작이 필요하면 `EnvironmentPostProcessor` + `META-INF/spring.factories`**(observability 사례). 확장이면 기존 모듈에 패키지 추가 + imports 에 새 autoconfig 줄.
2. `build.gradle`: 능력 전이=`api` · 내부구현=`implementation` · 호스트/선택 의존=`compileOnly`. **테스트 넣으면 `testImplementation 'org.springframework.boot:spring-boot-starter-test'` 도**(JUnit5+AssertJ; 루트 launcher 는 실행 런처일 뿐 API 아님). "클래스 직접 참조 없이 런타임 classpath 로만" 동작하는 레지스트리/익스포터류는 모듈이 안 받고 호스트가 `runtimeOnly` opt-in. **BOM 밖 새 라이브러리만** `libs.versions.toml`+루트 `ext` 핀.
3. `settings.gradle`(신규 모듈) / `imports`(새 autoconfig) 등록 — 누락 주의.
4. 코드 전 **Boot4/Spring7/Jackson3 + 외부 라이브러리 API 를 공식 소스(GitHub raw)로 확정**(메이저 버전업=패키지 이동 잦음).
5. 오토컨피그: `@AutoConfiguration(afterName=관련 autoconfig)` + `@ConditionalOnClass/Property` + 빈 `@ConditionalOnMissingBean`. 선택 의존 연계는 `@ConditionalOnClass(타 모듈 마커)`+`@ConditionalOnBean`(+compileOnly). 교체형 SPI 는 `@ConditionalOnMissingBean(타입)` 으로 기본구현.
6. 검증: `compileJava` (+`spotlessApply`).
7. 드롭인: 변경 파일 전부(모듈 폴더 + 변경된 기존 파일 + `settings.gradle`/`imports`/필요 시 카탈로그·문서) → 한 zip, 루트에서 `unzip -o`.

---

## B. 이번 세션 (매번 새로 작성 — `〈…〉` 채우기)

### 이번 세션 한 줄 요약
〈무엇을 끝냈는지 한 문장. 핵심 결정/트레이드오프 1개 포함. 새 외부 의존성 유무 명시.〉

### 최종 갱신
- 일자: 〈YYYY-MM-DD〉 · 갱신자: 〈이름〉
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / Jackson 3(tools.jackson.*)

### 무엇을 했나 (Done)
- 〈신규/변경 모듈·클래스 — 책임 한 줄씩. 토글 기본값, 핵심 빈, SPI, 의존성(api/implementation/compileOnly) 명시.〉
- 〈등록/문서: settings.gradle / imports / libs.versions.toml / STACK.md / FRAMEWORK_MODULES.md 중 건드린 것.〉

### 현재 상태 (적용/검증)
- 〈repo 반영 여부. 정적 점검 결과(괄호·패키지·Jackson2). API 를 어디서(GitHub raw 등) 확정했는지.〉
- ⚠️ gradle 컴파일 미검증 → 받는 쪽: `./gradlew :framework:framework-〈X〉:compileJava` + `./gradlew spotlessApply`. 〈런타임 전제(DB 테이블/Redis/Kafka 등) 있으면 명시.〉

### 켜는 법 (application.yml)
```yaml
framework:
  〈module〉:
    enabled: true
    〈하위 토글/필수 설정〉
```
〈주입 빈 이름 + 사용 예시 1줄.〉

### 바로 다음 할 일 (Next)
1. 받는 쪽 컴파일 확인 + spotlessApply. 〈런타임 설정 체크리스트.〉
2. 〈다음 모듈 후보 — A. 베이스라인 "다음 후보"에서 선택 또는 새로 합의된 것.〉
3. 이후: 〈장기 순서 — docs/FRAMEWORK_MODULES.md 4절 참조.〉

### 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- 〈이번에 처음 밟은 API 이동/버전/SPI 변경/타이밍 이슈. 없으면 "없음".〉
- 〈A. 고정 함정에 승격할 만한 항목이면 HANDOFF.md 6절 + 이 템플릿 A 절에도 추가.〉

<!-- 끝. 위 B 절을 HANDOFF_SUMMARY.md 의 갱신 구간에 붙여넣고 〈…〉를 채운다. -->
