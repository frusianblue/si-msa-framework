# FRAMEWORK_MODULES.md — 전 기능 On/Off 모듈 설계서 (금융 우선)

> 목적: 프레임워크의 **모든 기능을 일관된 규약으로 켜고 끄도록** 모듈을 분해하고,
> 공공·금융·일반 SI를 같은 코어로 커버한다. 기존 저장소의 오토컨피그 패턴(`@AutoConfiguration` + `.imports` +
> `@ConditionalOnProperty` + `@ConditionalOnMissingBean`)을 그대로 확장한다.
> 기준일: 2026-05-31

---

## 0. 진행 현황 (2026-06-03)
- ✅ **토대**: framework-idempotency · framework-i18n · framework-idgen · framework-client (선택형, 3단 토글 적용)
- ✅ **보안 완성(ISMS-P)**: framework-security 확장(비번 만료/이력·동시로그인) · framework-audit(접속/감사 로그 적재·조회, logging|jdbc|kafka)
- ✅ **framework-secure-web**: 보안헤더·경로조작 차단·인젝션 스크리닝·CSRF 더블서브밋(필터 계층, XSS 본문은 core)
- ✅ **금융 핵심**: framework-datasource(읽기/쓰기 분리 라우팅 + 독립 다중 DB) · framework-messaging(Transactional Outbox + Kafka 릴레이 **+ 소비자측 멱등 소비**, `x-event-id`↔framework-idempotency) · audit↔messaging 연동(`store.type=kafka`)
- ✅ **업무 생산성**: framework-excel(POI 스트리밍/양식검증) · framework-batch(Batch6+Quartz) · framework-notification(메일/SMS/알림톡) · framework-pdf(PDF 산출물·한글 TTF 임베딩, OpenPDF) · framework-image(리사이즈/썸네일·EXIF 보정·메타 제거, ImageIO·의존성 0)
- ✅ **규제특화 시작**: framework-mfa(2단계 인증 — TOTP/OTP + ISMS-P 복구코드, **외부 의존성 0개**, security 로그인 흐름에 `MfaGate` SPI 로 연결)
- ✅ **운영/관측**: framework-observability(공통 메트릭 태그 `MeterRegistryCustomizer` · Boot4 네이티브 구조화 JSON 로그 · 메트릭/트레이스 OTLP 익스포터 표준, 전부 토글·기본 off). **외부 의존성 0개**(레지스트리/익스포터는 호스트 runtimeOnly opt-in, Boot BOM 관리). k8s 샘플 `deploy/k8s/observability.yaml`
- ✅ **SI 공통 유틸 보강(2026-06-02)**: `framework-core/util` 에 검증(`KoreanRegNoUtils`·`ValidationUtils`)·날짜/영업일(`DateUtils`·`HolidayUtils`)·금액(`MoneyUtils`)·한글(`HangulUtils`)·해시(`HashUtils`)·JSON(`JsonUtils`) 신규 + `MaskingUtils` 확장. 빈/오토컨피그 없는 순수 정적, **새 외부 의존성 0**(JSON 만 Jackson 3). 회귀 테스트 `CoreUtilsTest`. + **빌드 인프라 픽스**: 루트 `subprojects` 에 `testRuntimeOnly junit-platform-launcher` 추가(Gradle 9 에서 테스트 발견 단계 실패 방지, 전 모듈 공통).
- ✅ **멱등성 확장(2026-06-03)**: framework-idempotency 에 **JDBC 스토어**(`store.type=jdbc`, 영속·다중 인스턴스 공유, DDL `db/idempotency-postgres.sql`) + **응답 재생(replay) 모드**(`replay.enabled`, 중복 시 409 대신 저장 응답 재생, 기본 off=하위호환). **외부 의존성 0개**(jdbc/web=compileOnly, H2=test-scope). 코덱·선점·재생 분기 순수 JDK 실행검증.
- ✅ **CORS/Rate-Limit(2026-06-03)**: 게이트웨이=전역 1선(globalcors + Redis RequestRateLimiter), framework-secure-web=직접 노출 서비스의 2선(Spring CorsFilter 옵트인 + 파드-로컬 토큰버킷). 게이트웨이 빌드 통과(런타임 점검 보류).
- ✅ **분산 트랜잭션 오케스트레이션(2026-06-03)**: **framework-saga 신설** — 경량 오케스트레이션 엔진(중앙 상태 + 역순 보상). 단계 커맨드/리플라이는 **기존 messaging Outbox 재사용**(상태변경과 한 트랜잭션=원자적), 상태는 **JDBC 영속**(DDL `db/saga/saga-postgres.sql`), **스턱/재기동 복구 폴러**(`FOR UPDATE SKIP LOCKED`, 옵트인). 전송·멱등 소비는 messaging 담당, 본 모듈은 오케스트레이션만 더함. 상태머신 순수 JDK **15/15** 실행검증, gradle 컴파일 통과(this-escape 경고 1건 수정). **새 외부 의존성 0**(kafka/jdbc=compileOnly, Boot BOM 관리).
- ✅ **테스트 커버리지 전 모듈 확대(2026-06-03)**: 무테스트 라이브러리 모듈 14개(audit·batch·commoncode·excel·file·file-s3·i18n·idgen·messaging·mybatis·notification·openapi·redis·secure-web)에 **오토컨피그 로딩/토글 스모크**(`ApplicationContextRunner`, 서블릿 한정 `WebApplicationContextRunner`) 추가 — `framework.<X>.enabled` 토글을 빈 등록 유무로 검증. introspection 함정에 맞춰 compileOnly 타입(중첩 @Configuration/@Bean 반환 포함) test 재선언(audit=jdbc+web+messaging 등). commoncode/file 은 `@MapperScan`+MyBatis 결합이라 **disabled 백오프**까지(enabled 풀 와이어링은 DB 슬라이스). **신규 테스트 14 + build.gradle 7, 전부 testImplementation → 런타임/배포 영향 0**, 사용자 환경 BUILD 통과. 무테스트 라이브러리 모듈 0. (발견: redis 의 `RedisLoginAttemptAutoConfiguration` 이 `.imports` 미등록 — 다음 후보.)
- ⏭️ **다음 후보**: **redis 레지스트레이션 갭 해소**(`RedisLoginAttemptAutoConfiguration` → `.imports` 등록, 현재 미활성) · commoncode/file enabled 경로 DB 슬라이스 테스트 · (devops) CI 게이트 + 멀티모듈 jacoco 집계 · **그릇 정비**(게이트웨이 런타임 점검·k8s 멀티서비스/CI-CD) · (선택) 규제특화 잔여(pki/hsm/recon/egov) · (선택) 멱등 재생 페이로드 지문(payload hash) · (선택) saga 단계별 타임아웃/보상 재시도·실DB(H2/PostgreSQL) 통합테스트
- ✅ **독립 다중 DB 완료(2026-06-03)**: framework-datasource 에 `multi.*` 추가 — 서로 다른 물리 DB 마다 `<k>DataSource`/`<k>SqlSessionFactory`/`<k>SqlSessionTemplate`/`<k>TransactionManager` 세트를 `ImportBeanDefinitionRegistrar` 로 동적 등록. `@MapperScan(sqlSessionFactoryRef)`/`@Transactional("<k>TransactionManager")` 는 앱이 배선. 라우팅과 **상호 배타**(fail-fast). 새 외부 의존성 0. 분산 원자성은 여전히 XA 대신 Outbox/Saga로.
- ✅ **분산 락 / 스케줄러 리더 선출(2026-06-03)**: **framework-lock 신설** — `DistributedLock` SPI(소유자 토큰 리스 기반 `tryLock/unlock/keepUntil` + 편의 `runIfLocked`), 백엔드 memory(단일JVM·기본)|redis(`SET NX PX` + Lua CAS)|jdbc(PK INSERT 충돌 선점·만료 재획득, idempotency JDBC 패턴 복제, DDL `db/lock-postgres.sql`). **`@SchedulerLock` 애스펙트**로 k8s 다중 파드 `@Scheduled` 중복 실행 방지(ShedLock 동등 `atMostFor`/`atLeastFor`, atLeastFor 는 조기종료 시 `keepUntil` 로 구현, 네이티브). batch(Quartz job-store 클러스터링)와 구분=평범한 `@Scheduled` 갭. **외부 의존성 0개**(redis/jdbc=compileOnly, H2=test-scope). ⚠️ 작성환경 컴파일 미검증 → 받는 쪽 `:framework:framework-lock:test`+`spotlessApply` 확인.
- ✅ **PDF 산출물 생성(2026-06-03)**: **framework-pdf 신설** — 표 기반 `PdfReport`/`PdfColumn` 스펙 → `PdfExporter` 가 OutputStream 스트리밍(거래내역서/통지서). 한글 **TTF IDENTITY_H 임베딩**(`PdfFontProvider`, 폰트 미설정/깨진바이트는 라틴 폴백·생성 성공), 표 헤더 페이지 반복 + 하단 페이지번호, A4/A5/LETTER/LEGAL·가로·여백·폰트크기 토글. 엔진 **OpenPDF 2.0.2**(iText4 LGPL/MPL fork·AGPL 회피, `com.lowagie.text` 패키지; **3.0+ 는 `org.openpdf` 리네임이라 2.x 고정**; BOM 밖 → 카탈로그+ext 고정·`implementation` 비노출). 3단 토글+레지스트레이션 가드. **사용자 환경 컴파일 BUILD 통과 확인(2026-06-03)**.
- ✅ **분산 캐시(2026-06-03)**: **framework-cache-redis 신설** — k8s 다중 파드 공유 캐시. core 의 로컬 Caffeine `CacheManager` 를 Redis 로 **대체**(`@AutoConfiguration(before=CacheAutoConfiguration.class)` 로 core 가 `@ConditionalOnMissingBean` 백오프하도록 먼저 등록). 값=**JDK 직렬화**(`RedisSerializer.java()`, Jackson2 직렬화기 회피)·키=String, 기본 TTL/keyPrefix/null정책 + 캐시별 `ttls`. JSON 직렬화 필요 시 앱이 `RedisCacheConfiguration` 빈 직접 등록(우선). 끄면(기본) core Caffeine. 3단 토글+레지스트레이션 가드. **외부 의존성 0개**(spring-data-redis=compileOnly+test, Boot BOM). **사용자 환경 빌드 검증 완료(2026-06-03)** — `:framework:framework-cache-redis:test` 이상 없음.
- ✅ **개인정보 로그 마스킹(2026-06-03)**: **framework-log-masking 신설** — core `MaskingUtils`(값 단위)의 보완으로 **자유 텍스트 로그**에 섞인 PII 를 정규식 탐지 후 `MaskingUtils` 형식에 위임(전사 일관). 두 경로: **(1)** `SensitiveDataMasker` 빈 명시 호출(구조화 로그까지, 1차) **(2)** Logback `%mmsg` 컨버터(`MaskingMessageConverter`+정적 다리 `MaskingSupport` 폴백+`LogMaskingInstaller`, 패턴 로그 자동, 2차 방어망). 내장 규칙 RRN/카드/휴대폰/이메일(계좌는 오탐 위험 기본 off)+커스텀 정규식. 순수 엔진은 Spring 무의존 JDK 검증. 3단 토글+레지스트레이션 가드. **외부 의존성 0개**(logback-classic=Boot 기본 로깅·compileOnly+test). **사용자 환경 빌드 검증 완료(2026-06-03)** — 테스트 1건(`LogMaskingAutoConfigurationTest` phone-off 단언) 수정 후 22 통과. (실패는 모듈이 아닌 테스트 결함: `account=true` 의 계좌 정규식이 dashed 휴대폰을 잡아 발생 → 검증 입력을 dashless 로 교체. 계좌가 dash-grouped 숫자열을 잡는 건 의도된 동작이라 기본 off.)
- 🏁 **기본기능 갭 정리 종료(2026-06-03)**: 분산 락(lock)·PDF(pdf)·분산 캐시(cache-redis)·로그 마스킹(log-masking) 4종 완료. 이후는 갭이 아니라 심화/운영.
- ✅ **요청 컨텍스트 / 멀티테넌시(2026-06-03)**: **framework-context 신설** — 기본기능 카탈로그(`docs/BASELINE_FEATURES.md`) #5. 요청마다 `RequestContext`(불변·JDK단독, tenantId/userId/locale+확장 attributes)를 `ContextHolder`(정적 ThreadLocal, **상속형 아님** — 가상스레드/풀 누수 방지)에 바인딩(`ContextBindingFilter`, `MdcTraceFilter` 안쪽·+MDC), 종료 시 정리. 전파 **명시 2경로**: `ContextTaskDecorator`(@Async/풀에 컨텍스트+MDC) · `ContextPropagationInterceptor`(아웃바운드 헤더). 해소 전략 `ContextResolver` 교체 가능(기본 `HeaderContextResolver`, `@ConditionalOnMissingBean` → JWT/SecurityContext). 서블릿 한정. 3단 토글+레지스트레이션 가드. **외부 의존성 0개**(servlet/web=compileOnly+test). **사용자 환경 컴파일 BUILD 통과 확인(2026-06-03)**.
- ✅ **이미지 처리(2026-06-03)**: **framework-image 신설** — 기본기능 카탈로그 #7. `ImageProcessor` SPI(`process`/`thumbnail`/`probe`)+`DefaultImageProcessor`(JDK `javax.imageio`+`java.awt`). 비율유지 리사이즈/썸네일(상한 박스·업스케일 옵트인·2배 초과는 단계적 절반축소 고품질), **EXIF orientation 보정**(`ExifOrientation` 순수 JDK JPEG APP1/TIFF 파서 1~8, AffineTransform·5~8 가로세로 스왑), **민감 EXIF(GPS) 제거**(디코드→리인코딩 부수효과로 메타 미보존), 출력 포맷 화이트리스트(JPEG/PNG), **디컴프레션 폭탄 방지**(디코드 전 헤더 픽셀수 검사·기본 40MP), JPEG 알파 흰배경 평탄화, 헤드리스. 웹 비의존(배치 가능). 3단 토글 기본 off+레지스트레이션 가드. **신규 외부 의존성 0개**(web 불필요·엔진 전부 JDK). 엔진 javac 단독 + 기능 하니스 **26/26** 통과, config/배선은 context·pdf 패턴 미러(사용자 Gradle 검증 예정).
- 📋 **기본기능 카탈로그 신설(2026-06-03)**: `docs/BASELINE_FEATURES.md` — 기본기능 10항목 실측 체크(있음/부분/없음 + 위치 + 인수기준). #5 컨텍스트·#7 이미지 완료. 다음 활성=파일 하드닝 묶음(#8+#9+#10: 대용량 스트리밍/presigned·메타 정합성·AV 훅). 추가 요청은 §6 대기열로 수집.
- 🛠️ **환경정비+보안·검증+spotless(2026-06-03)**: 프로파일 **local/dev/prod 통일 + `local-xx` 오버레이**(local-postgres/redis/noauth), **감사 로그 DB 적재 활성화**(`audit_log` 마이그레이션 추가 — store.type=jdbc 의 3요건 중 누락분), **JWT 시크릿 prod 가드**(`JwtSecretSafetyGuard`), **요청 검증 빈틈 보강**(Spring7 `HandlerMethodValidationException`·로그인 `@Valid`), **spotless 다소스 확장**(Java=Palantir + gradle/yaml/sql/md, 설정 캐시 충돌 `lineEndings=UNIX` 해결). 문서 `docs/LOCAL_SETUP.md`·`CHANGES_AND_DEPRECATIONS.md`·`SECURITY_VALIDATION_ADDITIONS.md`·`SPOTLESS_NOTES.md`.
- ⏭️ **다음 최우선 = 설정값(YAML) 패스워드 암호화**: `framework-core` crypto 책임 확장 — 커스텀 Boot4 `EnvironmentPostProcessor` 가 `ENC(...)` 프로퍼티를 기존 `AesCryptoService`(AES-GCM, 마스터키 `framework.crypto.aes-secret`/`AES_SECRET`)로 복호화. **Jasypt 보류**(Boot4 지원 불명확), 신규 의존성 0, Jackson 무관. 설계서 `docs/NEXT_YAML_PASSWORD_ENCRYPTION.md`.
- 표기: ✅ 구현완료 · ⏭️ 다음 · (무표기) 예정. 세션 단위 상세는 `HANDOFF_SUMMARY.md`.

