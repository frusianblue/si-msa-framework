# STACK.md — 라이브러리 / 플러그인 관리

> 목적: 무엇을, 왜, 어디에, 어떤 버전으로 쓰는지 한곳에서 추적한다.
> 단일 버전 소스는 `gradle/libs.versions.toml`. **버전을 바꿀 땐 카탈로그를 고치고 이 표를 갱신**한다.
> 최종 갱신: 2026-06-04 · 갱신자: SAML IdP-initiated SLO 수신(6.2-A) 세션

---

## 1. 버전 정책
- 모든 버전은 `gradle/libs.versions.toml` 가 단일 소스. (`gradle.properties` 의 버전 라인은 제거)
- 루트 `build.gradle` 의 `ext { }` 브리지로 기존 모듈의 `${...Version}` 참조가 그대로 동작 → 모듈은 `libs.*` 로 점진 이관.
- Boot BOM(`spring-boot-dependencies`)이 관리하는 라이브러리는 **버전을 적지 않는다**(BOM 위임). 카탈로그엔 BOM 밖 라이브러리/플러그인만 버전 명시.

## 2. 적용된 Gradle 플러그인
| 플러그인 id | 버전 | 용도 | 적용 위치 |
|---|---|---|---|
| `org.springframework.boot` | 4.0.6 | Boot 빌드/패키징(bootJar, 레이어, bootBuildImage, SBOM) | 루트(apply false) → 서비스 모듈 |
| `io.spring.dependency-management` | 1.1.7 | BOM 기반 의존성 버전 관리 | 전 모듈 |
| `java-library` | (내장) | 라이브러리 모듈 컴파일 | 전 서브프로젝트 |
| `jacoco` | (내장) | 테스트 커버리지 → Sonar 연동 | 전 서브프로젝트 |
| `com.diffplug.spotless` | 8.5.1 | 코드 포맷(**Palantir** Java Format) 게이트 — Java(subprojects) + 루트에서 gradle/yaml/sql/md. **설정 캐시 호환 위해 `lineEndings=UNIX` 고정**(GIT_ATTRIBUTES 는 직렬화 불가). `docs/SPOTLESS_NOTES.md` | 전 서브프로젝트 + 루트 |
| `org.owasp.dependencycheck` | 12.1.0 | 의존성 CVE 스캔(CVSS 7.0+ 빌드 실패) | 루트(aggregate) |
| `org.sonarqube` | 6.0.1.5171 | 정적분석/보안 핫스팟/커버리지 수집 | 루트 |
| `org.flywaydb.flyway` | 11.15.0 | CI 마이그레이션(flywayValidate/Info/Migrate) | user/admin 서비스 |

> 플러그인 최신 버전 확인: https://plugins.gradle.org/ (플러그인 id 검색)

