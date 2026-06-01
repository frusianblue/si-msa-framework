# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**framework-batch** 완성 — **Spring Batch 6 실행 헬퍼**(`JobLaunchSupport`: JobOperator 래핑 + 매 실행 고유 run.id 로 재실행 보장 + 체크예외→런타임), **표준 로깅 리스너**, **Quartz cron 스케줄**(yaml `framework.scheduler.jobs[*].{name,cron}` 선언만으로 배치 Job 기동). 새 외부 의존성 0(batch/quartz 는 Boot BOM). **Boot4↔Batch6 패키지 대이동을 전부 GitHub 소스로 검증**.

## 최종 갱신
- 일자: 2026-06-01 · 갱신자: <!-- 채우기 -->
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)** / **Spring Batch 6**

## 무엇을 했나 (Done)
- **신규 `framework/framework-batch/`** ([선택], `framework.batch.enabled=false`·`framework.scheduler.enabled=false` 기본). **새 외부 의존성 0**(spring-boot-starter-batch/-quartz = Boot BOM). batch/quartz 타입은 소비 서비스가 직접 쓰므로 **api 전이**.
  - **`JobLaunchSupport`**(launch) — Batch 6 `JobOperator.start(Job, JobParameters)` 래핑(구 `JobLauncher` 는 6.0 deprecated). 매 실행 고유 `framework.run.id`(addLong) 자동 추가 → 파라미터 없/같은 Job 도 재실행(JobInstanceAlreadyComplete 회피). 체크예외 `JobExecutionException` 계열 → `BatchLaunchException`(런타임) 래핑. `launch(Job)`/`launch(Job,JobParameters)`/`launch(Job,Map)` 3종.
  - **`LoggingJobExecutionListener`**(listener) — before/after 잡명·executionId·status·exitCode·소요시간 로깅(감사 친화). **전역 자동부착 아님** → 앱이 `JobBuilder.listener(...)` 로 부착(자바독에 예시).
  - **Quartz 스케줄**(scheduler) — `BatchJobQuartzJob`(QuartzJobBean): 발화 시 스케줄러 컨텍스트의 ApplicationContext 에서 `jobName` 빈 + JobLaunchSupport 조회해 기동(자동주입 비의존). `BatchSchedulerRegistrar`(SmartLifecycle, phase=MAX): `framework.scheduler.jobs[*]` 읽어 cron 검증(`CronExpression.isValidExpression`, 실패=fail-fast) 후 JobDetail+CronTrigger 등록(재기동 시 delete→schedule 로 갱신). 기본 RAM JobStore.
  - 오토컨피그 `FrameworkBatchAutoConfiguration` — `@AutoConfiguration(afterName={Boot batch/quartz autoconfig})` + `@ConditionalOnClass(JobOperator)`. batch 빈은 `framework.batch.enabled=true`+`@ConditionalOnBean(JobOperator)`. 스케줄러 nested 구성은 `framework.scheduler.enabled=true`+`@ConditionalOnClass(Scheduler,QuartzJobBean)`, 등록기 빈은 `@ConditionalOnBean(Scheduler)`+`framework.batch.enabled=true`(=batch 도 켜야 함).
- **등록/문서**: `settings.gradle`(batch include) · `STACK.md` 3.2(batch/quartz 행) · `docs/FRAMEWORK_MODULES.md`(2.4 ✅ + 진행현황). **libs.versions.toml 무변경**(버전 0).

## 현재 상태 (적용/검증)
- 신규 파일 모두 repo 반영. 정적 점검 통과(중괄호/괄호 균형, 패키지=디렉터리, Jackson2 import 0, deprecated JobLauncher import 0).
- **Boot4/Batch6 API 를 GitHub 소스로 직접 확정**(아래 함정 참조). 의존성 zero 라 카탈로그 무변경.
- ⚠️ **실제 gradle 컴파일 미검증**(작성 환경 차단). 받는 쪽: `./gradlew :framework:framework-batch:compileJava` + `./gradlew spotlessApply`. **배치 메타테이블 필요** — 서비스에서 `spring.batch.jdbc.initialize-schema=always`(또는 Flyway 로 `org/springframework/batch/core/schema-*.sql` 적용). DataSource/트랜잭션매니저 없으면 Boot 가 JobOperator 미생성 → 모듈 우아하게 비활성.

## 켜는 법 (application.yml)
```yaml
framework:
  batch:
    enabled: true                    # JobLaunchSupport / LoggingJobExecutionListener
  scheduler:
    enabled: true                    # Quartz cron (batch.enabled 도 필요)
    jobs:
      - name: settlementJob          # Spring Batch Job '빈 이름'
        cron: "0 0 2 * * ?"          # 매일 02:00 (Quartz: 초 분 시 일 월 요일)
        enabled: true
spring:
  batch:
    jdbc: { initialize-schema: always }   # 배치 메타테이블 자동생성(또는 Flyway)
  # quartz: { job-store-type: jdbc }      # 다중 인스턴스 1회 실행 필요 시(클러스터)
```
사용: `JobLaunchSupport` 주입 → `launch(myJob)`. 로깅은 Job 정의에서 `.listener(loggingJobExecutionListener)` 부착.

