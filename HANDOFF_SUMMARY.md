# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**SI 공통 유틸(`framework-core/util`) 대거 보강** — 한국 공공/금융 SI 에서 반복되는 정적 헬퍼 8묶음(검증·마스킹·날짜/영업일·금액·한글·해시·JSON) 신규/확장. **빈/오토컨피그 없음**(순수 정적), core 전이로 어디서나 사용, **새 외부 의존성 0**(JSON 만 Jackson 3). 체크섬·금액·한글 조합·해시 등 "틀리면 조용히 잘못되는" 로직은 **순수 JDK 로 실제 실행 검증(27케이스 전수 통과)** 후 작성, 회귀 테스트 `CoreUtilsTest` 동봉.

## 최종 갱신
- 일자: 2026-06-02 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
- **신규 유틸(`com.company.framework.core.util`)**:
  - **검증** — `KoreanRegNoUtils`(주민번호 체크섬·외국인번호 형식·**사업자번호**·**법인번호**) · `ValidationUtils`(이메일·휴대폰/유선·**카드 Luhn**).
  - **날짜/영업일** — `DateUtils`(yyyyMMdd↔LocalDate·만나이·기간·월초/말) · `HolidayUtils`(주말+**양력 고정공휴일** 자동, 음력·대체공휴일은 `Set<LocalDate>` **주입식** 영업일 계산: isBusinessDay/next/prev/plusBusinessDays/between).
  - **금액** — `MoneyUtils`(천단위 콤마·**한글 금액** "일금 …원정"·반올림/절사/올림 BigDecimal).
  - **한글** — `HangulUtils`(초성 추출·**초성 검색**·자모 분해/**조합**·**조사 선택**·**한↔영 자판 변환**(두벌식)).
  - **해시/인코딩** — `HashUtils`(SHA-256/512 hex·Base64 표준/URL-safe·Hex).
  - **JSON** — `JsonUtils`(Jackson 3 `tools.jackson.*` 기반 null-safe `toJson`/`fromJson`/`TypeReference`, `JacksonConfig` 규칙 동일).
- **기존 확장** — `MaskingUtils` 에 **주민번호·카드·계좌·주소 마스킹** 추가(기존 이름/이메일/전화 유지, 가산).
- **테스트** — `framework-core/src/test/.../util/CoreUtilsTest.java`(JUnit5+AssertJ, 7 메서드). core 의 `testImplementation spring-boot-starter-test` 로 의존 충분 → **build.gradle 무변경**.
- **문서** — `README.md`("framework-core — SI 공통 유틸" 절 추가) · `HANDOFF.md`(6절 함정 2줄: util/support 구분, 유틸 주의점). **STACK/libs/settings/imports/카탈로그 무변경**(빈·새 라이브러리 0).

## 현재 상태 (적용/검증)
- 신규/변경 파일 모두 repo 반영. 정적 점검 통과(괄호 균형, 패키지=디렉터리, **실제 `com.fasterxml` import 0건** — JsonUtils 의 fasterxml 문자열은 "쓰지 말라"는 주석).
- **알고리즘 실행 검증 완료(JDK source-launch)**: 사업자 124-81-00998·법인 130111-0006246 유효, RRN self-consistent, Luhn(4111…/4532…/위조), 한글금액 123456→"십이만삼천사백오십육", 초성 "안녕하세요"→"ㅇㄴㅎㅅㅇ", 조사, 자모조합(닭+ㅏ→달가·값), 한영 "dkssudgktpdy"→"안녕하세요", sha256("abc") 표준벡터 → **전수 통과**.
- ⚠️ **실제 gradle 컴파일은 미검증**(작성 환경 Maven Central 차단). 특히 **`JsonUtils` 만 런타임 미검증**(Jackson jar 없음) — `tools.jackson.core.type.TypeReference` import 1줄만 받는 쪽에서 확인. 나머지는 순수 JDK 라 안전.
- 받는 쪽: `./gradlew :framework:framework-core:compileJava :framework:framework-core:test` + `./gradlew spotlessApply`(palantir 정렬).

## 켜는 법 (설정 불필요)
- 정적 유틸이라 토글/설정/빈 등록 없음. `import com.company.framework.core.util.*` 후 바로 호출.
- 예) `KoreanRegNoUtils.isValidBusinessNo("124-81-00998")` · `MoneyUtils.toKoreanAmount(50000)`("일금 오만원정") · `HangulUtils.matchesChosung("사과","ㅅㄱ")` · `HolidayUtils.nextBusinessDay(d, extraHolidays)`(음력/대체공휴일은 특일정보 API/사내표에서 주입).

## 바로 다음 할 일 (Next)
1. 받는 쪽 `compileJava`+`test`+`spotlessApply` 확인(특히 JsonUtils 의 Jackson3 import). 음력/대체공휴일 적재 소스 결정(공공데이터포털 특일정보 API → `Set<LocalDate>` 캐싱).
2. **다음 묶음 선택**(이전 세션 후보 유효): **관측(observability)** — core 에 micrometer-tracing-otel 보유, 메트릭/로그 표준화·대시보드 / **규제특화 잔여**(pki/hsm/recon/egov, 해당 사업만) / **JdbcIdempotencyStore**(현재 memory/redis만) / 게이트웨이·k8s·CI-CD 멀티서비스화.
   - (선택) 한글 `korToEng`(역변환) 추가, 날짜 유틸에 한국 분기/반기 헬퍼 등 유틸 추가 보강.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **`util` vs `support` 구분**: `core/util`=상태없는 순수 정적 헬퍼(컨텍스트 무의존) → SI 공통 유틸 자리. `<module>/support`=모듈전용·컨텍스트결합 헬퍼. **core 에 support 없음** — 범용 유틸을 support 로 만들지 말 것. util 은 빈 없음 → `imports` 무변경.
- **외국인등록번호**: 2020-10 이후 검증번호(체크섬) 폐지 → `isValidForeignerNo` 는 **형식만** 검증(체크섬 적용 금지).
- **음력/대체공휴일**: 양력 변환표 필요 → `HolidayUtils` 가 자동계산하지 않고 `extraHolidays` 주입식. 자동 고정은 양력 고정공휴일+주말만.
- **JsonUtils 는 Jackson 3 전용**(`tools.jackson.*`). `com.fasterxml.*` import 금지(주석 언급은 무방). 직렬화 실패는 `BusinessException`(INTERNAL_ERROR/INVALID_INPUT)로 변환.
- **범용 유틸 재발명 금지**: `StringUtils`/`CollectionUtils`/`ObjectUtils` 는 Spring·Commons 사용. 공통엔 한국·도메인·스택(Jackson3) 특화만.
- (기존) MFA=security nullable SPI(`MfaGate`)·`LoginService` 9-arg·`AuthController` `ApiResponse<Object>` · 챌린지 store redis(멀티) · 새 모듈/변경은 `settings.gradle`/`imports` 등록 · BOM 밖 새 라이브러리만 카탈로그 핀 · Jackson3 전역 · Spring7 메일 jakarta.

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규: `framework/framework-<X>/`(config Properties+AutoConfiguration · 도메인 패키지 · imports FQCN). 확장: 기존 모듈에 패키지/빈 추가 + imports 에 새 autoconfig 줄. **단, 빈 없는 순수 유틸은 `core/util` 에 클래스만 추가(오토컨피그/imports 무변경).**
2. `build.gradle`: 능력 전이=`api`, 내부구현=`implementation`, 호스트/선택 의존=`compileOnly`(+test). **BOM 밖 새 라이브러리만** 카탈로그+ext 핀.
3. `settings.gradle`(신규 모듈) / `imports`(새 autoconfig) 등록 잊지 말 것.
4. 코드 전 **Boot4/Spring7/Jackson3 + 외부 라이브러리 API 를 공식 소스(GitHub raw)로 확정**. **틀리면 조용히 잘못되는 알고리즘(체크섬/포맷/조합)은 순수 JDK 로 실제 실행 검증 후 박을 것**(이번 세션 패턴).
5. 오토컨피그: `@AutoConfiguration(afterName=…)` + `@ConditionalOnClass/Property` + 빈 `@ConditionalOnMissingBean`. 교체형 SPI 는 `@ConditionalOnMissingBean(타입)`.
6. 검증: `./gradlew :...:compileJava (+:test) (+spotlessApply)`.
7. 드롭인: 변경 파일 전부(모듈/유틸 + 변경된 기존 파일 + 필요 시 settings/imports/카탈로그·문서) → 한 zip, 루트에서 `unzip -o`.


<!-- 갱신 끝 -->