## 3. 런타임 라이브러리
### 3.1 BOM 밖(카탈로그에서 버전 고정)
| 라이브러리 | 버전 | 용도 | 적용 위치 |
|---|---|---|---|
| `mybatis-spring-boot-starter` | 4.0.1 | MyBatis 연동 | framework-mybatis |
| `io.jsonwebtoken:jjwt-*` | 0.12.6 | JWT 발급/검증 | framework-security · services/gateway(엣지 인증, 동일 secret 공유) · framework-oauth-client(OIDC id_token 검증 — security `api` 전이 재사용, 테스트만 impl/jackson `testRuntimeOnly`) |
| `springdoc-openapi-starter-webmvc-ui` | 3.0.3 | Swagger UI / OpenAPI | framework-openapi |
| `org.mapstruct:mapstruct(+processor)` | 1.6.3 | DTO 컴파일타임 변환(런타임 비용 0) | framework-commoncode 등 |
| `org.projectlombok:lombok-mapstruct-binding` | 0.2.0 | Lombok+MapStruct 병행 | (MapStruct 쓰는 모듈) |
| `software.amazon.awssdk:bom` / `s3` | 2.31.0 | S3 저장소 (Spring Cloud AWS 회피). **presigned PUT/GET 용 `S3Presigner` 도 이 `s3` 아티팩트에 포함**(별도 의존 불필요) | framework-file-s3 |
| `com.github.gavlyukovskiy:datasource-proxy-spring-boot-starter` | 2.0.0 | SQL 디버깅(바인딩 값/슬로우쿼리) — **Boot 4 는 2.0.0+** | 서비스(개발) |
| `org.apache.poi:poi-ooxml` | 5.5.1 | Excel 업/다운로드(XSSF/SXSSF/HSSF) — **Boot BOM 미관리**(여기서 고정), 모듈 내부 implementation | framework-excel(선택) |
| `org.apache.tika:tika-core` | 3.1.0 | 업로드 콘텐츠 타입 매직넘버 검출 — **Boot BOM 미관리**(여기서 고정). file 에 `compileOnly`(선택 의존·가드된 인스턴스화) + test. 검출만이라 tika-core 단독(파서 모듈 불필요) | framework-file(선택, `validation.content-type-detection=true` 시) |
| `com.github.librepdf:openpdf` | 2.0.2 | PDF 산출물 생성(거래내역서/통지서) — **Boot BOM 미관리**(여기서 고정), 모듈 내부 `implementation`. 라이선스 LGPL-2.1/MPL-2.0(iText 5+ AGPL 회피). **패키지 `com.lowagie.text`(2.x); 3.0+ 는 `org.openpdf` 로 리네임 → 의도적으로 2.x 고정.** 한글은 TTF IDENTITY_H 임베딩 | framework-pdf(선택) |
| `org.apache.sshd:sshd-core` / `sshd-sftp` | 2.16.0 | SFTP(원격 SSH) 파일 저장 — 순수 JDK SSH 가 없어 필수. **Boot BOM 미관리**(여기서 고정), 모듈 내부 `implementation`(전이 금지). **3.0.0 은 마일스톤·2.x 와 API 비호환 → 안정 2.x 고정**. `sshd-common` 은 `sshd-core` 가 전이 | framework-file-sftp(선택, `storage.type=sftp` 시) |
| `com.google.zxing:core` | 3.5.4 | QR 코드 인코딩(`BitMatrix` 생성) — **Boot BOM 미관리**(여기서 고정), 모듈 내부 `implementation`(전이 금지). **렌더링은 JDK ImageIO 직접 → `zxing-javase` 미사용**(의존성 1개로 최소화). Java 8+ 호환·전이 런타임 의존 0 | framework-qr(선택, `framework.qr.enabled=true` 시) |