## 바로 다음 할 일 (Next)
1. 받는 쪽에서 **batch 모듈 컴파일 확인** + `spotlessApply`. 배치 메타테이블 초기화(initialize-schema 또는 Flyway) 잊지 말 것.
2. **업무 생산성 잔여** — framework-notification(mail/sms/알림톡 채널 추상화: `channels.{mail,sms}.enabled`, 메일=starter-mail/BOM, SMS·알림톡은 벤더 어댑터 인터페이스 + 더미/로깅 구현부터).
   - 또는 **messaging 소비자측**: 멱등 소비(컨슈머) — Kafka 헤더 `x-event-id` 키로 기존 framework-idempotency 연계.
3. 이후: 규제특화(pki/mfa/hsm/recon/egov) → observability → 게이트웨이/k8s/CI-CD 멀티서비스화. (상세 `docs/FRAMEWORK_MODULES.md` 4절)

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **Spring Batch 6(Boot 4) 패키지 대이동** — 추측 금지, GitHub 소스로 확정함:
  - `org.springframework.batch.core.job.{Job, JobExecution, JobExecutionException}` (구 `...core.Job` 등에서 이동)
  - `org.springframework.batch.core.job.parameters.{JobParameters, JobParametersBuilder, RunIdIncrementer, InvalidJobParametersException}`
  - `org.springframework.batch.core.listener.JobExecutionListener`
  - `org.springframework.batch.core.launch.{JobOperator, JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException, NoSuchJobException}`
  - `org.springframework.batch.core.{Entity(getId():long), ExitStatus}` 는 그대로.
- **`JobLauncher` 는 6.0 deprecated(forRemoval) → `JobOperator` 사용**(JobOperator extends JobLauncher). 비-deprecated 실행 = `JobExecution start(Job, JobParameters)`. Boot 가 `JobOperator` 빈 자동구성(`DefaultBatchConfiguration`). `JobExplorer`/`JobRegistry` 도 통합·자동화(별도 빈 불요).
- **start() 체크예외는 모두 `JobExecutionException(core.job)` 상속** → catch 하나로 처리.
- **Boot 4 autoconfigure 패키지 분리**(jdbc 선례와 동일): 배치=`org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration`, 퀄츠=`org.springframework.boot.quartz.autoconfigure.QuartzAutoConfiguration`. `@AutoConfiguration(afterName=...)` 에 이 FQCN 사용.
- **Quartz vs Batch 의 `JobExecutionException` 이름 충돌**: Quartz 잡(`BatchJobQuartzJob`)에선 `org.quartz.JobExecutionException` 만 import(배치 예외는 JobLaunchSupport 가 이미 런타임으로 래핑하므로 거기선 안 보임).
- **QuartzJobBean 은 Spring 자동주입 비의존이 안전** → 등록기가 ApplicationContext 를 `scheduler.getContext()` 에 넣고, 잡이 런타임에 `getBean(jobName)`/`getBean(JobLaunchSupport)` 조회.
- **스케줄러는 batch 와 동반 필요**: 등록기 빈을 `framework.scheduler.enabled` + `framework.batch.enabled` 둘 다로 게이팅(같은 오토컨피그 내 `@ConditionalOnBean(JobLaunchSupport)` 순서 취약성 회피 — Scheduler 만 ConditionalOnBean).
- **Batch 메타테이블 필요**: `spring.batch.jdbc.initialize-schema` 또는 Flyway. 안 하면 기동/실행 시 테이블 없음 오류.
- (기존) **새 모듈은 `settings.gradle` 등록 필수** · BOM 밖 새 라이브러리만 카탈로그 핀(이번엔 0) · 트랜잭션매니저 새로 정의 금지 · 필터에서 BusinessException 금지 · SXSSF 종료는 `close()`(dispose deprecated).

## 모듈 추가 레시피 (검증된 반복 절차)
1. `framework/framework-<X>/` 생성: `config`(Properties+AutoConfiguration) · 도메인 패키지 · `resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`(FQCN 등록).
2. `build.gradle`: `api project(':framework:framework-core')`(+필요 모듈) + starter 는 능력 전이면 `api`(batch/quartz·kafka), 내부 구현이면 `implementation`(POI), 호스트 제공이면 `compileOnly`(web/jdbc). **BOM 밖 새 라이브러리만** `libs.versions.toml`+루트 ext 브리지로 고정.
3. **`settings.gradle` 에 include 추가**(잊지 말 것).
4. 코드 작성 전 **Boot4/Spring7/Jackson3 + 외부 라이브러리(Batch6/Quartz/POI) API 를 공식 소스(GitHub raw)로 확정**(추측 금지 — 메이저 버전업은 패키지 이동 잦음, 컴파일 미검증 환경).
5. 오토컨피그: `@AutoConfiguration(afterName=Boot 관련 autoconfig)` + `@ConditionalOnClass(마커)` + `@ConditionalOnProperty(framework.<x>.enabled=true)` + 빈 `@ConditionalOnMissingBean`. 다른 빈 의존은 `@ConditionalOnBean`(순서 취약 시 property 게이팅으로 대체).
6. 검증: `./gradlew :framework:framework-<X>:compileJava` (+`spotlessApply`).
7. 드롭인: 모듈 폴더 + 변경 파일(완성 `settings.gradle`/필요 시 `libs.versions.toml`·루트 `build.gradle`·문서) 을 한 zip 에 담아 루트에서 `unzip -o`.


<!-- 갱신 끝 -->
