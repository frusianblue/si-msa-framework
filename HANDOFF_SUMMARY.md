# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**분산 락 / 스케줄러 리더 선출 모듈 `framework-lock` 신설** — 직전 논의의 "기본기능 갭" 최우선 후보 처리. `DistributedLock` SPI(소유자 토큰 리스 기반) + 백엔드 3종(memory/redis/jdbc) + `@SchedulerLock` AOP 애스펙트로 k8s 다중 파드 `@Scheduled` 중복 실행 방지. 3단 토글·기본 off, **새 외부 의존성 0**(redis/jdbc=compileOnly, H2=test). 옵트인·하위호환, 런타임/배포 영향 0.

## 최종 갱신
- 일자: 2026-06-03 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
1. **`DistributedLock` SPI**(`com.company.framework.lock`): 리스(TTL)+소유자 토큰 기반 `tryLock(key,token,ttl)`/`unlock(key,token)`/`keepUntil(key,token,ttl)` + 디폴트 편의 `runIfLocked(key,ttl,Runnable)`(UUID 토큰 자동). 보유 인스턴스 사망 시 TTL 자동 해제(교착 방지), unlock/keepUntil 은 소유자 일치 시만(만료 후 타 인스턴스 재획득분 오삭제 차단).
2. **백엔드 3종**(`support/`): **InMemory**(`ConcurrentHashMap`, `merge` 로 원자 선점, 단일 JVM·기본). **Redis**(`StringRedisTemplate.setIfAbsent`=SET NX PX, unlock/keepUntil 은 **Lua CAS**=`get==token then del/pexpire`, 키 prefix `lock:`). **Jdbc**(`framework_lock` 테이블, 만료 동일키 DELETE 후 INSERT, PK 충돌(`DataIntegrityViolationException`)=보유중→false, unlock/keepUntil 은 `WHERE lock_key=? AND lock_owner=?` — framework-idempotency JDBC 패턴 복제). DDL `db/lock-postgres.sql`.
3. **`@SchedulerLock` + 애스펙트**: `@Around("@annotation(schedulerLock)")` 가 트리거마다 락 시도 → 잡으면 실행·못 잡으면 스킵(대상 void). `atMostFor`=리스 상한, `atLeastFor`=최소 보유(조기 종료 시 `keepUntil(atLeastFor-경과)` 로 파드 간 클럭스큐 직후 재실행 차단). 기간은 `DurationStyle.detectAndParse`(`"10m"`·`PT10M` 양형). name 비우면 `선언타입.메서드명`.
4. **오토컨피그/프로퍼티**: `LockAutoConfiguration`(`@ConditionalOnClass(DistributedLock)`+`@ConditionalOnProperty(framework.lock.enabled=true)`) 백엔드 `@Bean` 3종(memory `matchIfMissing` / redis `@ConditionalOnClass(StringRedisTemplate)` / jdbc `@ConditionalOnClass(JdbcTemplate)`, 전부 `@ConditionalOnMissingBean(DistributedLock)`). 애스펙트 `@Bean`(`@ConditionalOnClass(org.aspectj.lang.annotation.Aspect)`+`framework.lock.scheduler.enabled` matchIfMissing=true). `LockProperties`(enabled=false·type=memory·default-at-most-for=5m·default-at-least-for=0·scheduler.enabled=true).
5. **등록/배선**: `settings.gradle` include · `META-INF/spring/...AutoConfiguration.imports` · `framework-archtest/build.gradle` 에 project 의존 · **레지스트레이션 가드 테스트**(클래스패스 `.imports` union 읽어 FQCN 단언).
6. **테스트 4종**: `InMemoryDistributedLockTest`(획득/경합/만료재선점/소유자한정/keepUntil/runIfLocked, 순수 JDK) · `JdbcDistributedLockTest`(H2 PostgreSQL모드) · `LockAutoConfigurationTest`(토글 off 기본·on→InMemory+애스펙트·type=redis/jdbc mock·scheduler off·가드) · `SchedulerLockAspectTest`(실 Spring AOP `@EnableAspectJAutoProxy`+가짜 락+Task: 획득시 실행+unlock / 미획득시 스킵 / atLeastFor 조기종료시 keepUntil).
7. **문서 5종 동기화**: `framework/framework-lock/README.md` 신설 · 루트 `README.md`(의존성 블록·요약줄·카탈로그) · `HANDOFF.md`(1·7절) · `docs/FRAMEWORK_MODULES.md`(0·2.7·3·4절) · `STACK.md`(새 의존성 0 노트).

## 현재 상태 (적용/검증)
- ⚠️ **작성 환경 컴파일 미검증**(이 환경은 Maven Central/Gradle 차단·JRE-only). 로직은 sibling 모듈 패턴을 그대로 복제하고 시그니처를 레포 내 동일 사용처로 교차확인했으나 **받는 쪽에서 빌드 필요**.
- 검증 명령: `./gradlew :framework:framework-lock:test :framework:framework-archtest:test spotlessApply`
- 신규 의존성 **0**: redis/jdbc 백엔드는 `compileOnly`(호스트 스타터 있을 때만 활성), 애스펙트는 core 의 `api spring-boot-starter-aspectj`(Boot4 개명) 전이로 충족. 테스트는 data-redis/jdbc(testImplementation, compileOnly 비전이) + H2(testRuntimeOnly). 카탈로그 무변경.

