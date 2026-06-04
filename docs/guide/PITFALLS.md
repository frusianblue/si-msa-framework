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
- **교훈**: 컴파일 미검증 환경이므로 FQCN/이동은 **공식 소스(GitHub raw 해당 버전 브랜치)** 로 확인하고 고친다. 추측 금지.

## 4. 오토컨피그 / 빈 등록

- ★ **[겪음] `ApplicationContextRunner` 가 `@Bean` 의 모든 파라미터/반환/중첩 타입을 로드** — `@ConditionalOnClass` 무관하게 introspect → 그 오토컨피그의 `compileOnly` 타입을 **전부** `testImplementation` 재선언해야 컨텍스트가 뜸(누락 시 `Failed to parse configuration class`). 운영은 ASM 으로 읽어 무관(테스트 전용 함정).
- **[겪음] `.imports` 등록 누락 = 죽은 코드** — 오토컨피그 클래스만 만들고 `META-INF/spring/...AutoConfiguration.imports` 에 안 넣으면 토글해도 안 켜짐(redis LoginAttempt 사례). 해결: 클래스 + `.imports` + 등록 가드 테스트(`.imports` 를 직접 읽어 단언).
- **[겪음] before-순서로 core 백오프 유도** — core 가 `matchIfMissing=true` 로 항상 등록하는 빈(CacheManager 등)을 대체하려면 `@AutoConfiguration(before=CacheAutoConfiguration)`. 동적 DataSource 는 `ImportBeanDefinitionRegistrar`(BDRPP 는 before-순서 못 지킴).
- **[겪음] 선택 백엔드는 중첩 `@Configuration static` + 클래스레벨 `@ConditionalOnClass`** — 톱레벨 `@Bean` 파라미터가 `compileOnly` 타입이면 Spring 이 클래스 전체를 먼저 로드해 메서드레벨 가드보다 먼저 깨짐.

## 5. 보안 / 인증 / JWT  → 깊은 사례는 [`JWT_STATELESS_PITFALLS.md`](JWT_STATELESS_PITFALLS.md)

- ★ **[겪음] 커스텀 `AuthenticationProvider` 가 `FactorGrantedAuthority` 누락 → OIDC `authenticationTime cannot be null`** — SS7 은 auth_time 을 인증 팩터 `issuedAt` 에서 산출. 해결: `FactorGrantedAuthority.fromAuthority(PASSWORD_AUTHORITY)` 부착 + roles 클레임에서 필터 제외.
- ★ **[겪음/일반] 멀티 인스턴스 + `type=memory` → 상태 미공유** — 토큰스토어·로그인잠금·멱등·락·MFA 챌린지 전부. 운영은 `redis`.
- **[겪음] stateless JWT 폐기 불가(로그아웃)** — jti 블랙리스트 + 짧은 TTL/회전. (5질문 ⑤)
- **[겪음] RBAC deny-by-default 아님** — `resources`/`role_resources` 매핑 없으면 인증 사용자 허용. 보호 자원은 매핑 명시 + `@PreAuthorize`.
- **[겪음/일반] `X-Forwarded-For` 위조 가능** — `login-id-and-ip` 잠금 키는 신뢰 프록시 환경만.
- **[겪음] JWT/AES 시크릿 prod 가드** — placeholder·약한키면 기동 실패(`JwtSecretSafetyGuard`/`AesMasterKeySafetyGuard`). 운영은 강한 키 env 주입.
- **[겪음] SAML SP-initiated SLO ↔ 무상태 충돌** — IdP-initiated 우선, NameID 무상태 추출은 디코더 확장 필요.

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