---

## 1. 표준 토글 규약 (모든 모듈 공통 — 이 3단을 반드시 따른다)

| 단계 | 무엇을 켜고 끄나 | 메커니즘 | 효과 |
|---|---|---|---|
| **1단 · 모듈** | 기능 묶음 자체의 존재 | `settings.gradle` include + 프로젝트 `build.gradle` 의존성 추가 → 모듈의 오토컨피그가 `@ConditionalOnClass(마커클래스)` | 의존성을 안 넣으면 클래스 자체가 없어 비용 0 |
| **2단 · 기능** | 모듈 안 개별 기능 | `framework.<module>.enabled` 및 세부 플래그 + `@ConditionalOnProperty` | yml 한 줄로 on/off |
| **3단 · 구현 교체** | 같은 인터페이스의 구현 선택 | `framework.<module>.<x>.type = memory \| redis \| jdbc …` (상호배제) + `@ConditionalOnMissingBean` | 환경별 백엔드 교체, 프로젝트가 빈 등록 시 자동 양보 |

**기본값 규칙**
- **코어 모듈**(core/security/mybatis): 모듈은 항상 탑재, 기능 플래그 기본 `true`. 단 모든 기능에 `enabled` 플래그를 둬 **끌 수 있게** 한다.
- **선택형 모듈**: 의존성을 넣어도 `framework.<module>.enabled` 기본 `false` → **명시적으로 켜야** 동작(안전 기본값).

