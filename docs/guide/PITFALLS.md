# 함정·교훈 대장 (Pitfalls & Lessons Ledger)

> **이 문서는 자산이다.** 일반적으로 알려진 문제와 **이 프로젝트를 개발하며 실제로 부딪힌** 문제를 한곳에 누적한다. 같은 에러로 두 번 헤매지 않기 위한 기억 장치다.
> 인증/JWT 깊은 사례는 [`JWT_STATELESS_PITFALLS.md`](JWT_STATELESS_PITFALLS.md), 개발 표준은 [`DEVELOPER_GUIDE.md`](DEVELOPER_GUIDE.md). 세션별 원문 기록(verbose)은 [`../_internal/HANDOFF.md`](../_internal/HANDOFF.md) §6.

## 추가 규칙 (append-only)
- 빌드/런타임 에러나 함정을 만나면 **즉시 여기 한 줄 추가**한다(특히 재발한 것).
- 형식: 분류 태그 **[일반]**(널리 알려진 문제) 또는 **[겪음]**(이 프로젝트에서 실제 발생) + `증상 → 원인 → 해결`.
- 카테고리 안에서 추가. 카테고리가 없으면 새로 만든다. 기존 항목은 지우지 말고 갱신만.

태그: ★ = 여러 번 재발(특히 주의).

---

## 1. 빌드 · 의존성 (Gradle / BOM)

- ★ **[겪음] `compileOnly` 는 test 클래스패스로 전이 안 됨** — 증상: main 은 컴파일되는데 테스트에서 `package … does not exist`. 원인: test 소스셋은 main 의 `compileOnly`(web/jdbc/redis 등)를 못 봄. 해결: 그 의존을 `testImplementation` 으로 **재선언**. (프로젝트 전반에서 가장 자주 재발.)
- **[겪음] 모듈마다 `testImplementation 'org.springframework.boot:spring-boot-starter-test'` 필수** — 루트 `subprojects` 가 깔아주는 건 실행 런처뿐. 누락 시 `package org.junit.jupiter.api does not exist`.
- **[겪음] JUnit Platform launcher 누락(Gradle 9)** — 증상: 테스트 **발견 단계**에서 `OutputDirectoryCreator not available … unaligned versions`. 원인: `spring-boot-starter-test` 가 `junit-platform-launcher` 비전이 + Gradle 9 자동주입 안 함. 해결: 루트 `subprojects { testRuntimeOnly 'org.junit.platform:junit-platform-launcher' }`.
- **[겪음] 새 모듈 추가 시 `settings.gradle` include 누락** — `project '<X>' not found in project ':framework'`. 모듈 폴더 + 완성 `settings.gradle` 을 한 zip 으로 배포.
- **[겪음] BOM 밖 라이브러리는 버전 핀** — POI(`poi`)·OpenPDF(`openpdf` 2.0.2)·MINA SSHD(`sshd` 2.16.0)·Tika(`tika`)·ZXing(`zxing`)는 Spring BOM 밖 → `gradle/libs.versions.toml` 에 고정 + `implementation`(타입 비노출). 신규 추가 시 `STACK.md` 갱신.
- **[겪음] OpenSAML 은 Maven Central 에 없음** — `spring-security-saml2-service-provider` 가 OpenSAML 을 전이로 끌어옴(버전도 SS 관리). 해결: 버전 **핀 금지**(SS 관리), 루트 repositories 에 Shibboleth(`build.shibboleth.net/maven/releases`)를 `org.opensaml`/`net.shibboleth` **그룹 한정** 추가.
- **[겪음] SAS(인가서버)가 commons-logging 제외 → 런타임 `NoClassDefFoundError: LogFactory`** — 컴파일은 통과, 기동만 실패(SF7 은 spring-jcl 폐지). 해결: `implementation 'commons-logging:commons-logging:1.3.5'` 명시.

## 2. Jackson 3 / 직렬화

