# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**기본기능 갭 잔여 2종 완료** — 선택형 모듈 **`framework-cache-redis`**(분산 캐시: core 의 로컬 Caffeine 을 파드 간 공유 Redis 캐시로 **대체**, `@AutoConfiguration(before=CacheAutoConfiguration)`)와 **`framework-log-masking`**(개인정보 **로그** 마스킹: 자유 텍스트 PII 정규식 탐지 → core `MaskingUtils` 형식 위임, 빈 명시호출 1차 + Logback `%mmsg` 컨버터 2차 방어망) 신설. 둘 다 3단 토글·기본 off, **신규 외부 의존성 0**(spring-data-redis·logback-classic 모두 Boot BOM 관리, compileOnly 비노출). 이로써 분산 락(lock)·PDF(pdf)에 이어 캐시·로그마스킹까지 "기본기능 갭" 정리 완료.

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
### A. framework-cache-redis (분산 캐시)
1. **왜 별도 모듈**: core `CacheAutoConfiguration` 이 Caffeine `CacheManager` 를 `@ConditionalOnMissingBean(CacheManager)+matchIfMissing=true` 로 **항상** 등록 → Boot 네이티브 `spring.cache.type=redis` 는 백오프되어 무력. 그래서 본 모듈이 **core 보다 먼저**(`@AutoConfiguration(before=CacheAutoConfiguration.class)`) Redis 매니저를 올려 core 가 자기 `@ConditionalOnMissingBean` 으로 물러나게 함.
2. **`RedisCacheAutoConfiguration`**: `@ConditionalOnClass(RedisConnectionFactory)`+`@ConditionalOnProperty(framework.cache.redis.enabled=true)`. 빈 `redisCacheConfiguration`(값=**JDK 직렬화 `RedisSerializer.java()`**·키=String·TTL/keyPrefix/null정책, `@ConditionalOnMissingBean(RedisCacheConfiguration)`) · `cacheManager`(`RedisCacheManager.builder(connectionFactory)`+캐시별 `ttls`, `@ConditionalOnMissingBean(CacheManager)`). `@EnableCaching` 재선언 안 함(core 가 이미 켬).
3. **`RedisCacheProperties`**(`framework.cache.redis`): enabled=false·timeToLive=10m·keyPrefix=""·cacheNullValues=true·`ttls` Map<String,Duration>.
4. build.gradle = `api framework-core` + **`compileOnly`+`testImplementation` spring-boot-starter-data-redis**(lock 패턴) + config-processor + starter-test. 테스트 4종(off기본·enabled+mock RCF→RedisCacheManager·앱제공 RedisCacheConfiguration 우선(isSameAs)·**.imports 등록 가드**).

### B. framework-log-masking (개인정보 로그 마스킹)
5. **왜 별도 모듈**: core `MaskingUtils` 는 "값을 이미 아는" 필드 단위 마스킹. 갭은 **자유 텍스트 로그**에 섞인 PII. 본 모듈은 정규식으로 탐지 후 실제 마스킹 모양은 `MaskingUtils` 에 위임(전사 형식 일관).
6. **엔진(Spring 무의존, JDK 단독 검증)**: `MaskingRule`(name+Pattern+Function, `of`/`fullMask`, `Matcher.replaceAll`+`quoteReplacement`) · `KoreanPiiRules`(RRN/PHONE/CARD/EMAIL/ACCOUNT 정규식 상수 + `MaskingUtils::mask*` 위임 팩토리, `defaults()`=card·rrn·phone·email) · `SensitiveDataMasker`(불변, rules+stripNewlines+maxLength, `withDefaults()`, null→null).
7. **Logback 경로(2차 방어망)**: `MaskingSupport`(정적 다리: volatile 마스커, 미설치 시 `withDefaults()` **폴백**) · `MaskingMessageConverter extends MessageConverter`(`%mmsg`, `convert`→`MaskingSupport.mask(super.convert)`) · `LogMaskingInstaller`(InitializingBean/DisposableBean 으로 다리에 설치/해제, 생성자 주입) · `logback-masking.xml`(conversionRule `mmsg` + `*_MASKED` appender, `%msg` 재정의 회피).
8. **오토컨피그/프로퍼티**: `LogMaskingAutoConfiguration`(`@ConditionalOnProperty(framework.log-masking.enabled=true)`+`@EnableConfigurationProperties`). 빈 `sensitiveDataMasker`(프로퍼티→규칙 조립 card→rrn→phone→email→account→customPatterns(`fullMask`), `@ConditionalOnMissingBean`) · `logMaskingInstaller`(`install-converter` 토글, matchIfMissing=true). `LogMaskingProperties`(enabled=false·stripNewlines=true·maxLength=0·installConverter=true·nested Rules{rrn/card/phone/email=true,account=**false**}·customPatterns Map).
9. build.gradle = `api framework-core` + **`compileOnly`+`testImplementation` logback-classic** + config-processor + starter-test. 테스트 3종(`SensitiveDataMaskerTest` 순수JDK 13케이스: 각 PII·account기본off/on·복합·stripNewlines·maxLength·custom·null/빈·비PII·ruleNames순서 / `MaskingMessageConverterTest` mock ILoggingEvent + 폴백 + clear격리 / `LogMaskingAutoConfigurationTest` off·on+설치·install-converter=false·규칙토글+커스텀·**가드**).