### 3.2 BOM 관리(버전 미명시)
| 라이브러리 | 용도 | 적용 위치 |
|---|---|---|
| `spring-boot-starter-web/validation/actuator` | 웹/검증/관측 | framework-core |
| `spring-boot-starter-aspectj` | AOP (Boot 4: starter-aop 개명) | framework-core |
| `spring-boot-starter-cache` + `caffeine` | 공통 캐시 | framework-core |
| `micrometer-tracing-bridge-otel` | 분산추적(traceId/spanId) | framework-core, gateway |
| `spring-boot-starter-security` | 인증/인가 | framework-security |
| `spring-boot-starter-data-redis` | Redis TokenStore + 로그인 시도 잠금 저장소(다중 인스턴스 공유) | framework-redis(선택) |
| `org.springframework.kafka:spring-kafka` | 신뢰성 발행(Transactional Outbox 릴레이) + saga 리플라이 소비(`ConsumerRecord`) — Boot BOM 관리(버전 미명시) | framework-messaging(선택) · framework-saga(선택, compileOnly) |
| `spring-boot-starter-batch` | 배치 실행/리스너(Spring Batch 6 — Boot 4) — Boot BOM 관리 | framework-batch(선택) |
| `spring-boot-starter-quartz` | Quartz cron 스케줄러(기본 RAM JobStore) — Boot BOM 관리 | framework-batch(선택) |
| `spring-boot-starter-mail` | 메일 채널(JavaMailSender, jakarta.mail) — Boot BOM 관리 | framework-notification(선택) |
| `spring-boot-starter-flyway` + `flyway-database-postgresql` | DB 마이그레이션(PG 10+) | 서비스 |
| `org.postgresql:postgresql` | 운영 DB 드라이버(프레임워크는 미포함) | 각 서비스 |
| `com.h2database:h2` | 로컬/테스트 DB | 각 서비스 + framework-idempotency·**commoncode·file** test(JdbcIdempotencyStore/enabled 매퍼 슬라이스 실DB 검증, `testRuntimeOnly`) |
| `spring-cloud-starter-gateway-server-webflux` | API 게이트웨이(Boot 4 아티팩트) | gateway |
| `spring-cloud-starter-circuitbreaker-reactor-resilience4j` | 회로차단 | gateway |
| `spring-security-saml2-service-provider` | SAML 2.0 SP(외부 SAML IdP 연동) — **Spring Security(=Boot import) 관리, 버전 미명시** | framework-saml-sp(선택) |
| `spring-security-oauth2-authorization-server` | OAuth2/OIDC Authorization Server(OP) — **SS7 흡수, Boot/Security BOM 관리, 버전 미명시**(오버라이드 불가, 카탈로그 미등록). 전이로 Nimbus JOSE(JWKS)·spring-security-oauth2-jose. **Jackson 3 기본**(`tools.jackson.*`) | services/auth-server(선택 배포) |
| `org.opensaml:opensaml-*`(core/saml-api/saml-impl) | SAML XML 서명·marshalling(위 SP 가 **전이로** 끌어옴, 버전=SS 관리, 명시 핀 금지) — ⚠️ **OpenSAML 4+ 는 Maven Central 에 없음** → 루트 `build.gradle` 의 **Shibboleth 저장소**(`build.shibboleth.net/maven/releases`, `org.opensaml`/`net.shibboleth` 그룹 한정)에서 해소. **이 프레임워크 최초의 비-Central 저장소.** | framework-saml-sp(선택, 전이) |
| `spring-boot-starter-data-redis`(saml-sp) | redis AuthnRequest 저장소(6.1, `request-repository: redis`) — `StringRedisTemplate`. **`compileOnly`+test 재선언**(Boot BOM 관리, 부재 시 guard 빈 fail-fast) | framework-saml-sp(선택) |
| *(SLO 6.2-A: 새 의존성 0)* | IdP-initiated SLO 수신은 **SAML 본체를 SS `saml2Logout` 기본구현에 위임** → 우리 기여물(SPI·`SamlSloService`·`SamlSloLogoutHandler`·`LoginService.logoutAllByUserId`)은 OpenSAML 무의존. slf4j(core 전이)·기존 security 만 사용 | framework-saml-sp + framework-security |