- ★ **[겪음] `com.fasterxml.jackson.core/databind` import 금지** — 이 스택은 **Jackson 3 (`tools.jackson.*`)**. import 시 컴파일 에러. 예외: `com.fasterxml.jackson.annotation`(애너테이션만) 유지 OK. 필터/인프라 단순 JSON 은 빈 주입 대신 **수기 직렬화**가 견고.
- **[겪음] Redis 값 직렬화에 `GenericJackson2JsonRedisSerializer` 금지** — Jackson 2 라 규약 위반. JDK 직렬화(`RedisSerializer.java()`)+`StringRedisTemplate` 수기. JSON 필요하면 앱이 `RedisCacheConfiguration` 빈으로 Jackson 3 구성.
- **[겪음] `FactorGrantedAuthority` 직렬화는 SS core Jackson 3 모듈이 지원** — `org.springframework.security.jackson.CoreJacksonModule`(allowIfSubType+mixin). 커스텀 직렬화 불필요.

## 3. Spring Boot 4 / SF7 패키지 이동 (★ 추측 금지 — 정본 소스 확인)

- **[겪음] autoconfigure 패키지 분리** — 모듈별 `org.springframework.boot.<module>.autoconfigure.*` 로 이동(jdbc/batch/quartz/mail). `@AutoConfiguration(afterName=...)` FQCN 확인.
- **[겪음] `EnvironmentPostProcessor` 이동** — `org.springframework.boot.env.*` → **`org.springframework.boot.EnvironmentPostProcessor`**. **코드 import + `spring.factories` 키 둘 다** 변경(키만 틀려도 조용히 미등록).
- **[겪음] Spring Batch 6 대이동** — `core.job.*`/`core.launch.JobOperator` 등. `JobLauncher` deprecated → `JobOperator`.
- **[겪음] actuator/micrometer 재편** — `MeterRegistryCustomizer` 가 `org.springframework.boot.micrometer.metrics.autoconfigure` 로.
- **[겪음] `HttpHeaders` 가 `MultiValueMap` 미구현(SF7)** — `containsKey`→`containsHeader`, `keySet`→`headerNames`, `forEach/entrySet` 제거.
- **[겪음] `ClientHttpRequestFactoryBuilder/Settings` 가 starter-web 컴파일 경로에 없음(Boot4 분리)** — RestClient 타임아웃은 spring-web `SimpleClientHttpRequestFactory` 로.
- **[겪음] Spring Session 프로퍼티 네임스페이스 변경(Boot4)** — `spring.session.redis.*` → **`spring.session.data.redis.*`**(autoconfigure 모듈 분리). 구 키는 조용히 무시됨(세션이 메모리로 떨어져 멀티 인스턴스에서 로그인 유실). `spring-session-data-redis` 는 `spring-boot-starter-data-redis` 동반 필요.
- **교훈**: 컴파일 미검증 환경이므로 FQCN/이동은 **공식 소스(GitHub raw 해당 버전 브랜치)** 로 확인하고 고친다. 추측 금지.

## 4. 오토컨피그 / 빈 등록