### C. 공통 등록/문서
10. **등록/배선**: `settings.gradle` 2종 include(pdf 다음, archtest 앞) · 각 모듈 `META-INF/spring/...AutoConfiguration.imports` · `framework-archtest/build.gradle` 에 2종 project 의존(arch 스캔 대상). 슬라이스 `cache`/`logmask` 신규(core 의 cache 는 `core` 슬라이스 — 충돌/순환 없음 확인).
11. **문서 5종 동기화**: 두 모듈 `README.md` 신설 · 루트 `README.md` · `HANDOFF.md` · `docs/FRAMEWORK_MODULES.md` · `STACK.md`(신규 버전 없음 명시 — 둘 다 의존성 0).

## 현재 상태 (적용/검증)
- ✅ **사용자 환경 빌드 검증 완료(2026-06-03)**: `./gradlew :framework:framework-cache-redis:test :framework:framework-log-masking:test :framework:framework-archtest:test spotlessApply`. **log-masking 테스트 1건 실패→수정**(아래) 후 전부 통과. cache-redis·archtest 는 이상 없음.
  - **수정한 실패**: `LogMaskingAutoConfigurationTest#appliesRuleTogglesAndCustomPatterns` 의 phone-off 단언이 **테스트 결함**이었음 — 같은 테스트에서 `account=true` 를 켜는데, 계좌 정규식(`\d{2,6}-\d{2,6}-\d{2,6}`)이 **구분자 있는 휴대폰 `010-1234-5678` 도 매칭**해 마스킹 → "변경 없음" 단언이 깨짐. 모듈 코드는 정상(계좌가 dash-grouped 숫자열을 잡는 건 의도된 동작). → phone-off 검증을 **구분자 없는 `01012345678`**(휴대폰 규칙만 매칭, 계좌는 dash 필수라 미매칭)로 교체.
  - 재검증: `./gradlew :framework:framework-log-masking:test spotlessApply` → 22 통과.
- 설계상 arch 규칙 통과 예상: `*AutoConfiguration`→`@AutoConfiguration` ✓ / top-level `*Properties`→`@ConfigurationProperties` ✓(중첩 `Rules` 제외) / 필드주입 0(`MaskingSupport` 정적필드는 `@Autowired` 아님 → `NO_CLASSES_SHOULD_USE_FIELD_INJECTION` 무관) / Jackson2 이동패키지 0 / 슬라이스 cache·logmask→core 단방향.
- **신규 외부 의존성 0**: 둘 다 Boot BOM 관리(spring-data-redis·logback-classic) → 카탈로그/ext/STACK 버전 추가 불필요. compileOnly 비노출 + 테스트 재선언(established 패턴).

