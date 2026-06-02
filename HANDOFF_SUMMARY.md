# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**`framework-idempotency` 에 JDBC 스토어 추가** — 기존 `IdempotencyStore` SPI(memory·redis)에 **세 번째 구현 `JdbcIdempotencyStore`(store.type=jdbc)** 를 얹음. Redis 없이 기존 DataSource 만으로 다중 인스턴스/재기동 간 멱등키 공유. 원자적 선점은 **PK 유니크 + INSERT 충돌(`DataIntegrityViolationException`) 캐치**(idgen 채번과 동일 관용), **만료행은 선점 직전 동일 키만 정리 후 INSERT** → 동시 정리에도 소유자 1개 보장. `saveResult` 는 벤더 UPSERT 회피("UPDATE 먼저, 0행이면 INSERT", MFA 스토어와 동일). 새 외부 의존성 0(jdbc 는 `compileOnly`, 호스트 제공). DDL(H2/PostgreSQL 공통) 동봉, H2 JUnit 6케이스 + 순수 JDK 시뮬 6/6 검증.

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
- **신규 `store/JdbcIdempotencyStore`**(`com.company.framework.idempotency.store`):
  - `putIfAbsent`: `DELETE … WHERE idem_key=? AND expires_at<=now`(만료 동일 키만) → `INSERT(result=NULL)`; PK 충돌이면 `DataIntegrityViolationException` 캐치 → false.
  - `saveResult`: `UPDATE`; 0행이면 `INSERT`(레이스로 행 생기면 재 `UPDATE`). 벤더 UPSERT 미사용.
  - `findResult`: `SELECT result WHERE idem_key=? AND expires_at>now`; 행 없음/만료/`result NULL` → `Optional.empty`(InMemory 와 동일 의미).
  - `remove`: `DELETE`. 테이블명 상수 `TABLE="framework_idempotency"`(DDL 과 일치).
- **배선** `config/IdempotencyAutoConfiguration`: jdbc 빈 추가 — `@ConditionalOnClass(JdbcTemplate)` + `@ConditionalOnMissingBean(IdempotencyStore)` + `@ConditionalOnProperty(store.type=jdbc)`(MFA 의 enrollment.store=jdbc 빈과 동형). 클래스 javadoc 3단 설명 `memory|redis|jdbc` 로 갱신.
- **DDL** `src/main/resources/db/idempotency-postgres.sql`(`CREATE TABLE IF NOT EXISTS framework_idempotency` + expires 인덱스, H2/PostgreSQL/Oracle 공통).
- **build.gradle**: `compileOnly spring-boot-starter-jdbc` + `testImplementation spring-boot-starter-test` + `testRuntimeOnly com.h2database:h2`(BOM 관리).
- **테스트** `JdbcIdempotencyStoreTest`(JUnit5+AssertJ, 인메모리 H2 MODE=PostgreSQL, 6케이스): 선점1회/save·find/선점만→empty/만료 재선점/만료결과 미반환/remove 후 재선점.
- **문서**: 모듈 `README.md`(JDBC 스토어 절 신규).

## 현재 상태 (적용/검증)
- 정적 점검 통과: 패키지=디렉터리, 괄호 균형, **`com.fasterxml` 0건**, FQCN(`org.springframework.jdbc.core.JdbcTemplate`·`org.springframework.dao.DataIntegrityViolationException`), H2 는 테스트에만.
- **선점/만료-재선점/저장/제거 의미 순수 JDK(JDK21 단일파일 런처) 실행검증 6/6**(SQL 분기 충실 모델링).
- ⚠️ **gradle 컴파일 미검증**(작성 환경 Maven Central 차단). 받는 쪽: `./gradlew :framework:framework-idempotency:compileJava :framework:framework-idempotency:test` + `./gradlew spotlessApply`. (H2 testRuntimeOnly 가 BOM 으로 해석되는지, jdbc compileOnly 로 `JdbcTemplate` 컴파일 통과하는지 확인.)

## 켜는 법
- `implementation project(':framework:framework-idempotency')` + `framework.idempotency.enabled=true` + `framework.idempotency.store.type=jdbc`.
- 호스트 앱에 `spring-boot-starter-jdbc`(또는 data-jpa 등 DataSource) 필요. 테이블은 `db/idempotency-postgres.sql` 실행(운영은 Flyway `db/migration` 배치 권장).
- 컨트롤러 메서드에 `@Idempotent` + 클라이언트가 `Idempotency-Key` 헤더 전송. 현재 정책: 헤더 없음 400 / 중복·처리중 409.