- ★ **[겪음] `ApplicationContextRunner` 가 `@Bean` 의 모든 파라미터/반환/중첩 타입을 로드** — `@ConditionalOnClass` 무관하게 introspect → 그 오토컨피그의 `compileOnly` 타입을 **전부** `testImplementation` 재선언해야 컨텍스트가 뜸(누락 시 `Failed to parse configuration class`). 운영은 ASM 으로 읽어 무관(테스트 전용 함정).
- **[겪음] `.imports` 등록 누락 = 죽은 코드** — 오토컨피그 클래스만 만들고 `META-INF/spring/...AutoConfiguration.imports` 에 안 넣으면 토글해도 안 켜짐(redis LoginAttempt 사례). 해결: 클래스 + `.imports` + 등록 가드 테스트(`.imports` 를 직접 읽어 단언).
- **[겪음] before-순서로 core 백오프 유도** — core 가 `matchIfMissing=true` 로 항상 등록하는 빈(CacheManager 등)을 대체하려면 `@AutoConfiguration(before=CacheAutoConfiguration)`. 동적 DataSource 는 `ImportBeanDefinitionRegistrar`(BDRPP 는 before-순서 못 지킴).
- **[겪음] 선택 백엔드는 중첩 `@Configuration static` + 클래스레벨 `@ConditionalOnClass`** — 톱레벨 `@Bean` 파라미터가 `compileOnly` 타입이면 Spring 이 클래스 전체를 먼저 로드해 메서드레벨 가드보다 먼저 깨짐.
- **[일반] 기능 모듈 토글 기본값 = OFF(opt-in)** — 대부분의 `framework.<x>.enabled` 는 `matchIfMissing` 없는 `havingValue="true"` 라 **명시 안 하면 비활성**(security 마스터만 `matchIfMissing=true`=ON 예외). README `끄는 법`/`켜는 법` 작성·문서화 시 이 기본값을 추측하지 말고 `@ConditionalOnProperty(matchIfMissing=?)` 로 확인. 3단 스토어 토글(`...store.type=memory|jdbc|redis`)은 redis 백엔드가 framework-redis 에 있을 때만 선택됨(`@ConditionalOnClass(StringRedisTemplate)` + `.imports`).

## 5. 보안 / 인증 / JWT  → 깊은 사례는 [`JWT_STATELESS_PITFALLS.md`](JWT_STATELESS_PITFALLS.md)

- ★ **[겪음] 커스텀 `AuthenticationProvider` 가 `FactorGrantedAuthority` 누락 → OIDC `authenticationTime cannot be null`** — SS7 은 auth_time 을 인증 팩터 `issuedAt` 에서 산출. 해결: `FactorGrantedAuthority.fromAuthority(PASSWORD_AUTHORITY)` 부착 + roles 클레임에서 필터 제외.
- ★ **[겪음/일반] 멀티 인스턴스 + `type=memory` → 상태 미공유** — 토큰스토어·로그인잠금·멱등·락·MFA 챌린지 전부. 운영은 `redis`.
- **[겪음] stateless JWT 폐기 불가(로그아웃)** — jti 블랙리스트 + 짧은 TTL/회전. (5질문 ⑤)
- **[겪음] RBAC deny-by-default 아님** — `resources`/`role_resources` 매핑 없으면 인증 사용자 허용. 보호 자원은 매핑 명시 + `@PreAuthorize`.
- **[겪음/일반] `X-Forwarded-For` 위조 가능** — `login-id-and-ip` 잠금 키는 신뢰 프록시 환경만.
- **[겪음] JWT/AES 시크릿 prod 가드** — placeholder·약한키면 기동 실패(`JwtSecretSafetyGuard`/`AesMasterKeySafetyGuard`). 운영은 강한 키 env 주입.
- **[겪음] SAML SP-initiated SLO ↔ 무상태 충돌** — IdP-initiated 우선, NameID 무상태 추출은 디코더 확장 필요.
- **[겪음] 세션 모드: 컨트롤러 로그인은 세션 고정(fixation) 수동 회전 필요** — 인증 필터가 아닌 `SessionAuthService` 에서 로그인하므로 SS 의 자동 `changeSessionId` 가 안 걸림. 성공 직후 `request.changeSessionId()` 직접 호출(고정 공격 방어).
- **[겪음] 세션 모드: `SecurityContextRepository` 명시 공유** — 컨트롤러에서 컨텍스트를 쓰는 측과 필터 체인이 읽는 측이 **같은 repo**(`HttpSessionSecurityContextRepository`)를 봐야 함. 체인 `securityContext().securityContextRepository(repo)` + 서비스의 `saveContext(...)` 가 동일 빈을 공유하도록 배선. 안 맞으면 로그인 직후 익명 취급.
- **[겪음/일반] 세션 모드 CSRF: SPA 는 XOR/BREACH 핸들러 대신 평문 핸들러** — SS6+ 기본 `XorCsrfTokenRequestAttributeHandler` 는 쿠키의 원시 토큰을 그대로 보내는 SPA 에서 403 유발. `CookieCsrfTokenRepository.withHttpOnlyFalse()` + `CsrfTokenRequestAttributeHandler`(평문) 조합. 로그인/로그아웃 경로는 `ignoringRequestMatchers("/api/*/auth/**")`.
- **[겪음] 인증 모드 분기는 중첩 `@Configuration static` + 클래스레벨 `@ConditionalOnProperty`** — stateless/session 체인을 한 클래스의 메서드 가드로 가르면 양쪽 `SecurityFilterChain` 빈이 동시 introspect 되어 충돌 위험. 모드별 중첩 설정 클래스로 분리하고 `@ConditionalOnMissingBean(SecurityFilterChain.class)` 로 단일 체인 보장.
- **[일반] 세션 모드 멀티 인스턴스는 세션 공유 필수** — 코어만으로는 세션이 인스턴스 로컬 → 라운드로빈 시 로그인 유실. `framework-session`(Spring Session Redis) 추가. 미스컨피그(모듈만 있고 `mode≠session`)는 `SessionStoreSafetyGuard` 가 기동 시 WARN.

