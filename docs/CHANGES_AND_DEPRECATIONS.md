# CHANGES_AND_DEPRECATIONS.md — Deprecated / 변경 사항 정리

> 목적: Spring Boot 4 / Spring Framework 7 / Spring Cloud 2025.1.x / Jackson 3 로 올라오며 바뀐
> API·패키지·관례와, 이 프레임워크의 자체 규약 변경을 **한 곳에** 모은 참조 문서.
> 각 항목은 *무엇이 바뀌었나 / 예전 / 지금 / 어떻게 써야 하나* 형식.
> (원 출처는 `HANDOFF.md`·`STACK.md` 곳곳 — 여기로 통합 정리.)

---

## A. 이번 세션에서 바뀐 것 (프로파일 체계)

### A-1. 환경 프로파일을 local / dev / prod 로 통일
- **무엇**: 환경 구분을 3개로 통일하고, 특수 환경은 `local-xx` 오버레이로 분리.
- **예전**:
  - `application.yml` 이 PostgreSQL 을 하드코딩 → 프로파일을 안 주면 PG 로 떴다.
  - `local` = H2 메모리, `dev` = **로그인 우회(dev-auth) 오버레이** (`local,dev` 로 함께 사용).
  - `prod` 없음.
- **지금**:
  - `application.yml` = 인프라 비종속 공통만. 기본 활성 프로파일 = `local`.
  - `local`(H2 메모리) / `dev`(개발 서버) / `prod`(운영) 3종.
  - 오버레이: `local-postgres`(DB→로컬 PG), `local-redis`(Redis on), `local-noauth`(로그인 우회).
- **어떻게**:
  ```bash
  # 메모리 → 로컬 PG → +Redis
  ./gradlew :services:user-service:bootRun
  ./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local,local-postgres'
  ./gradlew :services:user-service:bootRun --args='--spring.profiles.active=local,local-postgres,local-redis'
  ```
  > ⚠️ **로그인 우회**는 `local,dev` → **`local,local-noauth`** 로 변경. `dev` 는 이제 "개발 서버 환경" 의미.

### A-2. 감사 로그 DB 적재에 마이그레이션이 필요해짐
- **무엇**: `framework.audit.store.type=jdbc` 로 DB 영속하려면 `audit_log` 테이블이 있어야 한다.
- **예전**: 테이블 마이그레이션이 서비스에 없어, jdbc 로 켜도 INSERT 가 조용히 실패(WARN 만)했다.
- **지금**: `db/migration/V4__audit_log.sql`(user) / `V2__audit_log.sql`(admin) 추가 → Flyway 자동 생성.
- **어떻게**: ① 서비스 `build.gradle` 에 `implementation project(':framework:framework-audit')`
  ② 마이그레이션 포함 ③ `store.type=jdbc`. 검증 절차는 `docs/LOCAL_SETUP.md` §5.

---

## B. Jackson (가장 자주 발 걸리는 항목)

### B-1. Jackson 2 → Jackson 3 (`com.fasterxml.jackson.*` → `tools.jackson.*`)
- **무엇**: 코어/데이터바인딩 패키지가 통째로 이동.
- **예전**: `com.fasterxml.jackson.databind.ObjectMapper`, `...core`, `...dataformat`, `...datatype`, `...module`.
- **지금**: `tools.jackson.*` (예: `tools.jackson.databind.json.JsonMapper`). 매퍼는 `JsonMapper`,
  커스터마이저는 `JsonMapperBuilderCustomizer`.
- **예외**: **애너테이션은 그대로** `com.fasterxml.jackson.annotation.*` (`@JsonInclude`, `@JsonProperty` 등) — 이동 안 됨.
- **어떻게**:
  - `com.fasterxml.jackson.core/databind/...` import 금지(클래스패스에 없음 → 컴파일 에러).
  - 필터/인프라 레벨의 단순 JSON 응답은 Jackson 빈 주입보다 **수기 직렬화**가 견고 (`SecureWebResponder` 사례).
  - ArchUnit 규칙이 이동된 5개 패키지만 금지하고 `...annotation` 은 허용하므로, 새 모듈 추가 시
    `framework-archtest/build.gradle` 에 `testImplementation project(...)` 한 줄 추가.