**오토컨피그 스켈레톤(모든 신규 모듈 동일)**
```java
@AutoConfiguration
@ConditionalOnClass(Xxx.class)                                  // 1단: 모듈 존재
@ConditionalOnProperty(prefix = "framework.xxx", name = "enabled", havingValue = "true")  // 2단
@EnableConfigurationProperties(XxxProperties.class)
public class XxxAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean                                   // 3단: 프로젝트 override 허용
    public XxxService xxxService(XxxProperties p) { ... }
}
```
등록: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 에 FQCN 한 줄.

---

## 2. 전체 모듈 카탈로그

분류 표기 — **[코어]** 항상 탑재(기능별 토글) · **[선택]** 의존성 추가형
규제 표기 — 공통(전부) · 공(공공) · 금(금융)

### 2.1 기존 (재사용)

| 모듈 | 책임 | 대표 토글 | 분류 | 규제 |
|---|---|---|---|---|
| framework-core | 응답/에러/페이징/AOP/로깅/트레이스/XSS/캐시/AES + **SI 공통 유틸(`util`: 검증·마스킹·날짜/영업일·금액·한글·해시·JSON)** | `framework.core.{trace,httpLogging,xss,auditAspect,...}` (util 은 토글 없는 정적) | [코어] | 공통 |
| framework-mybatis | 감사필드·암호화 타입핸들러·CurrentUser | (코어 연동) | [코어] | 공통 |
| framework-security | 인증추상화·JWT·TokenStore·RBAC·비번정책·로그인잠금 | `framework.security.*` | [코어] | 공통 |
| framework-openapi | API 문서 | `framework.openapi.enabled` | [선택] | 공통 |
| framework-redis | Redis TokenStore/LoginAttempt | `...type=redis` | [선택] | 공통 |
| framework-commoncode | 공통코드 CRUD | `framework.commoncode.enabled` | [선택] | 공통 |
| framework-file / -s3 | 파일 저장(로컬/NAS/S3) + **콘텐츠 타입 검증(Tika, 옵트인)·확장자↔MIME 정합·at-rest 암호화(AES)·HTTP Range 스트리밍·S3 presigned PUT/GET·안티바이러스(ClamAV INSTREAM)** | `framework.file.enabled`,`storage.type`,`storage.encrypt`,`validation.content-type-detection`,`validation.enforce-extension-match`,`scan.enabled`,`scan.type` | [선택] | 공통 |