## 6. MyBatis / DB

- **[겪음] `@MapperScan` 에 SPI 섞인 패키지면 `annotationClass = Mapper.class`** — 미지정 시 `ConflictingBeanDefinitionException`(commoncode/file). 앱 전용 매퍼 패키지는 plain OK.
- **[겪음] H2(MODE=PostgreSQL) SQL 이식성** — `TIMESTAMPTZ`/`ON CONFLICT`(PG 전용)는 H2 파싱 실패 → `TIMESTAMP`+평문 INSERT 로 양립.
- **[겪음] 감사 로그 DB 적재 3요건** — ① 모듈 의존 ② `store.type=jdbc` ③ `audit_log` 테이블. 하나라도 빠지면 INSERT 실패를 삼키고 WARN 만.
- **[겪음] 멱등/락 JDBC = PK 유니크 + INSERT 충돌(`DataIntegrityViolationException`) 선점** — 트랜잭션/락 불요. UPSERT 금지(UPDATE→0행이면 INSERT)로 H2/PG/Oracle 양립.
- **[겪음] framework-mybatis 재사용 서비스는 `mybatis.mapper-locations: classpath*:mapper/**/*.xml` 필수** — 없으면 `Invalid bound statement (not found)`.

## 7. 테스트

- **[겪음] "빈 등록 ≠ 동작"** — 로그인 잠금 빈이 떴지만 `LoginService` 에 배선 안 돼 무동작(컴파일·기동 정상). 보안 기능은 **막는지**(429/401/403) 회귀 테스트.
- **[겪음] `FilteredClassLoader` 로 클래스 부재 백오프 검증** — 단, 필터한 타입을 참조하는 사용자 설정은 그 컨텍스트에서 로드 불가 → 단일-위임 설정으로 분리.
- **[겪음] deep-stub mock 컨텍스트 러너 회귀** — 오토컨피그에 새 `@Bean` 추가 시, 그 빈이 참조하는 외부 타입 mock 도 `withBean` 으로 제공해야 함(예: `S3Presigner` region null).
- **[겪음] 한글 콘솔(@DisplayName) 깨짐 = 출력 스트림 인코딩** — 테스트워커(build.gradle)·데몬(gradle.properties)·**클라이언트(셸 `GRADLE_OPTS`)** 3계층 UTF-8.
- **[일반] JWT 서명 변조 음성테스트** — 서명 세그먼트 **중간 문자**를 바꿔야 확실히 깨짐(마지막 base64url 은 trailing-bit 무효 가능).

## 8. 운영 / 환경 / IDE