> **공통기능 토대 4종(2026-05: idempotency·i18n·idgen·client) + 보안 완성(framework-audit·framework-secure-web, framework-security 확장)은 새 버전 의존성을 추가하지 않는다.**
> 모두 `framework-core`/`framework-security` + (필요 시) `spring-boot-starter-web/jdbc/data-redis` 를 `compileOnly`(호스트 제공)로만 사용하고,
> 서킷브레이커는 자체 구현, 거부 응답 JSON 은 수기 직렬화(Jackson 비의존). 따라서 `libs.versions.toml` 변경 없음. 향후 messaging(Kafka)/batch 등 BOM 밖 라이브러리가
> 필요한 모듈을 추가할 때 비로소 이 표에 행을 추가한다.
>
> **멱등성 확장(2026-06: JDBC 스토어 + 응답 재생)도 새 의존성 0.** `JdbcIdempotencyStore` 는 `spring-boot-starter-jdbc`(compileOnly), 재생 필터/래퍼는 `spring-boot-starter-web`(compileOnly, `ContentCachingResponseWrapper`/`OncePerRequestFilter`)만 쓰고 재생 저장은 수기 고정 셰이프(`status\ncontentType\nbase64(body)`, Jackson 비의존). 테스트는 `spring-boot-starter-jdbc`/`-web`(testImplementation, **compileOnly 는 test 로 전이 안 됨**) + H2(testRuntimeOnly) — 모두 Boot BOM 관리라 카탈로그 무변경.
>
> **framework-saga(2026-06: 경량 오케스트레이션)도 새 의존성 0.** `api framework-core`(JsonMapper=Jackson3) + `compileOnly framework-messaging`(OutboxEventPublisher) + `compileOnly spring-kafka`(ConsumerRecord, **비전이→의존 서비스가 messaging/kafka 재선언**) + `compileOnly spring-boot-starter-jdbc`(JdbcTemplate/TransactionTemplate). Jackson 읽기는 `readValue(.,Map/Object.class)` 만(JsonNode 메서드명 회피). 순수 코어(상태머신)는 Spring/Jackson 무의존 분리 → JDK 단독 검증. 모두 Boot BOM 관리라 `libs.versions.toml` 무변경.
>
> **framework-lock(2026-06-03: 분산 락 / `@Scheduled` 중복방지)도 새 의존성 0.** `api framework-core`, redis 백엔드는 `compileOnly spring-boot-starter-data-redis`(`StringRedisTemplate`+`DefaultRedisScript` Lua), jdbc 백엔드는 `compileOnly spring-boot-starter-jdbc`(`JdbcTemplate`), `@SchedulerLock` 애스펙트는 core 가 `api` 로 노출하는 `spring-boot-starter-aspectj`(Boot4 에서 starter-aop→starter-aspectj 개명) 전이로 충족. 테스트는 data-redis/jdbc(testImplementation, **compileOnly 비전이**) + H2(testRuntimeOnly) — 모두 Boot BOM 관리라 카탈로그 무변경.
>
> **framework-cache-redis(2026-06-03: 분산 캐시)도 새 의존성 0.** `api framework-core` + `compileOnly spring-boot-starter-data-redis`(`RedisConnectionFactory`/`RedisCacheManager`/`RedisCacheConfiguration`) — 모두 Boot BOM 관리. 값 직렬화는 `RedisSerializer.java()`(JDK), 키는 String → **Jackson2 `GenericJackson2JsonRedisSerializer` 의도적 회피**(Jackson 3 규약). JSON 직렬화가 필요하면 앱이 `RedisCacheConfiguration` 빈 직접 등록(`@ConditionalOnMissingBean` 우선). 테스트는 data-redis(testImplementation, compileOnly 비전이) — BOM 관리라 카탈로그 무변경.
>
> **framework-log-masking(2026-06-03: 개인정보 로그 마스킹)도 새 의존성 0.** `api framework-core`(`MaskingUtils` 재사용) + `compileOnly ch.qos.logback:logback-classic`(`MessageConverter`/`ILoggingEvent` — Boot 기본 로깅이라 런타임 상존). 탐지 정규식·엔진은 순수 JDK(외부 라이브러리 무), Logback 컨버터는 DI 불가라 정적 다리로 연결. 테스트는 logback-classic(testImplementation, compileOnly 비전이) — BOM 관리라 카탈로그 무변경.
>
> **framework-context(2026-06-03: 요청 컨텍스트/멀티테넌시)도 새 의존성 0.** `api framework-core` + `compileOnly spring-boot-starter-web`(`OncePerRequestFilter`/`ClientHttpRequestInterceptor`/servlet API — 호스트 웹앱이 런타임 제공). `RequestContext`/`ContextHolder`/`ContextTaskDecorator` 는 순수 JDK(+slf4j MDC, core 전이). 테스트는 starter-web(testImplementation: `MockHttpServletRequest`/`WebApplicationContextRunner`) — 전부 Boot BOM 관리라 카탈로그 무변경.

> **framework-image(2026-06-03: 이미지 처리)도 새 의존성 0.** `api framework-core` 만 — 리사이즈/썸네일·EXIF orientation 보정·메타 제거 엔진이 전부 **JDK 내장**(`javax.imageio`/`java.awt.image`/`java.awt.geom.AffineTransform`)이라 외부 이미지 라이브러리(thumbnailator/metadata-extractor/TwelveMonkeys 등) 불필요. `ExifOrientation` 은 JPEG APP1/TIFF 를 직접 파싱(메타 라이브러리 무), 메타 제거는 디코드→리인코딩 부수효과. **web 도 불필요**(`@ConditionalOnWebApplication` 미부착 → 배치/MQ 컨슈머 사용). 테스트는 starter-test(testImplementation, 합성 JPEG/PNG 바이트 생성)만 — 카탈로그/ext 무변경.