### B-2. Spring Data Redis 의 Jackson2 직렬화기 사용 금지
- **무엇**: `GenericJackson2JsonRedisSerializer`/`Jackson2JsonRedisSerializer` 는 Jackson 2 기반.
- **지금**: 본 스택(Jackson 3)에선 클래스패스 부재 → 쓰지 않는다. 캐시 값은 JDK 직렬화(`RedisSerializer.java()`)
  → 캐시 대상은 `Serializable` 이어야 함. `RedisTokenStore`/MFA 도 `StringRedisTemplate`+수기.
- **어떻게**: JSON 직렬화가 꼭 필요하면 앱이 `RedisCacheConfiguration` 빈을 직접 등록(모듈이
  `@ConditionalOnMissingBean` 으로 양보) → 그 안에서 Jackson 3 `JsonMapper` 기반 직렬화기를 앱 책임으로 구성.

---

## C. Spring Framework 7 / Boot 4 API 변경

### C-1. `HttpStatus.PAYLOAD_TOO_LARGE` → `CONTENT_TOO_LARGE`
- **무엇**: RFC 9110 명칭 반영 (상태코드 413 동일).
- **예전**: `HttpStatus.PAYLOAD_TOO_LARGE`
- **지금**: `HttpStatus.CONTENT_TOO_LARGE` (Spring 6.1+). 예: `framework-image` 의 `ImageErrorCode` 수정 완료.
- **어떻게**: 413 을 쓰는 에러코드/핸들러는 새 enum 으로 교체. 값은 동일하므로 동작 영향 없음.

### C-2. `EnvironmentPostProcessor` / `BootstrapRegistry` 패키지 이동 (Boot 4)
- **무엇**: 부트스트랩 인터페이스 패키지 이동. 구버전은 deprecated 브리지(4.2.0 제거 예정, `[removal]` 경고).
- **예전**: `org.springframework.boot.env.EnvironmentPostProcessor`,
  `org.springframework.boot.BootstrapRegistry` 계열.
- **지금**: `org.springframework.boot.EnvironmentPostProcessor`,
  `org.springframework.boot.bootstrap.*`.
- **어떻게**: **코드 import 와 `META-INF/spring.factories` 키 둘 다** 변경.
  키만 틀려도 조용히 미등록(기본 프로퍼티 미주입)되니 주의. 메서드 시그니처
  `postProcessEnvironment(ConfigurableEnvironment, SpringApplication)` 은 불변. (`framework-observability` 수정 완료.)

---

## D. Spring Cloud 2025.1.x (Oakwood) — 게이트웨이

### D-1. Spring Cloud 버전 호환
- **무엇**: Boot 4 와는 **2025.1.x(Oakwood)** 만 호환.
- **예전/지금**: 2025.0.x 는 Boot 4 비호환 → 반드시 2025.1.x.

### D-2. 게이트웨이 아티팩트/설정 경로
- **무엇**: WebFlux 게이트웨이 아티팩트·설정 키 변경.
- **지금**:
  - 아티팩트: `spring-cloud-starter-gateway-server-webflux`
  - 설정 경로: `spring.cloud.gateway.server.webflux.*` (routes/globalcors/default-filters 가 이 아래).
- **어떻게**: 기존 `spring.cloud.gateway.*` 평면 구조에서 `server.webflux` 하위로 이동했는지 확인.

### D-3. `trusted-proxies` 기본 비활성
- **무엇**: 2025.x 부터 신뢰 프록시 기본 off → `X-Forwarded-*` 가 기본 무시.
- **어떻게**: 프록시/게이트웨이 뒤에서 포워딩 헤더를 쓰면 `trusted-proxies` 패턴을 명시 설정
  (현 gateway 설정에 사설 IP 대역 정규식 지정돼 있음).

---

## E. 라이브러리 (BOM 밖 — 버전 고정 주의)