### 2.2 신규 — 토대 (다른 기능의 전제, 먼저 깔 것)

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| ✅ framework-i18n | MessageSource·에러메시지 외부화·다국어 | `framework.i18n.enabled` | [선택]→코어승격 권장 | 공통 |
| ✅ framework-idgen | 채번(Sequence/Table/Snowflake) | `framework.idgen.enabled` + `type` | [선택] | 공통 |
| ✅ framework-client | 외부 API 표준 클라이언트(타임아웃·재시도·서킷·연계로그) | `framework.client.enabled` | [선택] | 공통 |

### 2.3 신규 — 보안 완성 (ISMS-P·보안성 심의 대비, 공통 필수)

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| ✅ framework-security (확장) | 비번 **만료·변경주기·이력(직전 N개 재사용 금지)** | `framework.security.password.{expiry,history}.enabled` | [코어] | 공통 |
| ✅ framework-security (확장) | **동시(중복) 로그인 제어** | `framework.security.concurrent-session.enabled` | [코어] | 공통 |
| ✅ framework-audit | 접속/감사 로그 **DB 적재·조회** 표준(현 AOP 영속화) + **kafka 싱크**(messaging Outbox 발행) | `framework.audit.enabled` + `store.type=logging\|jdbc\|kafka` | [선택] | 공통 |
| ✅ framework-secure-web | 보안헤더·경로조작·인젝션 스크리닝·CSRF 더블서브밋(SQLi 등, XSS는 core) | `framework.secure-web.enabled` (+`headers`/`path-traversal`/`injection`/`csrf`) | [선택] | 공통 |
| ✅ framework-log-masking | **개인정보 로그 마스킹** — 자유 텍스트 로그 PII(주민/카드/휴대폰/이메일, 계좌 기본 off) 정규식 탐지 → core `MaskingUtils` 위임. (1) `SensitiveDataMasker` 빈 명시 호출 (2) Logback `%mmsg` 컨버터(정적 다리+폴백). 외부 의존성 0개(logback=compileOnly) | `framework.log-masking.enabled` (+`rules.*`/`custom-patterns`/`strip-newlines`/`install-converter`) | [선택] | 공통 |
| ✅ framework-context | **요청 컨텍스트 / 멀티테넌시** — 요청마다 `RequestContext`(tenantId/userId/locale+확장 attributes)를 `ContextHolder`(정적 ThreadLocal, 상속형 아님)에 바인딩/정리(+MDC). 전파 명시 2경로: `ContextTaskDecorator`(@Async/풀) · `ContextPropagationInterceptor`(아웃바운드 헤더). 해소 전략 `ContextResolver` 교체 가능(기본 헤더→JWT 대체). 서블릿 한정. 외부 의존성 0개(web=compileOnly) | `framework.context.enabled` (+`tenant-header`/`user-header`/`put-to-mdc`/`propagate-downstream`) | [선택] | 공통(횡단) |