> **framework-file-batch(2026-06-03: 파일 일괄처리)도 새 의존성 0.** `api framework-core` + `compileOnly framework-image`/`framework-archive`(전이 노출 금지). 병렬은 **JDK 내장** `Executors.newVirtualThreadPerTaskExecutor()`(Java 21 가상스레드) + `java.util.concurrent.Semaphore` 동시도 상한이라 Spring Batch/commons-io/리액터 등 불필요. 변환/압축은 image/archive 에 **위임**(직접 인코딩 안 함) — 해당 모듈이 없으면 그 op 팩토리만 `@ConditionalOnClass` 백오프하고 rename·오케스트레이터는 그대로 동작. 순수 로직(`FileBatchProcessor`/`RenameOperation`/`BatchSafety`)은 Spring 무의존이라 JDK 단독 검증 가능. 테스트만 image/archive `testImplementation` 재선언(백오프/위임 검증).

## 4. 테스트 / 개발 도구
| 항목 | 버전 | 용도 | 적용 위치 |
|---|---|---|---|
| `spring-boot-starter-test` | BOM | JUnit5/AssertJ/Mockito | 전 모듈 test |
| `org.junit.platform:junit-platform-launcher` | BOM | **테스트 발견(launcher) — Gradle 9 에서 필수**(starter-test 가 전이 안 함) | 전 모듈 testRuntime(루트 subprojects 일괄) |
| `spring-boot-testcontainers` | BOM | 실 PostgreSQL 통합테스트(@ServiceConnection) | 서비스 test |
| `org.testcontainers:junit-jupiter` / `postgresql` | BOM | 컨테이너 기동/PG 모듈 | 서비스 test |
| `spring-security-test` | BOM | 보안 테스트 | framework-security test |
| `spring-boot-devtools` | BOM | 핫 리로드(developmentOnly) | 서비스 |

