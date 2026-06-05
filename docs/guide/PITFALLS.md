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
- ★ **[겪음] 두 번째 `SecurityFilterChain` 빈이 메인 체인을 조용히 억제** — 증상: webauthn/saml 같은 모듈을 켜면 main 보안 체인이 사라져 전 경로 무인증/오작동. 원인: framework-security 메인 체인이 `@ConditionalOnMissingBean(SecurityFilterChain.class)` 가드 → **아무 체인이나 먼저 등록되면** 메인이 백오프. 해결: 추가 체인 모듈은 ① `@AutoConfiguration(after = SecurityAutoConfiguration.class)` 로 **메인이 먼저 등록**되게 하고 ② 추가 체인에는 `@ConditionalOnMissingBean` 을 **달지 않으며** ③ `@Order`(고우선순위, 예 `Ordered.HIGHEST_PRECEDENCE + 50`) + `securityMatcher(경로 한정)` 로 path-한정 체인 + 메인 catch-all 공존. (saml-sp·webauthn 동일 패턴.)

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
- ★ **[겪음] WebAuthn JDBC 영속 클래스는 `spring-security-webauthn` 아티팩트(코어 web 아님)** — `JdbcPublicKeyCredentialUserEntityRepository`/`JdbcUserCredentialRepository`/`WebAuthnRelyingPartyOperations`/`Map*Repository`/`PublicKeyCredentialRpEntity` 전부 이 모듈(`org.springframework.security.web.webauthn.{management,api}`)에 있음(공식 이슈 #18377, start.spring.io 엔 있으나 문서 의존성엔 누락). 이 의존 빠지면 `Jdbc*Repository` 컴파일 불가. 버전은 SS BOM 관리(핀 금지). WebAuthn4J(`com.webauthn4j:webauthn4j-core`)가 전이.
- **[겪음] `http.webAuthn()` 은 `UserDetailsService` 빈을 필수로 요구** — 없으면 `IllegalStateException: Missing UserDetailsService Bean`(부팅 실패). assertion 검증 후 권한 적재를 `WebAuthnAuthenticationProvider(rpOperations, userDetailsService)` 가 함. 패스키 전용 앱도 최소 stub `UserDetailsService` 제공 필요.
- **[겪음] WebAuthn ceremony ↔ 무상태(JWT) 체인 충돌** — ceremony 는 챌린지를 **세션**에 보관 + **CSRF** 필요 → CSRF off·STATELESS 인 주류 체인에 그대로 얹으면 ceremony 실패. 해결: `/webauthn/**`·`/login/webauthn`·token-path 에만 적용되는 세션+CSRF 전용 체인(IF_REQUIRED + `CookieCsrfTokenRepository.withHttpOnlyFalse()`)을 별도로 두고, 성공 후 토큰교환 엔드포인트가 자체 JWT 발급 → 이후 무상태. (webauthn DSL `WebAuthnConfigurer` 는 successHandler 미노출 → 세션→JWT 교환 컨트롤러로 접합.)
- **[일반] WebAuthn 은 HTTPS(SecureContext)에서만 동작** — 브라우저가 HTTP origin 의 `navigator.credentials` 호출 거부(localhost 예외). dev/prod 는 Ingress TLS 전제. rpId(등록가능 도메인) ↔ origin(공개 URL) 불일치 = ceremony 거부 → 멀티서비스에서 정합 일원화.
- **[겪음] WebAuthn JDBC 스키마 = SS 번들 리소스(BLOB)** — 원본 `classpath:org/springframework/security/user-{entities,credentials}-schema.sql`(H2/HSQLDB용 `BLOB`). **PostgreSQL 은 `BLOB`→`BYTEA` 치환 필요**(모듈 `db/webauthn-postgres.sql` 동봉). 컬럼명/구조는 SS 소스 그대로(추측 금지 — `webauthn/src/main/.../Jdbc*Repository` 의 `TABLE_NAME`/`COLUMN_NAMES` 확인).
- ★ **[겪음] 패스키 관리(목록/삭제) — 자체 소유권 비교 금지, SS7 `CredentialRecordOwnerAuthorizationManager`(since 6.5.10) 재사용** — `org.springframework.security.web.webauthn.management` 에 있으며 `authorize(Supplier<Authentication>, Bytes credentialId)` 로 ① 인증 ② credential 존재 ③ `credential.getUserEntityUserId()` == `userEntities.findByUsername(auth.getName()).getId()` 를 한 번에 판정. **소유 아님·미존재를 모두 deny 로 동일 처리** → 우리는 deny 를 `NOT_FOUND(404)` 로 변환해 자격증명 존재 여부를 노출하지 않는다(WebAuthn 명세 §14.6.3 credential id 프라이버시 권고). 자체 equals 비교를 새로 짜면 SS 규약과 드리프트 위험. 사용자 식별 키는 ceremony 와 동일하게 principal username(`Authentication#getName()`) → `findByUsername` → user handle(`Bytes`). `Bytes.equals` 는 base64url 문자열 비교(내용 동등).
- **[겪음] 패스키 관리 엔드포인트의 인증 컨텍스트 경계** — 등록/목록/삭제는 모두 **세션+CSRF 전용 체인**의 `authenticated()` 안(=ceremony 와 같은 컨텍스트). 무상태 JWT 주류만 가진 호출자는 진입 불가(세션 부재) — 의도된 경계. JWT 로그인 사용자가 패스키를 등록/관리하려면 받는 앱이 이 전용 체인에 1차 인증(formLogin/JWT 필터)을 더해 세션을 수립해야 함(향후 슬라이스). 삭제(상태변경)는 전용 체인 CSRF(`CookieCsrfTokenRepository.withHttpOnlyFalse()` 더블서브밋 `X-XSRF-TOKEN`) 필수, 목록(GET)은 안전 메서드라 토큰 불요. 관리 경로는 전용 체인 `securityMatcher` 에 `{credentials-path}`·`{credentials-path}/**` 둘 다 추가(컬렉션+하위리소스).
- **[겪음] `CredentialRecord`/`PublicKeyCredentialUserEntity` 는 인터페이스 → 단위테스트 mock 용이** — 관리 서비스 테스트는 두 리포지토리(`UserCredentialRepository`/`PublicKeyCredentialUserEntityRepository`)를 Mockito mock, `Bytes` 만 실제 인스턴스(concrete final, `new Bytes(byte[])`)로 구성해 웹 컨텍스트/실제 크립토 없이 목록 매핑·소유권 게이트 삭제를 검증(Maven Central 차단 환경 적합). 소유권은 운영과 동일하게 실제 `CredentialRecordOwnerAuthorizationManager(mockRepos)` 를 거치게 한다.
- ★ **[겪음] SS 캡슐화 타입을 "항상 로드되는 핵심 빈"의 필드로 들지 말 것 → 중첩 `@ConditionalOnClass` 별도 빈으로 격리** — MFA WebAuthn 2차에서 SS7 webauthn 타입을 캡슐화한 `MfaWebAuthnSupport` 를 핵심 빈 `MfaService`(항상 생성) 필드로 넣으면, spring-security-webauthn 부재 앱에서 JVM 클래스 검증 시 `NoClassDefFoundError` 위험(webauthn 안 쓰는 모든 앱이 깨짐). 해결: WebAuthn 오케스트레이션/컨트롤러를 `MfaWebAuthnService`·`MfaWebAuthnController` 로 분리하고, autoconfig 의 중첩 `@Configuration static` + 클래스레벨 `@ConditionalOnClass(WebAuthnRelyingPartyOperations.class)` 에서만 빈 생성 → 부재 시 중첩 설정 통째 스킵(클래스 미로드). `@Bean` 파라미터의 compileOnly 타입 로딩 함정(§4)의 "빈을 넘어 도메인 빈까지" 확장판.
- **[겪음] WebAuthn 옵션 직렬화/자격증명 역직렬화는 SS7 Jackson 3 모듈 재사용 — 수기 코덱 불필요** — `WebauthnJacksonModule`(접미사 없음 = Jackson 3, `tools.jackson.*`; `WebauthnJackson2Module` 은 Jackson 2 `com.fasterxml`)이 `PublicKeyCredential{Creation,Request}Options`·`PublicKeyCredential`·attestation/assertion response 전부 커버. SS 정본 패턴 `JsonMapper.builder().addModule(new WebauthnJacksonModule()).build()` 를 **MFA 전용 매퍼**로 보유(글로벌 ObjectMapper 무영향). 제네릭 자격증명(`PublicKeyCredential<Authenticator{Attestation,Assertion}Response>`)은 Jackson 3 `tools.jackson.core.type.TypeReference` 로 구체화(SS 필터의 `ResolvableType.forClassWithGenerics` 와 동등). 옵션/응답 타입(`PublicKeyCredential{Creation,Request}Options`)은 `final class` 라 `readValue(json, Class)` 가능.
- **[겪음] 세션 ceremony ↔ 무상태 MFA 티켓 접합 = challenge 를 티켓에 바인딩** — SS7 RP `authenticate(requestOptions, credential)` 은 발급한 옵션을 검증 시 **다시 제출하는 stateful 방식**(기본은 `*RequestOptionsRepository` 세션 보관). MFA 2차는 세션이 없으므로 발급 옵션 JSON 을 `PendingAuth.webauthnOptionsJson` 으로 발급 티켓에 바인딩 보관했다가 검증 때 복원한다(등록 ceremony 도 단기 등록 티켓 재사용). 소유 판정은 `authenticate` 반환 `userEntity.getName() == ticket.userId`. `Redis*ChallengeStore` 의 고정 셰이프 코덱엔 base64 필드 1개 추가(+하위호환 길이체크). RP 가 등록 시 `userCredentials.save`, 검증 시 서명카운트 `save` 까지 직접 하므로 호출측은 재저장 불요 — MFA 는 `MfaEnrollment(WEBAUTHN, confirmed)` 메타만 기록.
- **[일반/겪음] 멀티서비스 rpId/origin 일원화** — 패스키는 rpId(등록가능 도메인)에 바인딩되고 origin host 는 rpId 와 같거나 하위 도메인이어야 한다(아니면 ceremony 거부). MSA 에서 서비스마다 설정이 갈리면 한 서비스 등록 패스키를 다른 서비스가 검증 못 함. 정책: **전 서비스 동일 `rp-id`(공통 상위 도메인)+동일 `allowed-origins`+credential 저장소 공유+ceremony 전담 서비스**(상세 `docs/guide/WEBAUTHN_RPID_ORIGIN_POLICY.md`). 설정은 서비스별 yml 중복 금지 → Config Server/ConfigMap 단일 출처 주입. 강제 수단으로 `WebAuthnRpSafetyGuard`(jwt-secret 가드 패턴) 가 기동 시 정합 검사 — prod 에서 rp-id 공백·localhost·origin 비어있음·http(localhost 제외)·origin host 가 rp-id 등록가능 도메인 밖이면 부팅 실패, 비-prod 는 경고. `diagnose()` 는 static 으로 분리해 프로파일/부팅 없이 단위 검증. MFA WebAuthn 2차는 동일 RP 빈을 재사용하므로 rpId/origin 이 자동 일관(별도 설정 없음).

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

## 9. 로컬 통합 실행(Docker Compose) · 멀티서비스 배포 정합

> 2026-06-05 로컬 compose 스택(`deploy/compose/`, A안=소스부터 컨테이너 안 Gradle 빌드) 가져오며 발견. ①②는 **k8s `overlays/local` 에도 동일 결함**(kind 배포도 같은 자리에서 깨짐).

- ★ **[겪음] user-service ↔ admin-service 는 같은 DB(sidb) 공유 불가** — 증상: 둘째로 뜨는 서비스가 `relation "users" already exists` 또는 Flyway 체크섬 불일치로 부팅 실패. 원인: 두 서비스 Flyway 가 `locations=classpath:db/migration`·같은 `flyway_schema_history`·**버전·테이블명 충돌**(양쪽 `V1__init` 이 `users/roles/user_roles/resources/menus/...` 동일 생성, user `V1` 은 `IF NOT EXISTS` 도 아님). 해결: **서비스별 DB 분리**(compose 는 admin→`admindb`; 운영/overlay 는 admin 전용 DB 또는 `spring.flyway.table` 분리). 같은 DB 한 스키마에 두 서비스 마이그레이션 금지. [§6 관련]
- ★ **[겪음] auth-server 는 `prod` 단독 부팅 불가(의도된 템플릿)** — 증상: `Parameter 0 of method frameworkAuthenticationProvider … required a bean of type 'com.company.framework.security.auth.Authenticator'`. 원인: `Authenticator` 빈을 만드는 `LocalDemo` 가 `@Profile("local")` → prod 엔 없음(framework 는 `@ConditionalOnBean(Authenticator.class)` 로 앱 제공 전제). 실배포는 프로젝트가 DB/LDAP `Authenticator` 구현을 주입해야 한다. 해결(로컬): `SPRING_PROFILES_ACTIVE=local,local-postgres`(demo/demo + demo-web/demo-service 클라이언트) + 실 PG. user-service 는 자체 `DbAuthenticationProvider`(@Component), admin-service 는 Authenticator 의존 없음 → 둘 다 prod OK. [§5 관련]
- **[겪음] `application-local-postgres.yml` 은 datasource URL 을 `localhost:5432` 로 하드코딩** — user-service 의 같은 파일과 달리 `${DB_URL:...}` 자리표시자가 없다. 컨테이너에선 `localhost`=자기 자신이라 PG 연결 실패. 해결: `SPRING_DATASOURCE_URL`(env 최우선) 로 `jdbc:postgresql://postgres:5432/authdb` 덮기. (username/password 는 `${DB_USER}`/`${DB_PASSWORD}` 라 env 로 들어감.)
- **[겪음] compose 다중 서비스 빌드 = 단일 공유 builder 스테이지** — 증상: `up --build` 가 4서비스 병렬 빌드 중 한둘이 Gradle `exit 1`(락/캐시 깨짐). 원인: 서비스마다 `RUN --mount=type=cache,target=/root/.gradle`(동일 id) 를 **동시 쓰기**. 해결: builder 스테이지를 `ARG SERVICE` **무관**하게 만들어(4 bootJar 한 번에 빌드) BuildKit 이 1회만 실행·재사용 → 경합·중복컴파일 동시 제거. 런타임 스테이지만 `ARG SERVICE` 로 JAR 선택.
- ★ **[겪음] auth-server `/actuator/health` 가 보안 체인에 막혀 healthcheck/프로브 영영 실패** — 증상: auth-server 앱은 **정상 기동**(`Tomcat started on port 9000`, `Started AuthServerApplication`)하는데 compose 가 `unhealthy` 로 판정 → `dependency failed to start` 로 gateway/user-service 가 안 뜸(스샷: auth-server 만 X, 나머지는 created/started). 원인: `AuthorizationServerConfig` 의 `@Order(2) defaultSecurityFilterChain` 이 `anyRequest().authenticated()` + `formLogin` 이라 `/actuator/health` 가 인증 뒤로 들어감 → 미인증 curl 은 302(→`/login`)/401 → healthcheck `curl -fsS … | grep -q UP` 가 항상 실패. framework-security 기본 체인(`SecurityAutoConfiguration`)은 `/actuator/**` 를 permitAll 하므로 user/admin/gateway 는 정상이지만, auth-server 가 자체 체인 2개를 정의해 framework 체인을 백오프시키면서 이 규약이 빠졌다. **같은 코드라 k8s `deployment-hardening.yaml` 의 startup/liveness/readiness(`/actuator/health{,/liveness,/readiness}`)도 동일하게 깨진다**(kind 올리면 pod 가 Ready 못 됨). 해결: auth-server `defaultSecurityFilterChain` 에 `requestMatchers("/actuator/**").permitAll()` 추가(framework-security 와 동일 규약). **헬스체크/프로브 우회가 아니라 앱을 고치는 게 정답**(한 줄로 compose+kind 동시 해소). 점검: `curl -i http://localhost:9000/actuator/health` 가 302/401 이면 이 함정. [§5 보안 관련]\n- ★ **[겪음] 컨테이너에서 logback 파일 appender 가 `./logs` 못 써서 user/admin 부팅 실패** — 증상: `[user-service]`/`[admin-service]` 가 기동 중 종료(`compose ps` 에서 사라짐), 로그에 `Suppressed: java.io.FileNotFoundException: ./logs/app.log (No such file or directory)`·`./logs/app-audit.log` + `LoggingApplicationListener.initializeSystem` 스택(Boot 가 logback 설정 에러를 치명으로 처리). 원인: `framework-core/logback-common.xml` 이 **프로파일 무관 항상** `${LOG_DIR:-./logs}/${APP}.log`·`${APP}-audit.log` 롤링 appender 를 켬(로컬 dev 는 프로젝트 폴더에 `./logs` 생겨 무탈). 컨테이너는 ① WORKDIR `/application` 이 root 소유 + 앱은 비루트(uid 1001) → `./logs` 생성 불가, ② **k8s 는 `readOnlyRootFilesystem: true` + 쓰기 가능 경로가 `/tmp` emptyDir 뿐** → Dockerfile 로 logs 디렉터리를 만들어줘도 못 씀. **왜 user/admin 만**: auth-server/gateway 는 `logback-spring.xml` 미포함(Boot 기본=콘솔만) + 로컬 계열 프로파일이라 무관. 해결: **`LOG_DIR=/tmp` env 로 덮기**(compose=user/admin env, k8s=`deployment-hardening.yaml` 의 `app` 컨테이너 env 단일 정의 → 전 서비스 공통, /tmp emptyDir 와 일치). logback 이 `${LOG_DIR}` 를 OS env 에서 해석하므로 코드/Dockerfile 무변경. ⚠️ /tmp 는 휘발(emptyDir) → **k8s 운영 로그/감사는 stdout(CONSOLE, root·AUDIT 둘 다 이미 연결됨) 수집 + DB/SIEM 연계가 정석**(logback-common 주석도 명시); 파일은 보조. 향후 더 깔끔히 하려면 컨테이너에선 파일 appender 자체를 끄고 stdout-only 로 가는 게 12-factor 정합(별도 작업). [§8 운영 관련]\n- ★ **[겪음] 컨테이너에서 local FileStorage 가 `./uploads` 못 만들어 user/admin 부팅 실패** — 증상: `BeanInstantiationException: localFileStorage … 파일 저장 기본경로 생성 실패: /application/uploads` → `Caused by: java.nio.file.AccessDeniedException: /application/uploads`(`FileSystemFileStorage.<init>` 가 생성자에서 `Files.createDirectories(basePath)`). 원인: `framework.file.storage.base-path` 기본 `./uploads`(=`/application/uploads`) — logback `./logs` 와 **동일한 컨테이너 쓰기불가 함정**(root 소유 WORKDIR+비루트 앱, k8s 는 readOnlyRootFilesystem). user-service 는 compose 에서 `FILE_STORAGE_TYPE=local` 로 덮어 local 저장소를 쓰고, **admin-service 도 `framework-file` 의존(s3 모듈 미포함)이라 기본 local** → 둘 다 같은 자리에서 깨진다. 해결: **`FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads`**(relaxed-binding 정식 env, yml `${FILE_BASE_PATH}` 매핑 없는 admin 도 듣고 user 의 yml 값도 덮음). ⚠️ /tmp 휘발 → 로컬/테스트 전용; 실서비스 업로드는 **s3(framework-file-s3 주석 해제 + type=s3)** 또는 영속 볼륨(PVC)·NAS 마운트가 정석. **일반 원칙: 비루트+하드닝 컨테이너에선 프레임워크가 작업디렉터리 하위에 만드는 모든 쓰기경로(logs/uploads/임시)를 쓰기 가능한 위치(/tmp emptyDir)로 리다이렉트한다.** [§9 logback 항목과 동근원]\n- ★ **[겪음] prod `token-store.type=redis` 인데 `framework-redis` 모듈 미의존 → `TokenStore` 빈 없음** — 증상: DB/Flyway 까지 통과 후 `UnsatisfiedDependencyException`: `loginService`(user) / `securityFilterChain`(admin, StatelessChainConfig) 의 `Parameter 2 ... required a bean of type 'com.company.framework.security.token.TokenStore' that could not be found`. 원인: user/admin 의 `application-prod.yml` 이 `framework.security.token-store.type: redis`(다중 파드 공유 토큰스토어 의도)인데 **build.gradle 에 `framework-redis` 의존이 없다**. `RedisTokenStore` 와 그 autoconfig 는 framework-redis 에만 있음 → 모듈 부재로 redis 빈 안 생김 + `TokenStoreAutoConfiguration` 의 memory 빈은 `havingValue=memory,matchIfMissing=true` 라 **type=redis(≠memory)면 조건 탈락** + jdbc 도 아님 → **TokenStore 빈이 하나도 없음**. **삼단 토글 위반**: 백엔드 선택(tier3 `type=redis`)을 켜면서 모듈 classpath(tier1)를 안 물린 것. **k8s prod 에서도 동일 결함**(같은 prod 프로파일). 해결: user/admin `build.gradle` 에 `implementation project(':framework:framework-redis')` 추가(이 모듈이 `api 'spring-boot-starter-data-redis'` 라 `StringRedisTemplate`+Redis 자동구성 전이 → `spring.data.redis.host=${REDIS_HOST}` 로 연결). ⚠️ **build.gradle 변경이라 이미지 재빌드(`up -d --build`) 필요**(앞선 env 수정들과 달리). auth-server(memory)·gateway(reactive, local)는 무관해 정상이었음. [삼단 토글 = §4 / DEVELOPER_GUIDE]\n- ★ **[겪음] auth-server prod 에 `Authenticator` 빈 없어 기동 실패 → 프로파일별 인증기 분리**: 증상: prod 부팅 시 `required a bean of type …security.auth.Authenticator`(또는 LoginService 연쇄). 원인: `LocalDemo` 의 demo 인증기가 `@Profile("local")` 이라 prod/dev/K8s 엔 `Authenticator` 빈이 없다(프레임워크는 인증 백엔드를 의도적으로 비워둠 — 프로젝트마다 다름). 해결: `user/ProdAuthenticatorConfig`(`@Profile("!local")` + `@Bean @ConditionalOnMissingBean(Authenticator.class)`)가 authdb `app_user` 기반 `DbAuthenticator` 등록. 함정 2개 동반: ① **인증기는 `@Component` 아니라 `@Configuration`+`@Bean`** 으로 — 컴포넌트 스캔 빈은 `@ConditionalOnMissingBean` 평가 순서가 불안정(@Bean 메서드에서만 신뢰). ② **seed 비번은 `{bcrypt}$2a$…` 포맷 필수** — `BcryptEnforcingPasswordEncoder`(framework-security 위임 인코더)가 `{noop}`/평문은 매칭 거부라, 평문이나 접두 없는 BCrypt 를 넣으면 항상 로그인 실패. (생성: `python -c "import bcrypt;..."` 또는 framework-core `encryptSecret`/BCrypt 도구 → 앞에 `{bcrypt}` 붙임.) MyBatis 는 auth-server 가 map-underscore 미설정이라 `login_id AS loginId` alias 필수, `@MapperScan` basePackages 에 user 패키지 추가(annotationClass=Mapper.class 유지). [§5 보안]\n- **[겪음] 로컬 이미지는 팻JAR `java -jar` 가 단순·견고** — 증상: 컨테이너가 `Error: Could not find or load main class org.springframework.boot.loader.launch.JarLauncher` 로 즉시 종료. 원인: Boot 4 레이어 추출(`jarmode=tools extract --layers`) 레이아웃을 워크디렉터리에 **평탄화 병합**해야 `JarLauncher` 가 클래스패스에 잡히는데 추출본을 하위 디렉터리째 둬서 미스. 해결(로컬): 추출/JarLauncher 대신 bootJar 를 그대로 `ENTRYPOINT ["java","-jar","app.jar"]`. (운영 Dockerfile 의 레이어 캐시 최적화는 별개로 유지.)

---

## 부록: 빠른 자가진단

| 증상 | 먼저 의심 |
|---|---|
| 테스트에서 `package … does not exist` | §1 compileOnly→testImplementation |
| 컴파일 에러 `com.fasterxml…` | §2 Jackson 3 |
| 토글했는데 기능 안 켜짐 | §4 `.imports` 등록 / §7 배선 |
| 멀티 파드에서 잠금/로그아웃 샘 | §5 memory→redis |
| OIDC `authenticationTime cannot be null` | §5 FactorGrantedAuthority |
| `ClassNotFoundException: …loader.launch.JarLauncher` | §9 로컬은 팻JAR `java -jar` |
| `required a bean of type …Authenticator` | §9 auth-server 는 local 데모 프로파일 |
| auth-server 앱은 떴는데 compose `unhealthy`/k8s pod Ready 안 됨 | §9 actuator 가 AS 보안 체인에 막힘(`/actuator/**` permitAll) |
| `FileNotFoundException: ./logs/app.log` 로 user/admin 부팅 실패 | §9 컨테이너는 `LOG_DIR=/tmp`(루트FS 읽기전용·비루트) |
| `AccessDeniedException: /application/uploads` (localFileStorage) | §9 컨테이너는 `FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads` |
| `required a bean of type …security.token.TokenStore` | §9 `token-store.type=redis` 인데 `framework-redis` 미의존(build.gradle 추가) |
| `required a bean of type …security.auth.Authenticator` (auth-server prod) | §9 ProdAuthenticatorConfig(DbAuthenticator, `@Profile("!local")`)·seed 비번 `{bcrypt}` |
| `relation "users" already exists` / Flyway 체크섬 불일치 | §9 user↔admin DB 분리(admindb) |
| `Invalid bound statement` | §6 mapper-locations |
| 기동 시 `NoClassDefFoundError: LogFactory` | §1 SAS commons-logging |
| 한글 깨짐 | §7 콘솔 인코딩 3계층 |