### E-1. datasource-proxy 2.0.0 (Boot 4 전용)
- **무엇**: SQL 바인딩/슬로우쿼리 로깅 스타터.
- **예전/지금**: 1.x 는 Boot 4 비호환 → **2.0.0+** 필요(카탈로그 `datasourceProxy=2.0.0`).

### E-2. Apache POI 5.5+ — `dispose()` deprecated → `close()`
- **무엇**: SXSSF 스트리밍 워크북 종료 방식.
- **예전**: `workbook.dispose()`
- **지금**: `close()` (try-with-resources). close 가 flush+임시파일 삭제까지 수행.
- **어떻게**: `framework-excel` 은 close 사용. `autoSizeColumn` 은 비용 커서 금지. POI 는 BOM 밖 → 카탈로그 고정(`poi`).

### E-3. OpenPDF 패키지 리네임 — 의도적으로 2.x 고정
- **무엇**: PDF 엔진 패키지명이 메이저에서 바뀜.
- **예전/지금**: 2.x = `com.lowagie.text`, **3.0+ = `org.openpdf`** 로 리네임.
- **어떻게**: `framework-pdf` 는 안정 API 인 **2.0.2 로 고정**(import 는 `com.lowagie.text*`).
  3.x 로 올리면 전 import 경로를 바꿔야 함. 라이선스 LGPL-2.1/MPL-2.0(iText 5+/7 AGPL 회피 목적).
  한글은 `BaseFont.createFont(name, IDENTITY_H, EMBEDDED, true, ttfBytes, null)` 로 TTF 임베딩.

---

## F. Spring Batch 6 (Boot 4)

### F-1. `JobLauncher` deprecated → `JobOperator`
- **무엇**: 잡 실행 진입점 변경 + 패키지 대이동.
- **예전**: `JobLauncher.run(...)`
- **지금**: `JobOperator`(extends JobLauncher), 실행 = `start(Job, JobParameters)`. Boot 가 JobOperator 자동구성.
- **패키지 이동**: `core.job.{Job,JobExecution,JobExecutionException}`,
  `core.job.parameters.{JobParameters,JobParametersBuilder,RunIdIncrementer}`,
  `core.listener.JobExecutionListener`,
  `core.launch.{JobOperator, JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException}`.
  (`core.{Entity,ExitStatus}` 는 그대로.)
- **어떻게**: 배치 메타테이블 필요(`spring.batch.jdbc.initialize-schema` 또는 Flyway 로 생성).

---

## G. 도메인 규칙 변경 (라이브러리 아님)

### G-1. 외국인등록번호 체크섬 폐지 (2020-10 이후)
- **무엇**: 외국인등록번호 검증 로직.
- **예전**: 체크섬(검증식) 사용.
- **지금**: 2020-10 이후 발급분은 체크섬 폐지 → `isValidForeignerNo` 는 **형식만 검증**(체크섬 검증 금지).
- **참고**: 음력/대체공휴일은 자동계산 안 함 → `HolidayUtils` 에 `Set<LocalDate> extraHolidays` 주입식.
  범용 문자열/컬렉션 유틸은 Spring 표준 사용(재발명 금지).

---

## 빠른 체크리스트 (새 코드/리뷰 시)
- [ ] `com.fasterxml.jackson.core/databind/...` import 없음 (annotation 만 예외)
- [ ] 413 은 `CONTENT_TOO_LARGE`
- [ ] Redis 직렬화에 Jackson2 직렬화기 미사용
- [ ] 게이트웨이 설정은 `spring.cloud.gateway.server.webflux.*`
- [ ] 배치는 `JobOperator`, 이동된 패키지 import
- [ ] BOM 밖 라이브러리(POI/OpenPDF/Tika/datasource-proxy)는 카탈로그 버전 고정
- [ ] 감사 DB 적재 = 모듈 의존 + `audit_log` 마이그레이션 + `store.type=jdbc`
- [ ] 로그인 우회는 `local,local-noauth` (옛 `local,dev` 아님)