- **[겪음] 게이트웨이 `lb://` 로컬 미해석** — 디스커버리 의존성 없음 → 로컬 직접 호출 또는 정적 URI override, K8s 는 서비스 DNS.
- **[겪음] 프로파일 = local/dev/prod + `local-xx` 오버레이** — `application.yml` 에 DB/Redis 하드코딩 금지. 로그인 우회는 `local,local-noauth`(과거 `local,dev`).
- **[겪음] IntelliJ 에서 새 파일 빨강 = Git untracked** — 빌드 에러 아님. `git add` 로 해소.
- **[겪음] 작업환경 dash 셸은 brace expansion `{a,b}` 미동작** — `for` 루프로.
- **[겪음] 블록 주석 안 `*/` 금지** — `RS*/ES*` 처럼 `*/` 가 주석을 닫아 컴파일 깨짐. `RS/ES 계열` 로. 점검: `grep -nE '\*/[^ ]'`.
- **[겪음] spotless = Palantir(google 아님) + 설정캐시 충돌** — `lineEndings = UNIX` 고정, 루트에도 적용, `encoding 'UTF-8'`. 에러 후 `.gradle/configuration-cache` 비우기.
- **[겪음/일반] SonarQube 는 배선만 돼 있고 서버가 없으면 안 돈다** — 플러그인·`sonar{}`·CI 스테이지 모두 존재. `SONAR_HOST_URL`/`SONAR_TOKEN`(env 자동 인식)만 주면 `./gradlew sonar` 동작. 사용법은 [`ops/SONARQUBE_GUIDE.md`](../ops/SONARQUBE_GUIDE.md).
- **[일반] Sonar 커버리지 0% = JaCoCo XML 누락** — `sonar` 는 재컴파일 안 하고 산출물만 읽음. `test jacocoTestReport` 를 먼저(또는 같은 호출). `sonar.login`(구) → `sonar.token`.
- **[일반] Sonar 가 분석을 올리되 머지를 안 막음** — 현재 CI 는 업로드만. 차단하려면 Gradle `-Dsonar.qualitygate.wait=true` 또는 Jenkins `waitForQualityGate abortPipeline:true`. 단 PR/브랜치 분석은 Server(Developer+)/Cloud 기능 — Community Build 는 main 만.
- **[일반] 새 모듈은 jacocoAggregation 목록에도 추가** — `settings.gradle` include 만으로는 "전체 합산 1장" 커버리지에서 빠진다(루트 `build.gradle` 의 `jacocoAggregation project(...)` 목록에 한 줄). Sonar 글롭 수집은 별개라 안 놓치지만 집계 리포트는 누락됨.
- **[겪음] "완료로 기록" ≠ "레포에 반영"** — 핸드오프/NEXT 문서에 ✅ 완료로 적혀 있어도 실제 master 에 커밋이 안 됐을 수 있다(예: README 실전샘플 — 문서엔 security·redis·session 완료였으나 라이브 레포엔 security·session 만 존재, redis 는 다른 헤더). **세션 시작 시 "기록"이 아니라 "레포"를 기준으로 재검증**한다. 점검 한 줄: `for d in framework/framework-*; do grep -q '^## 실전 사용 예' "$d/README.md" || echo "$d 누락"; done`.

---

## 부록: 빠른 자가진단

| 증상 | 먼저 의심 |
|---|---|
| 테스트에서 `package … does not exist` | §1 compileOnly→testImplementation |
| 컴파일 에러 `com.fasterxml…` | §2 Jackson 3 |
| 토글했는데 기능 안 켜짐 | §4 `.imports` 등록 / §7 배선 |
| 멀티 파드에서 잠금/로그아웃 샘 | §5 memory→redis |
| OIDC `authenticationTime cannot be null` | §5 FactorGrantedAuthority |
| `Invalid bound statement` | §6 mapper-locations |
| 기동 시 `NoClassDefFoundError: LogFactory` | §1 SAS commons-logging |
| 한글 깨짐 | §7 콘솔 인코딩 3계층 |