## 켜는 법
```yaml
framework:
  lock:
    enabled: true
    type: redis            # memory(단일JVM) | redis(권장) | jdbc(폐쇄망)
    default-at-most-for: 5m
    scheduler: { enabled: true }
```
```java
@Scheduled(cron = "0 0 2 * * *")
@SchedulerLock(name = "nightlySettlement", atMostFor = "10m", atLeastFor = "30s")
public void settle() { ... }   // 한 파드만 실행, 나머지 스킵
```
- 운영(replicas≥2)은 `type=redis|jdbc` 필수(memory 는 파드 간 미배타). jdbc 는 `db/lock-postgres.sql` DDL 적용 + 호스트에 jdbc 스타터.
- SPI 직접: `lock.runIfLocked("warmup", Duration.ofMinutes(2), () -> ...)` 또는 `tryLock/unlock` 토큰 직접 관리.

## 바로 다음 할 일 (Next)
1. (devops) **CI 게이트**: `:framework-archtest:test` + 전 모듈 `:test` PR 차단 + **멀티모듈 jacoco 집계 리포트**(루트 aggregate).
2. **그릇 정비**: 게이트웨이 런타임 점검(CORS preflight·rate-limit 429) · k8s 멀티서비스/CI-CD(redis/secret/observability ServiceMonitor 실배포).
3. **기본기능 갭 잔여**: 개인정보 로그 마스킹 · PDF 생성 · 분산 캐시(분산 락/스케줄러 리더선출은 framework-lock 으로 완료).
4. 파일 후속(선택): 이미지 처리(썸네일/EXIF) · 대용량 스트리밍(HTTP Range/S3 presigned) · 안티바이러스 훅.
5. (선택) 규제특화 잔여(pki/hsm/recon/egov) · saga 단계별 타임아웃/보상 재시도 · 멱등 재생 페이로드 지문 · 암호화 파일 키 회전.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **Boot4 AOP 스타터 개명**: `spring-boot-starter-aop`→**`spring-boot-starter-aspectj`**. framework-core 가 이미 `api` 로 노출 → 의존 모듈은 AspectJ(`org.aspectj.lang.annotation.Aspect`) 전이 사용 가능(lock 애스펙트가 이에 의존). 애스펙트 빈은 `@ConditionalOnClass(Aspect)` 로 가드.
- **락은 리스+소유자 토큰이 필수 쌍**: TTL 없으면 보유 파드 사망 시 영구 교착. 소유자 토큰 없으면 "내 락 TTL 만료→타 인스턴스 재획득→뒤늦게 끝난 내가 그 락을 오삭제". 그래서 unlock/keepUntil 은 **반드시 소유자 일치 원자 연산**(Redis=Lua CAS, JDBC=`WHERE lock_owner=?`, InMemory=`computeIfPresent` 토큰 비교). 단순 `del key` 금지.
- **`@SchedulerLock` atLeastFor=keepUntil 으로 구현**: 작업이 atLeastFor 보다 빨리 끝나면 unlock 대신 `keepUntil(atLeastFor-경과)` 로 락을 남겨, 파드 간 트리거 클럭스큐로 인한 직후 중복 실행을 막는다(ShedLock 동등). 끝나자마자 unlock 하면 스큐 구간에 두 번 돌 수 있음.
- **JDBC 락 컬럼명 `lock_owner`**(not `owner`): `owner` 는 일부 DB 예약어 → 회피. idempotency 와 동일하게 만료 행은 동일 키 재선점 때만 정리되므로 전체 청소는 운영 잡 별도.
- **애스펙트 테스트는 실 AOP 프록시로**: `ApplicationContextRunner`+`UserConfigurations.of(@EnableAspectJAutoProxy 설정)`+제어 가능한 가짜 락 빈으로 around 동작(실행/스킵/keepUntil)을 검증(`org.springframework.boot.context.annotation.UserConfigurations`). 단순 단위 호출론 프록시 안 걸려 의미 없음.
- (지난·유효) 레지스트레이션 가드(`.imports` union 직접 단언) / introspection=compileOnly 타입 test 재선언 / 토글 3단 기본 off(commoncode·file·openapi 만 matchIfMissing) / Jackson3(`tools.jackson.*`, `.annotation` 만 예외) / 필터예외는 GlobalExceptionHandler 밖 / Boot4 패키지 이동 추측 금지(actuator·EPP 등 공식 확인) / JUnit launcher·starter-test 모듈마다 / 콘솔 UTF-8 3계층.

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. 순수 로직은 Spring 무의존 코어로 분리해 JDK 단독 검증.
2. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`. **테스트가 그 compileOnly 클래스(또는 그게 붙은 컨트롤러/빈)를 참조하면 재선언.** 선택 의존(Tika 류)은 `compileOnly`+가드된 인스턴스화.
3. `settings.gradle`/`imports` 등록. 신규 모듈이면 `framework-archtest/build.gradle` 에 project 의존 추가. **새 오토컨피그는 `.imports` 등록 + 등록 가드 테스트 확인**(미등록=죽은 코드). BOM 밖 의존은 `libs.versions.toml`+root ext+`STACK.md`.
4. Boot4/Spring7/Jackson3 + 통합 대상 실제 시그니처를 레포 내 동일 사용처/공식 소스로 교차확인(Boot4 패키지 이동 추측 금지).
5. 오토컨피그 3단 토글 + 빈 `@ConditionalOnMissingBean`. 런타임 개수 가변 빈은 `ImportBeanDefinitionRegistrar`, 기존 빈 래핑은 `BeanPostProcessor`.
6. **테스트**: 핵심 알고리즘 단위 + 오토컨피그 로딩(enabled/disabled). MapperScan+MyBatis 결합은 임베디드 H2 슬라이스로 enabled 까지. AOP 는 실 프록시(`@EnableAspectJAutoProxy`). 외부연동 WireMock(standalone). 검증 `./gradlew :…:test (+:framework-archtest:test) (+spotlessApply)`.
7. 드롭인: 변경 파일 전부 → 한 zip, 루트에서 `unzip -o`. 문서 5종 동기화.

<!-- 갱신 끝 -->