## 바로 다음 할 일 (Next)
1. 받는 쪽 `:framework:framework-idempotency:compileJava :test (+spotlessApply)` 확인. 특히 jdbc 빈 `@ConditionalOnProperty(store.type=jdbc)` 활성·H2 테스트 6/6 그린.
2. (선택) `result` 컬럼 활용 = **결과 재생(replay)** 로 인터셉터 확장: 중복 시 409 대신 저장된 응답 반환(현 인터셉터는 `saveResult` 미호출, `findResult` 만 중복판정에 사용). 응답 캡처/재생은 정책 결정 필요(상태코드/바디/헤더 범위).
3. (선택) user-service 에 jdbc 멱등 실적용 + 전체 만료 청소 잡(스케줄러 `DELETE WHERE expires_at<=now`) 추가.
4. **누적 문서 동기화 미완**: 이번 세션은 모듈 README 만 갱신. 구조/원칙 변화는 아니지만 `HANDOFF.md`·`docs/FRAMEWORK_MODULES.md`(idempotency 3단 토글 표에 jdbc 행)·`STACK.md`(새 라이브러리 0/H2 test-scope) 반영 권장.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **JDBC 멱등 선점 = PK 충돌로 상호배제**: 별도 트랜잭션/락 없이 `INSERT` + `DataIntegrityViolationException` 캐치로 충분(idgen 채번과 동일). 만료행은 **선점 직전 동일 키만** 정리(전체 정리 아님) → 살아있는 선점 보존, 동시 정리에도 INSERT 는 하나만 성공.
- **벤더 UPSERT 금지(이식성)**: MERGE/ON CONFLICT/ON DUPLICATE 미사용. `saveResult` 는 "UPDATE 먼저, 0행이면 INSERT". `VARCHAR/TIMESTAMP` 만 사용 → H2/PostgreSQL/Oracle 공통(BIGSERIAL 등 벤더 타입 회피, idgen 원칙과 동일).
- **`result NULL` = 선점됨·미완료 → findResult empty**: 선점만 하고 결과 미저장 행은 `Optional.empty` 로 InMemory 와 의미 일치. `findResult` 의 `expires_at>now` 로 만료 결과도 자동 배제.
- **JDBC 스토어 테스트는 H2 실DB 로**: 선점/만료는 PK·TIMESTAMP 비교 의존이라 모킹 말고 인메모리 H2(`MODE=PostgreSQL`)로 검증. H2 는 모듈 `testRuntimeOnly`(BOM 관리, 카탈로그 핀 불요).
- **`compileOnly` 는 test 클래스패스로 전이되지 않는다**: main 을 `compileOnly`(jdbc/redis/web)로 받는 모듈에서 **테스트가 그 클래스를 직접 import 하면** `testCompileJava` 가 `package … does not exist` 로 실패한다(main 은 통과). 해결: 해당 의존을 test 소스셋에 **별도 선언**(`testImplementation`, 실행도 필요하면 그대로/아니면 `testCompileOnly`). idempotency: `testImplementation spring-boot-starter-jdbc` 추가로 해소. (관측의 "테스트 API 모듈마다 선언"과 같은 결: test 소스셋은 main 의 compileOnly 를 못 본다.)
- (기존) Boot4 패키지 이동(MeterRegistryCustomizer 등) · 구조화로그=EPP · OTLP 이중키 · 관측 새 의존성 0 · 테스트 API 모듈마다 선언 · util vs support · JsonUtils=Jackson3 · JUnit launcher 루트 적용 · 범용 유틸 재발명 금지 · 신규 모듈 settings/imports 등록 · BOM 밖만 카탈로그 핀.

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규: `framework/framework-<X>/`(config Properties+AutoConfiguration · 도메인 패키지 · imports FQCN). **기존 SPI 에 구현만 추가**할 땐(이번 jdbc 사례): 구현 클래스 + AutoConfig 에 `@ConditionalOnClass/@ConditionalOnMissingBean/@ConditionalOnProperty(type=…)` 빈 1개 + (영속이면) `db/*.sql` DDL. 컨텍스트 이전 동작이 필요하면 EPP + `spring.factories`.
2. `build.gradle`: 능력전이=`api`, 내부구현=`implementation`, 호스트/선택=`compileOnly`(jdbc/redis/web 등). **테스트 넣으면 `testImplementation spring-boot-starter-test`**, 실DB 검증 필요하면 `testRuntimeOnly 'com.h2database:h2'`(둘 다 BOM). **테스트가 main 의 `compileOnly` 클래스를 import 하면 그 의존도 test 소스셋에 재선언**(`testImplementation …`) — compileOnly 는 test 로 전이 안 됨(idempotency: `testImplementation spring-boot-starter-jdbc`). 레지스트리/익스포터처럼 런타임 classpath 로만 동작하면 호스트가 `runtimeOnly` opt-in.
3. `settings.gradle`(신규 모듈) / `imports`(새 autoconfig) 등록. **기존 모듈 확장이면 변경 없음**(이번 jdbc: settings/imports 무변경).
4. 코드 전 Boot4/Spring7/Jackson3 + 외부 API 공식 소스 확정. 틀리면 조용히 잘못되는 알고리즘(멱등 선점·만료 등)은 순수 JDK 또는 H2 로 실제 실행 검증.
5. 오토컨피그: `@AutoConfiguration` + `@ConditionalOnClass/Property` + 빈 `@ConditionalOnMissingBean`.
6. 검증: `./gradlew :...:compileJava (+:test) (+spotlessApply)`.
7. 드롭인: 변경 파일 전부 → 한 zip, 루트에서 `unzip -o`.


<!-- 갱신 끝 -->