### 2.4 신규 — 업무 생산성 (업무개발자 직접 사용)

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| ✅ framework-excel | POI 업/다운로드: **다운로드 SXSSF 스트리밍**(대용량 일정메모리)·**업로드 양식검증**(헤더/타입/필수/길이·패턴, 행별 오류수집). POI 타입 비노출(implementation) | `framework.excel.enabled` (+`export.window-size`/`import.max-rows`) | [선택] | 공통 |
| ✅ framework-batch | **Spring Batch 6 실행**(JobLaunchSupport: JobOperator 래핑+run.id 재실행보장)·**표준 로깅 리스너**·**Quartz cron 스케줄**(yaml 선언만으로 Job 기동) | `framework.batch.enabled`,`framework.scheduler.enabled` | [선택] | 공통 |
| ✅ framework-notification | **메일/SMS/알림톡 채널 추상화** — NotificationService 가 ChannelType 라우팅. 메일=JavaMailSender, SMS·알림톡=벤더 SPI(SmsClient/AlimtalkClient)+기본 로깅구현(@ConditionalOnMissingBean 교체) | `framework.notification.enabled` + `channels.{mail,sms,alimtalk}.enabled` | [선택] | 공통 |
| ✅ framework-pdf | **PDF 산출물 생성**(거래내역서/통지서) — 표 기반 `PdfReport`/`PdfColumn` → `PdfExporter` OutputStream 스트리밍. **한글 TTF IDENTITY_H 임베딩**(미설정 시 라틴 폴백), 헤더 페이지반복+하단 페이지번호. 엔진 OpenPDF 2.0.2(iText4 LGPL/MPL, `com.lowagie` 패키지·implementation 비노출; 3.0+ `org.openpdf` 리네임이라 2.x 고정) | `framework.pdf.enabled` (+`page-size`/`landscape`/`margin`/`*-font-size`/`page-number`/`font.location`) | [선택] | 공통 |
| ✅ framework-image | **이미지 처리** — `ImageProcessor` SPI(`process`/`thumbnail`/`probe`)+`DefaultImageProcessor`(JDK ImageIO+AWT). 비율유지 리사이즈/썸네일(상한 박스·업스케일 옵트인·2배 초과 단계 축소)·**EXIF orientation 보정**(순수 JDK APP1/TIFF 파서 1~8)·**민감 EXIF(GPS) 제거**(리인코딩 부수효과)·출력 화이트리스트(JPEG/PNG)·**디컴프레션 폭탄 방지**(헤더 픽셀수 검사·기본 40MP)·JPEG 알파 평탄화·헤드리스. 웹 비의존(배치 가능). **외부 의존성 0개**(엔진 전부 JDK) | `framework.image.enabled` (+`default-format`/`thumbnail-max-edge`/`jpeg-quality`/`max-source-pixels`) | [선택] | 공통 |

