# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**파일 보안 2종(콘텐츠 타입 검증 + 저장소 at-rest 암호화) 신설 + 직전 Next #1·#2 완결 + Boot4 deprecation 정리.** redis 레지스트레이션 갭 해소(+회귀 가드), commoncode/file enabled 풀 와이어링을 임베디드 H2 슬라이스로 검증(MapperScan 결합 모듈 경계 돌파), 파일 모듈에 Tika 콘텐츠 검증·AES-CBC 스트리밍 저장 암호화 추가, `@PreAuthorize` 테스트 경고 제거, observability `EnvironmentPostProcessor` 패키지 이동(Boot4). 전부 옵트인·하위호환, 런타임/배포 영향 0(신규 의존성은 tika 1종, compileOnly/옵트인).

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
1. **redis 레지스트레이션 갭 해소**: `RedisLoginAttemptAutoConfiguration` 을 `AutoConfiguration.imports` 에 등록(이제 `framework.security.login-attempt.type=redis` 로 자동활성). 같은 함정 재발 차단용 **회귀 가드 테스트**(`RedisAutoConfigurationTest`): 클래스패스의 모든 `*.imports` 를 직접 읽어 두 redis 오토컨피그 등록을 단언(기존 토글 스모크는 `AutoConfigurations.of(클래스)` 직접 로드라 미등록을 못 잡았음).
2. **commoncode/file enabled 경로 테스트**(직전 Next #2): `ApplicationContextRunner` 에 실제 오토컨피그 체인(`DataSourceAutoConfiguration`→`MybatisAutoConfiguration`→`MyBatisConfig`→모듈) + 임베디드 H2 를 물려 프로덕션 와이어링 검증 + 매퍼 XML CRUD 라운드트립(snake→camel·`useGeneratedKeys`·감사필드 자동주입까지 교차검증). 스키마는 `.run()` 안에서 `DataSource` 로 프로그램matically 생성. build.gradle 에 `testRuntimeOnly h2`. **MapperScan 결합 모듈 "백오프까지"의 경계를 enabled 슬라이스로 돌파.**
3. **파일 콘텐츠 타입 검증(옵트인)**: `FileContentTypeValidator`(NoOp 기본 / Tika) — Tika 매직넘버로 실제 MIME 검출 → 위험 MIME(실행파일/스크립트/HTML 등) 차단, 검출 MIME 을 메타에 기록(헤더 위조 무시). 토글 `framework.file.validation.content-type-detection=true` + tika-core(`compileOnly`, **가드된 인스턴스화**로 선택 의존). `FileService` 가 `file.getContentType()` 대신 검증값 사용.
4. **파일 저장소 at-rest 암호화(옵트인)**: `EncryptingFileStorage` 데코레이터(`BeanPostProcessor` 로 local/nas/s3 어느 백엔드에도 래핑). 코어 `AesCryptoService` 에 **CBC 스트리밍 메서드**(`encryptingInputStream`/`decryptingInputStream`) + `cbcEncryptedLength`(IV+패딩 정확 길이, S3 content-length 용) 추가 — 기존 GCM 문자열 암호화와 같은 키 재사용. 토글 `framework.file.storage.encrypt=true`(`framework.crypto.enabled` 필요).
5. **`@PreAuthorize` 테스트 경고 제거**: commoncode/file 의 enabled 테스트가 컨트롤러(`@PreAuthorize`) 빈을 참조 → `testImplementation spring-security-core` 추가(compileOnly 는 test 비전이; introspection 함정과 동일 결).
6. **observability EPP Boot4 패키지 이동**: `org.springframework.boot.env.EnvironmentPostProcessor` → `org.springframework.boot.EnvironmentPostProcessor`(import + `spring.factories` 키). Boot 4.0 에서 인터페이스 패키지 이동(구버전 deprecated 브리지, 4.2.0 제거 예정). 메서드 시그니처 불변. compileJava 경고 3건 제거.

## 현재 상태 (적용/검증)
- ✅ **사용자 환경 BUILD SUCCESSFUL 확인**: redis·commoncode/file 테스트·파일 보안 2종 컴파일 OK(사용자 보고). observability EPP 수정 후 deprecation 경고 제거 예상(키 변경 포함 → 부팅 시 EPP 등록 1회 확인 권장).
- 신규 테스트: `RedisAutoConfigurationTest`(가드), `CommonCodeAutoConfigurationTest`/`FileStorageAutoConfigurationTest`(enabled CRUD), `AesCryptoServiceStreamTest`, `EncryptingFileStorageTest`, `TikaFileContentTypeValidatorTest`, `FileStorageFeaturesTest`.
- 신규 의존성: **tika 3.1.0**(카탈로그, Boot BOM 밖 — POI 와 동일 방식) — file 에 `compileOnly`+`testImplementation`. 그 외 commoncode/file 에 `testRuntimeOnly h2`·`testImplementation spring-security-core`(전부 테스트/옵트인, 런타임·배포 영향 0).

## 켜는 법
- 콘텐츠 검증: `framework.file.validation.content-type-detection=true` + 서비스에 `runtimeOnly|implementation 'org.apache.tika:tika-core:3.1.0'`(없으면 경고 후 NoOp 폴백). 차단 MIME 은 `framework.file.validation.blocked-content-types` 로 커스터마이즈.
- 저장 암호화: `framework.file.storage.encrypt=true`(+ `framework.crypto.aes-secret` 설정). 기존 평문 파일은 복호화 실패하니 **신규 적용은 빈 스토리지부터**(키/모드 전환 마이그레이션은 별도).
- 부분 빌드: `./gradlew :framework:framework-{core,redis,commoncode,file,observability}:test :framework:framework-archtest:test spotlessApply`(셸 중괄호 미동작 시 모듈 나열).

## 바로 다음 할 일 (Next)
1. (devops) **CI 게이트**: `:framework-archtest:test` + 전 모듈 `:test` 를 PR 차단 게이트 + **멀티모듈 jacoco 집계 리포트**(루트 aggregate).
2. **그릇 정비**: 게이트웨이 런타임 점검(CORS preflight·rate-limit 429) · k8s 멀티서비스/CI-CD(redis/secret/observability ServiceMonitor 실배포).
3. **분산 락 / 스케줄러 리더 선출**(k8s 다중 파드 `@Scheduled` 중복 방지 — 직전 논의의 "기본기능 갭" 최우선). 개인정보 로그 마스킹·PDF 생성·분산 캐시도 후보.
4. 파일 후속(선택): 이미지 처리 모듈(썸네일/EXIF 스트립) · 대용량 스트리밍(HTTP Range/S3 presigned·멀티파트) · 안티바이러스 훅 · 다운로드 `X-Content-Type-Options:nosniff`(secure-web 책임).
5. (선택) 규제특화 잔여(pki/hsm/recon/egov) · saga 단계별 타임아웃/보상 재시도 · 멱등 재생 페이로드 지문 · 암호화 파일 키 회전/마이그레이션.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **Boot 4: `EnvironmentPostProcessor` 패키지 이동** — `org.springframework.boot.env` → `org.springframework.boot`(구버전 deprecated, 4.2.0 제거). `BootstrapRegistry` 도 `org.springframework.boot.bootstrap` 로 이동. EPP 는 코드 import + **`spring.factories` 키** 둘 다 바꿔야 함(키만 틀려도 조용히 미등록). 메서드 시그니처 `postProcessEnvironment(ConfigurableEnvironment, SpringApplication)` 는 불변.
- **컨트롤러(`@PreAuthorize`)를 참조하는 테스트는 `testImplementation spring-security-core` 필요**: security-core 가 main `compileOnly` 면 test 컴파일러가 `@PreAuthorize.value()` 를 못 찾아 경고(`Cannot find annotation method 'value()'`). 빌드는 통과하나 깔끔히 없애려면 test 재선언. (런타임은 애노테이션 부재 무해 → compileOnly 유지.)
- **MapperScan 결합 모듈 enabled 슬라이스 패턴**: 순수 로딩 스모크의 "백오프까지" 한계는, `ApplicationContextRunner` 에 `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`(Boot4 이동 패키지) + `org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration` + `MyBatisConfig` + 모듈 오토컨피그 + 임베디드 H2(`spring.datasource.url=jdbc:h2:mem:..;DB_CLOSE_DELAY=-1`) + `mybatis.mapper-locations=classpath*:mapper/**/*.xml` 를 물리면 돌파된다(스키마는 `.run()` 안에서 DataSource 로 직접 DDL). Testcontainers 불필요.
- **레지스트레이션 가드 테스트**: `AutoConfigurations.of(클래스)` 직접 로드 스모크는 `.imports` 미등록을 못 잡는다 → 클래스패스의 `META-INF/spring/...AutoConfiguration.imports` 를 `getResources` 로 union 해 FQCN 등록을 직접 단언하는 가드를 함께 둔다(redis 갭이 4개 테스트를 빠져나간 이유).
- **선택 의존(Tika)은 가드된 인스턴스화로**: 오토컨피그가 `TikaFileContentTypeValidator` 를 import 하되 `new` 는 `ClassUtils.isPresent("org.apache.tika.Tika", ...)` 가드 안에서만 → Tika 부재 런타임에도 그 클래스가 로드되지 않아 안전(import 만으론 로드 안 됨). 토글 ON+Tika 부재면 경고 후 NoOp.
- **암호화 후 길이는 결정적이어야 S3 가 산다**: S3 `putObject` 가 `contentLength(size)` 를 요구 → `EncryptingFileStorage` 는 평문 size 가 아니라 `AesCryptoService.cbcEncryptedLength(size)`(IV 16B + PKCS5 패딩)를 위임에 넘긴다. 로컬/NAS 는 size 무시라 무해. AES-CBC 스트리밍은 기밀성만(무결성 태그 없음 — 파일 메타는 DB 로 관리). `CryptoHolder.aes()` 정적 접근(타입핸들러와 동일 패턴).
- (지난·유효) introspection=compileOnly 타입 전부 test 재선언(중첩/반환 포함) / `@ConditionalOnWebApplication(SERVLET)`→`WebApplicationContextRunner` / 모듈 toggle 3단 기본 off(단 commoncode·file·openapi 는 matchIfMissing=true) / Jackson3(`tools.jackson.*`, `.annotation` 만 예외) / 콘솔 UTF-8 3계층 / JUnit launcher·starter-test 모듈마다 / ArchUnit Jackson 은 이동 패키지만 금지 / 새 오토컨피그는 `.imports` 등록 항상 확인.

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. 순수 로직은 Spring 무의존 코어로 분리해 JDK 단독 검증.
2. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`. **테스트가 그 compileOnly 클래스(또는 그게 붙은 컨트롤러/빈)를 참조하면 재선언.** 선택 의존(Tika 류)은 `compileOnly`+가드된 인스턴스화.
3. `settings.gradle`/`imports` 등록. 신규 모듈이면 `framework-archtest/build.gradle` 에 project 의존 추가. **새 오토컨피그는 `.imports` 등록 + (있으면) 등록 가드 테스트 확인**(미등록=죽은 코드). BOM 밖 의존(Tika 등)은 `libs.versions.toml`+root ext+`STACK.md`.
4. Boot4/Spring7/Jackson3 + 통합 대상 실제 시그니처를 레포 내 동일 사용처/공식 소스로 교차확인(Boot4 패키지 이동 추측 금지).
5. 오토컨피그 3단 토글 + 빈 `@ConditionalOnMissingBean`. 런타임 개수 가변 빈은 `ImportBeanDefinitionRegistrar`, 기존 빈 래핑은 `BeanPostProcessor`.
6. **테스트**: 핵심 알고리즘 단위 + 오토컨피그 로딩(enabled/disabled). MapperScan+MyBatis 결합은 임베디드 H2 슬라이스로 enabled 까지. 외부연동 WireMock(standalone). 검증 `./gradlew :…:test (+:framework-archtest:test) (+spotlessApply)`.
7. 드롭인: 변경 파일 전부 → 한 zip, 루트에서 `unzip -o`. 문서 5종 동기화.

<!-- 갱신 끝 -->