## 켜는 법
```yaml
framework:
  cache:
    redis:
      enabled: true          # 끄면(기본) core Caffeine 로컬 캐시 그대로
      time-to-live: 10m
      key-prefix: ""
      cache-null-values: true
      ttls: { commonCode: 1h, userProfile: 5m }
  log-masking:
    enabled: true            # 끄면(기본) 빈 미등록
    strip-newlines: true     # CR/LF→공백(로그 인젝션 방지)
    install-converter: true  # Logback %mmsg 컨버터 설치기
    rules: { rrn: true, card: true, phone: true, email: true, account: false }
    custom-patterns: { employeeId: "EMP\\d{6}" }
```
```java
// 분산 캐시: 사용처 코드 불변 — @Cacheable("이름") 만, 로컬/분산은 설정으로 전환(@EnableCaching 은 core).
// 로그 마스킹 1차(확실): SensitiveDataMasker 빈 주입 후 명시 호출(구조화 로그까지 커버).
log.info("AUDIT payload={}", masker.mask(dto.toString()));
// 로그 마스킹 2차(방어망): logback-spring.xml 에 logback-masking.xml include + root 를 CONSOLE_MASKED/FILE_MASKED 로.
```
- 분산 캐시는 호스트 앱에 `spring-boot-starter-data-redis` + Redis 연결 필요(본 모듈은 compileOnly). 캐시 값은 `Serializable`(JDK 직렬화). JSON 필요 시 앱이 `RedisCacheConfiguration` 빈 직접 등록(우선).
- 로그 마스킹은 **Boot 구조화 로깅(observability)**엔 `%mmsg` 가 안 먹음(PatternLayout 우회) → 그 경우 **1차(빈 명시호출)** 로 가려야 함.