### 2.5 신규 — 데이터/연계 (금융 핵심 ★)

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| ✅ **framework-idempotency** | **정확히-한번/멱등키**(중복요청·중복결제 차단) + **응답 재생**(중복 시 저장 응답 재생) | `framework.idempotency.enabled` + `store.type=memory\|redis\|jdbc` (+`replay.enabled`) | [선택] | 금 ★ |
| ✅ framework-messaging | Kafka + **Outbox**(발행: 유실/중복 방지) **+ 소비자측 멱등 소비**(`IdempotentEventProcessor`: `x-event-id` 헤더로 중복 배달 1회 처리, 실패 시 키 해제→재배달 재처리). 멱등 저장소는 framework-idempotency(redis 권장) | `framework.messaging.enabled`(+`outbox.relay.enabled`)·`framework.messaging.consumer.enabled` | [선택] | 금 ★ |
| ✅ framework-datasource | **읽기/쓰기 분리 라우팅**(primary/replica) · **독립 다중 DB**(DB키별 SqlSessionFactory/tx매니저 세트, 앱이 `@MapperScan`/`@Transactional` 배선). 두 기능 상호 배타 | `framework.datasource.routing.enabled` / `framework.datasource.multi.enabled` | [선택] | 금/공 |
| ✅ framework-saga | **경량 오케스트레이션 Saga**(중앙 상태 + 실패 시 역순 보상). 단계 커맨드/리플라이는 **messaging Outbox 재사용**(상태변경과 한 트랜잭션=원자적), 상태 **JDBC 영속**(재기동 복구), **스턱 복구 폴러**(`SKIP LOCKED`, 옵트인). 전송·멱등 소비는 messaging, 본 모듈은 오케스트레이션만. 리플라이는 앱 `@KafkaListener`→`SagaReplyConsumer`. **참여자 멱등 키=`(saga-id, step)`** | `framework.saga.enabled`(+`recovery.enabled`) | [선택] | 금 ★ |