## 5. Boot 4 호환 주의 (되돌리지 말 것)
- **Gradle 8.14+** 필수 (Boot 4 Gradle 플러그인 요구사항).
- **JUnit Platform launcher 명시 필수(Gradle 9)** — `spring-boot-starter-test` 는 `junit-platform-launcher` 를 전이하지 않고, Gradle 9 + 최신 JUnit Platform 에서는 `useJUnitPlatform()` 도 자동 주입하지 않는다. 누락 시 테스트가 있는 모듈에서 `OutputDirectoryCreator not available ... unaligned versions of junit-platform-engine and junit-platform-launcher` 로 **테스트 발견 단계에서 실패**(어서션 이전). → 루트 `build.gradle` 의 `subprojects { dependencies { testRuntimeOnly 'org.junit.platform:junit-platform-launcher' } }` 로 일괄 적용(버전은 Boot BOM).
- **Jackson 3** (`tools.jackson.*`) — 커스터마이저는 `JsonMapperBuilderCustomizer`, 매퍼는 `JsonMapper`. ⚠️ `com.fasterxml.jackson.core/databind` import 금지(클래스패스에 없음 → 컴파일 에러; 특히 `com.fasterxml.jackson.databind.ObjectMapper`). 단 **애너테이션**(`@JsonInclude` 등)은 Jackson 3 에서도 `com.fasterxml.jackson.annotation` 패키지 유지 → OK. 필터/인프라 레벨의 단순 JSON 응답은 Jackson 빈 주입 대신 수기 직렬화가 견고(`SecureWebResponder` 사례).
- **Spring Security 7** — `AuthorizationManager.authorize()` (구 `check()` 제거).
- **SAML 2.0 SP(framework-saml-sp) — 첫 비-Central 저장소 예외**: SAML 은 XML 서명 검증 때문에 OpenSAML 이 불가피하다(프레임워크의 "새 외부 의존성 0" 원칙 최초 예외). `spring-security-saml2-service-provider` 가 OpenSAML 을 **전이로** 끌어오고 **버전도 Spring Security 가 관리**하므로 opensaml 을 명시 선언/핀하지 않는다(명시 핀은 SS 관리 버전과 어긋날 위험 + 루트 ext 미정의 시 설정 단계 실패). 단 **OpenSAML 4+ 는 Maven Central 에 게시되지 않으므로**(라이선스/면책) 루트 `allprojects.repositories` 에 `https://build.shibboleth.net/maven/releases/` 를 `org.opensaml`/`net.shibboleth` **그룹 한정**으로 추가했다(필수, fallback 아님 — 그 외 의존성은 계속 Central 에서만 해소, saml-sp 미사용 빌드엔 영향 0). SS7 SAML2 DSL 은 `saml2Login`/`saml2Logout`/`saml2Metadata`. `Saml2AuthenticatedPrincipal#getRelyingPartyRegistrationId`+`getAttributes()`(`Map<String,List<Object>>`)로 신원 추출. 멀티 파드 AuthnRequest 상관은 기본 HTTP 세션 → `request-repository: redis`(6.1, 운영 HTTPS) 로 redis 공유해 스티키 세션을 제거하거나 게이트웨이 스티키 세션을 쓴다. redis 저장소는 서버 발급 UUID 쿠키로 상관하며 POST 바인딩 ACS(크로스사이트 top-level POST) 때문에 쿠키가 `SameSite=None; Secure` 여야 한다(`spring-boot-starter-data-redis` 는 `compileOnly`, 부재 시 fail-fast). 직렬화는 `AbstractSaml2AuthenticationRequest` 네이티브/Jackson 대신 고정형 수기 코덱.
- **Authorization Server(services/auth-server) — SAS = SS7 흡수, 버전 BOM·Jackson 3 기본**: `spring-security-oauth2-authorization-server` 는 SS7 에 흡수돼 **버전을 Boot/Security BOM 이 관리**(오버라이드 불가, 카탈로그 미등록 — SAML 의 OpenSAML 저장소 추가 패턴과 다름). **Jackson 3 기본**이라 JDBC 인가 저장(`JdbcOAuth2AuthorizationService`)도 `com.fasterxml` 누수 없음(커스텀 principal 직렬화 시 `SecurityJacksonModules`+`JsonMapper.builder()`+`BasicPolymorphicTypeValidator.allowIfSubType`; 골격은 표준 `User` principal 로 회피). ⚠️ **SS7 패키지 재배치**: config 클래스가 메인 config 모듈로 이동(`OAuth2AuthorizationServerConfiguration`=`...config.annotation.web.configuration`, `OAuth2AuthorizationServerConfigurer`=`...config.annotation.web.configurers.oauth2.server.authorization`), `OAuth2TokenType`=`...oauth2.server.authorization`(`.token` 아님), `applyDefaultSecurity` 제거→`new Configurer()`+`securityMatcher(getEndpointsMatcher()).with(...)` DSL. ⚠️ **SAS POM 이 commons-logging 을 제외(spring-security#18372)** → SF7 은 실제 Apache Commons Logging 사용(spring-jcl 폐지, SF#32459)이라 런타임 `LogFactory` NoClassDefFound → auth-server build.gradle 에 `commons-logging:commons-logging:1.3.5` 명시 재추가(SAS 미사용 모듈 무영향). Flyway 는 H2 이식성 위해 `TIMESTAMP`/평문 INSERT, framework-security 재사용 서비스는 `mybatis.mapper-locations`+빈 RBAC 테이블 필요(상세 HANDOFF §6 SAS 묶음).
- **콘솔 한글 인코딩(테스트 출력)** — JDK 21 은 `file.encoding`=UTF-8 이나 Windows(한국어)는 `stdout/stderr` 가 MS949 로 잡혀 `@DisplayName` 등이 깨진다. 3계층 UTF-8 고정: 테스트 워커=루트 `build.gradle` Test `jvmArgs(-Dstdout/-Dstderr/-Dfile.encoding=UTF-8)`+`defaultCharacterEncoding`, 데몬=`gradle.properties` `org.gradle.jvmargs=...UTF-8`(변경 시 `--stop`), **콘솔 렌더=`gradlew` 클라이언트 JVM → 셸 `GRADLE_OPTS="...UTF-8"`**(데몬 설정만으론 부족).
- **오토컨피그 로딩 테스트** — `ApplicationContextRunner` 는 설정 클래스를 리플렉션 introspect 하며 **모든 @Bean 파라미터/반환 타입을 로드**(@ConditionalOnClass 무관). 그 모듈 `compileOnly` 의존을 **전부** `testImplementation` 재선언 필요(누락 시 `Failed to parse configuration class`). 운영은 Boot ASM 메타데이터라 무관.
- **Gateway** 아티팩트 `spring-cloud-starter-gateway-server-webflux`.
- **Spring Cloud 2025.1.x(Oakwood)** — 2025.0.x 는 Boot 4 비호환.
- **datasource-proxy / p6spy 스타터** — 반드시 `2.0.0+`.
- **Spring Cloud AWS 미사용** — Jackson2 의존 회피 위해 AWS SDK v2 직접 사용.
- **안티바이러스(framework-file `scan/`)** — ClamAV `clamd` 와 **INSTREAM(순수 JDK `java.net.Socket`)** 으로 통신해 **새 외부 의존성 0**(클라이언트 라이브러리 미사용). `scan.enabled=true`+`scan.type=clamav` 옵트인, fail-closed 기본. HTTP Range/presigned 도 신규 의존 없음(Range=JDK NIO, S3Presigner=awssdk:s3 포함).
- Docker 이미지: 레이어 추출 후 엔트리포인트는 `org.springframework.boot.loader.launch.JarLauncher` (구 `java -jar` 대체).
- **Spring 7 `HttpHeaders`** — `MultiValueMap` 미구현. `containsKey→containsHeader`, `keySet→headerNames()`, `forEach/entrySet` 제거. 헤더 다루는 코드 주의.
- **Boot 4 모듈 분리** — `org.springframework.boot.http.client.*` 는 starter-web 컴파일 경로에 없을 수 있음 → RestClient 타임아웃은 spring-web `SimpleClientHttpRequestFactory` 로. `RestTemplateBuilder` 는 `org.springframework.boot.restclient` 모듈.
- **Boot 4 `EnvironmentPostProcessor` 패키지 이동** — `org.springframework.boot.env.EnvironmentPostProcessor` → `org.springframework.boot.EnvironmentPostProcessor`(구버전 deprecated 브리지, 4.2.0 제거 예정). `BootstrapRegistry` 계열은 `org.springframework.boot` → `org.springframework.boot.bootstrap`. EPP 는 **코드 import + `spring.factories` 키 둘 다** 갱신(키 불일치 시 조용히 미등록). 메서드 시그니처는 불변. (framework-observability 적용 완료.)
- **관측(framework-observability) — 새 라이브러리 0**: 메트릭 레지스트리(`micrometer-registry-prometheus`/`-otlp`)·OTel 익스포터(`opentelemetry-exporter-otlp`)는 **전부 Boot BOM 관리**(버전 미명시) → 카탈로그/`STACK` 무변경. 호스트 서비스가 필요할 때만 `runtimeOnly` 로 opt-in. ⚠️ Boot 4 패키지 재편: `MeterRegistryCustomizer` 가 `org.springframework.boot.micrometer.metrics.autoconfigure`(3.x 의 `actuate.autoconfigure.metrics` 에서 이동). 구조화 로그는 Boot4 네이티브(`logging.structured.format`)라 `logstash-logback-encoder` 불필요. OTLP 트레이스 키는 브리지 방식 `management.otlp.tracing.endpoint`(신규 스타터는 `management.opentelemetry.tracing.export.otlp.endpoint`).

## 6. 추천 후보 (미적용 — 필요 시 도입)
| 항목 | 종류 | 용도 | 판단 |
|---|---|---|---|
| `com.github.ben-manes.versions` | 플러그인 | 구버전 의존성 탐지(`dependencyUpdates`) | 권장 |
| `nl.littlerobots.version-catalog-update` | 플러그인 | 위 결과로 카탈로그 반자동 갱신 | 선택(짝꿍) |
| `com.gorylenko.gradle-git-properties` | 플러그인 | `/actuator/info` 에 git commit 노출(배포 추적) | 권장 |
| `com.github.jk1.dependency-license-report` | 플러그인 | OSS 라이선스 목록(SI 납품 산출물) | SI 권장 |
| Boot 내장 SBOM(`/actuator/sbom`) | 기능 | 공급망 보안 SBOM | 권장(별도 플러그인보다 우선) |
| `org.gradle.test-retry` | 플러그인 | CI flaky 테스트 재시도 | 선택 |
| Spring REST Docs | 라이브러리 | 테스트 기반 API 문서 | 선택(공공 산출물) |
| `org.wiremock:wiremock-standalone:3.13.2` | 라이브러리 | 서비스 간 HTTP 연동 테스트 목 | ✅ **도입**(framework-client test). standalone=Boot4 Jetty12.1/Jackson3 충돌 회피 |
| Awaitility | 라이브러리 | 비동기/가상스레드 테스트 검증 | 선택 |
| `com.tngtech.archunit:archunit-junit5:1.4.2` | 라이브러리 | 모듈/레이어 의존 규칙 강제 | ✅ **도입**(테스트전용 `framework-archtest`: 순환·Jackson3·레이어·네이밍·필드주입 7규칙) |
| Error Prone + NullAway | 플러그인 | 컴파일타임 버그/NPE 탐지 | 신규 모듈부터 점진(초기 노이즈 많음) |
| 설정값(YAML) 암호화 | 기능 | yaml 시크릿 `ENC(...)` 자동 복호화 | ✅ **완료(2026-06-03)** — **Jasypt 미도입**. 커스텀 Boot4 `EncryptedPropertyEnvironmentPostProcessor`(+`DecryptingPropertySource`, `spring.factories` 등록) + 기존 `AesCryptoService`(AES-GCM, 마스터키 `AES_SECRET`) 재사용, 토글 `framework.crypto.config-decryption.enabled`(기본 on), 토큰 CLI `CryptoCli`, prod 마스터키 가드 `AesMasterKeySafetyGuard`. **신규 의존성 0·Jackson 무관**. 설계 `docs/archive/NEXT_YAML_PASSWORD_ENCRYPTION.md` |
| 아카이빙/압축 | 모듈 | ZIP/GZIP 묶기·풀기 | ✅ **완료(2026-06-03, `framework-archive`)** — 순수 JDK `java.util.zip`, 스트리밍·zip-slip·압축폭탄 가드. `Archiver` SPI+`ZipArchiver`. **신규 의존성 0.** tar/tar.gz 만 commons-compress 옵트인 후속 |
| 공통 유틸 6종 | 코어 | IO/CSV/고정폭/문자셋/텍스트/컬렉션 | ✅ **완료(2026-06-03, `framework-core/util`)** — Io(가드 스트리밍)·Csv(RFC4180)·FixedWidth(CP949 전문)·Charset(MS949/EUC-KR)·Text(바이트절단)·Collection(chunk). 순수 정적 JDK·**신규 의존성 0** |
| 파일 일괄처리 | 모듈 | 다파일 동일작업 일괄 | ✅ **완료(2026-06-03, `framework-file-batch`)** — 이름변경/변환/압축 일괄, 부분실패 격리·Java21 가상스레드+Semaphore 병렬·드라이런·입력순서 보존. `RenameOperation`/`ImageTransformOperation`/`CompressOperation` + `FileBatchProcessor`(Spring 무의존). image/archive 는 `compileOnly`+`@ConditionalOnClass` 백오프. **신규 의존성 0.** 설계 `docs/archive/NEXT_FILE_BATCH_PROCESSING.md` |

## 7. 버전 확인 / 업데이트 방법
- 의존성 최신 여부: `./gradlew dependencyUpdates` (ben-manes 플러그인 도입 후)
- 플러그인 최신: https://plugins.gradle.org/ 에서 id 검색
- 라이브러리 최신: https://central.sonatype.com/ 또는 https://mvnrepository.com/
- 취약점: `./gradlew dependencyCheckAggregate` → `build/reports/dependency-check-report.html`
- Boot BOM 이 관리하는 버전 확인: `./gradlew :services:user-service:dependencies` 로 실제 해소 버전 확인