## 바로 다음 할 일 (Next)
1. (devops) **CI 게이트**: `:framework-archtest:test` + 전 모듈 `:test` PR 차단 + **멀티모듈 jacoco 집계 리포트**(루트 aggregate).
2. **그릇 정비**: 게이트웨이 런타임 점검(CORS preflight·rate-limit 429) · k8s 멀티서비스/CI-CD(redis/secret/observability ServiceMonitor 실배포).
3. **기본기능 갭 정리 완료**(분산 락=lock, PDF=pdf, 분산 캐시=cache-redis, 로그 마스킹=log-masking). 다음은 갭이 아니라 심화/운영.
4. 파일 후속(선택): 이미지 처리(썸네일/EXIF) · 대용량 스트리밍(HTTP Range/S3 presigned) · 안티바이러스 훅.
5. (선택) 규제특화 잔여(pki/hsm/recon/egov) · saga 단계별 타임아웃/보상 재시도 · 멱등 재생 페이로드 지문.
6. (선택) 캐시 **2단(near-cache+Redis)** 모드(로컬 near-cache 무효화=Redis pub/sub) · 로그 마스킹 커스텀 마스킹 함수 주입 포인트.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **core 가 CacheManager 를 항상 등록한다**: `matchIfMissing=true` 라 Boot 네이티브 redis 캐시는 무력. 분산 캐시는 반드시 `@AutoConfiguration(before=CacheAutoConfiguration.class)` 로 **core 보다 먼저** 매니저를 올려야 적용된다(끄면 core Caffeine 그대로).
- **Redis 캐시 직렬화는 JDK(`RedisSerializer.java()`)**: Spring Data Redis 의 `GenericJackson2JsonRedisSerializer` 는 **Jackson 2** 라 본 스택(Jackson 3) 규약 위반 → 쓰지 않는다(레포 전역: RedisTokenStore/MFA 도 Redis 에 Jackson 미사용). JSON 필요 시 앱이 `RedisCacheConfiguration` 직접 주입(`@ConditionalOnMissingBean` 으로 우선).
- **Logback 컨버터는 DI 불가**: `MessageConverter`/conversionRule 은 Logback 이 직접 인스턴스화 → Spring 빈 주입 불가. 그래서 **정적 다리(`MaskingSupport`)** 에 라이프사이클 빈(`LogMaskingInstaller`)이 마스커를 꽂는 패턴. 부팅 초기/순수 Logback 사용 대비 **미설치 시 기본규칙 폴백**(원문 노출 0).
- **`%msg` 재정의 금지**: Logback 에서 표준 conversionWord(`msg`/`message`) 를 재정의하면 경고 → 별도 단어 **`%mmsg`** 를 둔다.
- **로그 마스킹 오탐 정책**: 자유 로그에서 임의 숫자열 과잉 마스킹은 운영 가독성 저하 → 경계 엄격(`(?<![0-9])…(?![0-9])`, 카드=16자리/4-4-4-4 한정). **계좌는 오탐 커서 기본 off**. 규칙 순서는 자릿수 긴 카드 먼저(상호 오삼킴 방지).
- **계좌 규칙은 dash-grouped 숫자열 전반을 잡는다(설계상 광범위, 2026-06-03 테스트 교훈)**: 계좌 정규식 `\d{2,6}-\d{2,6}-\d{2,6}` 은 구분자(`-`)로 묶인 2~6자리 그룹이면 **휴대폰 `010-1234-5678`·기타 코드도 매칭**한다(그래서 기본 off, 켤 땐 환경의 숫자열 형태 점검 필요). 테스트 교훈: account=on 상태에서 다른 규칙(예: phone off)을 검증할 땐 **구분자 없는 입력**으로 격리해야 계좌 매칭에 오염되지 않는다(`LogMaskingAutoConfigurationTest` 1차 실패가 바로 이 중첩 — 모듈 결함 아님, 테스트 결함이었음).
- **Boot 구조화 로깅엔 %mmsg 미적용**: observability 의 JSON 로그는 PatternLayout 우회 → 컨버터 안 먹음. 그 경로는 1차(SensitiveDataMasker 빈 명시 호출)로 커버(README 명시).
- (지난·유효) BOM 밖 의존=`implementation` 비노출+카탈로그/ext/STACK 고정(이번엔 의존성 0이라 해당 없음) / compileOnly 타입은 test 재선언 / 레지스트레이션 가드(`.imports` union 직접 단언) / 토글 3단 기본 off / Jackson3(`tools.jackson.*`, `.annotation` 만 예외) / 필터·로그 컨버터처럼 컨테이너 밖 컴포넌트는 GlobalExceptionHandler/DI 밖 / Boot4 패키지·외부 라이브러리 리네임 추측 금지 / JUnit launcher·starter-test 모듈마다 / 콘솔 UTF-8(미해결 보류).

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. 순수 로직은 Spring 무의존 코어로 분리해 JDK 단독 검증(예: `SensitiveDataMasker`).
2. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`(+테스트가 그 타입 참조 시 `testImplementation` 재선언), BOM 밖 내부 라이브러리=`implementation`(비노출). 이번 2모듈은 의존성 0(BOM 관리).
3. `settings.gradle`/`imports` 등록. 신규 모듈이면 `framework-archtest/build.gradle` 에 project 의존 추가. **새 오토컨피그는 `.imports` 등록 + 등록 가드 테스트**(미등록=죽은 코드). 신규 top-level 패키지는 슬라이스 충돌/순환 확인.
4. Boot4/Spring7/Jackson3 + 통합 대상 실제 시그니처를 레포 내 동일 사용처/공식 소스로 교차확인(core 의 기존 자동구성·기존 Redis 사용처 패턴 먼저 확인 — 이번에 core CacheAutoConfiguration 의 matchIfMissing 이 설계를 좌우).
5. 오토컨피그 3단 토글 + 빈 `@ConditionalOnMissingBean`. core 보다 먼저 떠야 하면 `before=`. 컨테이너 밖 컴포넌트(Logback 컨버터 등)는 정적 다리+라이프사이클 빈으로 연결.
6. **테스트**: 핵심 알고리즘 단위(JDK) + 오토컨피그 로딩(enabled/disabled) + 등록 가드. 정적 상태 쓰는 테스트는 `@AfterEach` 로 격리.
7. 드롭인: 변경 파일 전부 → 한 zip, 루트에서 `unzip -o`. 문서 5종 동기화. 사용자 환경에서 `./gradlew :…:test :framework-archtest:test spotlessApply` 검증.

<!-- 갱신 끝 -->