### 2.6 신규 — 규제 특화 (해당 사업만 켬)

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| framework-pki | GPKI/NPKI·전자서명·부인방지 | `framework.pki.enabled` | [선택] | 공/금 |
| ✅ framework-mfa | **2단계 인증(MFA)** — TOTP(RFC 6238)·OTP(SMS/메일/알림톡 `OtpSender` SPI)·**ISMS-P 일회용 복구코드**(SHA-256). security 로그인이 `MfaGate` SPI 로 2단계 분기(미사용 시 단일단계 그대로). 챌린지 저장소 memory\|redis(멀티 인스턴스는 redis 필수)·등록 저장소 memory\|jdbc. **외부 의존성 0개**(Base32/HOTP/TOTP/복구코드 전부 JDK) | `framework.mfa.enabled` + `policy=ENROLLED\|OFF`, `totp/otp.enabled`, `challenge/enrollment.store.type` | [선택] | 금/공 |
| framework-crypto-hsm | HSM 키관리(PKCS#11) | `framework.crypto.provider=hsm` | [선택] | 금 |
| framework-recon | 대사/정산 배치 | `framework.recon.enabled` | [선택] | 금 |
| framework-egov-compat | 전자정부 표준프레임워크 호환 어댑터 | `framework.egov.enabled` | [선택] | 공 |

### 2.7 신규 — 운영/관측

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| framework-observability ✅ | 구조화(JSON) 로그·Micrometer 메트릭(공통 태그)·OTel 트레이스/메트릭 OTLP 익스포터 | `framework.observability.enabled` | [선택] | 공통 |
| ✅ framework-lock | **분산 락 / 스케줄러 리더 선출** — `DistributedLock` SPI(소유자 토큰 리스 기반 `tryLock/unlock/keepUntil` + 편의 `runIfLocked`), 백엔드 memory(단일JVM)\|redis(SET NX + Lua CAS)\|jdbc(INSERT 충돌·만료 재획득). **`@SchedulerLock`**(애스펙트)로 k8s 다중 파드 `@Scheduled` 중복 실행 방지(ShedLock 동등 `atMostFor`/`atLeastFor`, 네이티브). **외부 의존성 0개**(redis/jdbc=compileOnly, H2=test-scope) | `framework.lock.enabled` + `type=memory\\|redis\\|jdbc` (+`scheduler.enabled`) | [선택] | 공통 |
| ✅ framework-cache-redis | **분산 캐시(Redis 백엔드)** — core 로컬 Caffeine `CacheManager` 를 파드 간 공유 Redis 로 **대체**(`@AutoConfiguration(before=CacheAutoConfiguration)` 로 core 백오프). 값=JDK 직렬화(Jackson2 회피)·키=String, TTL/keyPrefix/null+캐시별 `ttls`, 앱 `RedisCacheConfiguration` 우선. 끄면 core Caffeine. **외부 의존성 0개**(spring-data-redis=compileOnly+test) | `framework.cache.redis.enabled` (+`time-to-live`/`key-prefix`/`cache-null-values`/`ttls`) | [선택] | 공통 |

> ✅ 구현완료(2026-06-03). **batch 모듈과의 차이**: Spring Batch 잡의 클러스터 중복방지는 Quartz `job-store-type=jdbc`. 본 모듈은 <b>평범한 `@Scheduled`</b>(Quartz 아님)의 중복방지 갭을 메우고, 임의의 단발 작업 단일 실행(`runIfLocked`)도 제공. 소유자 토큰으로 "내 락 만료 후 타 인스턴스 재획득분을 잘못 해제"를 차단(Redis=Lua CAS, JDBC=`WHERE lock_owner=?`). 운영(다중 replica)은 `type=redis\|jdbc` 필수(memory 는 파드 간 미배타). JDBC DDL `db/lock-postgres.sql`. 상세는 `framework/framework-lock/README.md`.

> ✅ 구현완료(2026-06-02). 공통 태그=`MeterRegistryCustomizer`(service/env/version+extra). 구조화 로그=Boot4 네이티브 `logging.structured.format`(ecs/logstash/gelf, 인코더 불필요). 익스포터=메트릭/트레이스 OTLP(기본 off, 호스트 runtimeOnly opt-in). 프로퍼티성 표준값은 `EnvironmentPostProcessor`(로깅 초기화 전)로 주입. **새 외부 의존성 0**. 상세는 `framework/framework-observability/README.md`, k8s 는 `deploy/k8s/observability.yaml`.

### 2.8 신규 — 테스트/아키텍처 검증 (테스트 전용, 배포 산출물 아님)

| 모듈 | 책임 | 토글 | 분류 | 규제 |
|---|---|---|---|---|
| framework-archtest ✅ | **ArchUnit 아키텍처 규칙 강제** — 모듈(슬라이스) 순환금지 · Jackson3 규약(이동된 `com.fasterxml.jackson.*` 금지, `.annotation` 예외) · mapper/domain 레이어 격리 · `*AutoConfiguration`/`*Properties` 네이밍 · 필드주입 금지(생성자 주입). 전 모듈 main 을 `testImplementation project(...)` 로 임포트해 검사 | (토글 없음·`test` 자동) | [테스트] | 공통 |

> ✅ 구현완료·**BUILD 통과(2026-06-03)**. 7규칙, `@AnalyzeClasses(DoNotIncludeTests)`. **새 라이브러리 모듈 추가 시 `framework-archtest/build.gradle` 에 project 의존 한 줄을 추가**해야 검사 대상에 포함된다(누락 시 사각지대). 함께 도입: 핵심 알고리즘 단위테스트(JWT/TOTP/Base32/RBAC/마스킹) + 오토컨피그 로딩 테스트(`ApplicationContextRunner`) + **WireMock(standalone) 서비스간 연동 테스트**(`framework-client`: 재시도/서킷/POST 비재시도). archunit/wiremock 모두 **test 전용 → 런타임 무영향**.
>
> **오토컨피그 로딩 테스트 함정**: `ApplicationContextRunner` 가 설정 클래스를 리플렉션 introspect 하며 **모든 @Bean 파라미터/반환 타입을 로드**한다(@ConditionalOnClass 무관). 그 모듈의 `compileOnly` 의존을 **전부**(중첩 @Configuration·@Bean 반환 타입이 끌어오는 import 포함) `testImplementation` 으로 재선언해야 컨텍스트가 뜬다(예: mfa = web+jdbc+data-redis). 운영은 Boot 가 ASM 으로 읽어 무관.
>
> **전 라이브러리 모듈 스모크 완료(2026-06-03)**: 무테스트였던 14개(audit·batch·commoncode·excel·file·file-s3·i18n·idgen·messaging·mybatis·notification·openapi·redis·secure-web)에 로딩/토글 스모크 추가 → 무테스트 라이브러리 모듈 0. **서블릿 한정 오토컨피그**(`@ConditionalOnWebApplication(SERVLET)`: i18n 웹·secure-web)는 `WebApplicationContextRunner` 로. **`@MapperScan`+matchIfMissing(기본 ON)+MyBatis 결합**(commoncode·file)은 enabled 가 DataSource 를 요구 → 순수 스모크는 **disabled 백오프**까지(enabled 풀 와이어링은 DB 슬라이스). redis 는 오토컨피그 2개 중 `RedisTokenStoreAutoConfiguration` 만 `.imports` 등록됨(`RedisLoginAttemptAutoConfiguration` 미등록 = 다음 후보).
>
> **콘솔 한글 인코딩**: 테스트 워커/데몬은 `build.gradle`·`gradle.properties` 에서 UTF-8 고정. 콘솔 최종 렌더는 `gradlew` 클라이언트 JVM 이라 Windows 에서 깨지면 셸 `GRADLE_OPTS="...UTF-8"` 추가(데몬 변경 시 `--stop`).

---

## 3. 의존 관계 (요약)

```
core ──┬── mybatis ── (audit, datasource, commoncode, file)
       ├── security ──┬── redis(impl), audit(연동), mfa, pki
       │              └── idempotency(impl: redis/jdbc)
       ├── i18n            (모두가 메시지 사용)
       ├── idgen           (도메인 채번)
       ├── client ──── messaging/saga(연계)
       ├── observability   (전 모듈 횡단)
       ├── lock            (분산 락; impl: redis/jdbc, 횡단)
       ├── cache-redis     (분산 캐시; core Caffeine 대체, before=CacheAutoConfiguration)
       ├── log-masking     (로그 PII 마스킹; core MaskingUtils 위임, Logback %mmsg)
       ├── pdf             (PDF 산출물; OpenPDF impl 비노출, 한글 TTF 임베딩)
       ├── context         (요청 컨텍스트/멀티테넌시; ThreadLocal 비상속, @Async·아웃바운드 명시 전파)
       └── image           (이미지 처리; ImageIO+AWT, 웹 비의존, 외부 의존성 0)
```
원칙: 상위(토대)는 하위를 모른다. 순환 금지. impl 모듈(redis 등)이 추상(security/idempotency)을 의존. **saga 는 messaging(Outbox) 위에 오케스트레이션만 얹는다**(전송/멱등 소비 재사용, compileOnly 비전이라 의존 서비스가 messaging 도 명시).

---

## 4. 구축 순서 (금융 우선, 토대→심의→생산성→연계→규제→운영)

1. **토대** — framework-i18n, framework-idgen, framework-client
2. **보안 완성(심의)** — 비번 만료/이력, 동시로그인, framework-audit, framework-secure-web
3. **금융 핵심** — **framework-idempotency**, framework-messaging(+Outbox), framework-datasource
4. **업무 생산성** — framework-excel, framework-batch, framework-notification, framework-pdf, framework-image
5. **규제 특화** — framework-pki, framework-mfa, framework-crypto-hsm, framework-recon, (공공 시) framework-egov-compat
6. **운영/관측** — framework-observability ✅ · framework-lock ✅(분산 락·`@Scheduled` 중복방지) · framework-cache-redis ✅(분산 캐시) · framework-log-masking ✅(개인정보 로그 마스킹) · framework-context ✅(요청 컨텍스트/멀티테넌시·횡단)
7. **그릇 정비** — 게이트웨이(폴백·CORS·rate-limit)·k8s(redis/secret/멀티서비스)·CI/CD 멀티서비스화

> 1·2단계 산출물은 3단계 이후 모든 모듈이 재사용한다(메시지·채번·연계·감사). 토대를 건너뛰면 각 모듈이 재발명한다.

---

## 5. 프로파일 프리셋 (사업유형별 일괄 on/off)

같은 코어 + yml 프리셋으로 사업유형을 전환한다.

```yaml
# application-finance.yml  (금융 풀세트)
framework:
  idempotency: { enabled: true,  store: { type: jdbc }, replay: { enabled: true } }  # 중복 차단+응답 재생, 영속 공유
  messaging:   { enabled: true, outbox: { relay: { enabled: true } } }
  datasource:  { routing: { enabled: true } }   # primary/replica 읽기·쓰기 분리 (또는 multi.enabled=true 로 독립 다중 DB — 둘은 배타)
  audit:       { enabled: true,  store: { type: jdbc } }
  mfa:         { enabled: true }
  pki:         { enabled: true }
  security:    { password: { expiry: { enabled: true }, history: { enabled: true } },
                 concurrent-session: { enabled: true } }
```
```yaml
# application-public.yml  (공공)
framework:
  egov:  { enabled: true }
  pki:   { enabled: true }
  audit: { enabled: true, store: { type: jdbc } }
  secure-web: { enabled: true }
```
```yaml
# application-enterprise.yml  (일반 기업 라이트 — 규제 특화 off)
framework:
  idempotency: { enabled: true, store: { type: redis } }   # 중복요청 방지는 보편적
  audit:       { enabled: true, store: { type: jdbc } }
  # egov/pki/mfa/hsm/recon/saga: 미설정 → 기본 false → 꺼짐
```

레퍼런스 구현은 `framework/framework-idempotency/` (전 3단 토글 적용 예) 참고.
