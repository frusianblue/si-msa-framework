# framework-batch

Spring Batch 6 실행 표준 + Quartz cron 스케줄. `JobLaunchSupport`(재실행 보장)·표준 로깅 리스너·yaml 선언만으로 Job 기동.

## 켜는 법
```gradle
dependencies { implementation project(':framework:framework-batch') }   // spring-boot-starter-batch + quartz 전이
```
```yaml
framework:
  batch:
    enabled: true            # 기본 false
    jobs:                    # yaml 선언만으로 cron 스케줄 등록
      - { name: nightlySettlement, cron: "0 0 2 * * ?", enabled: true, group: framework-batch }
  scheduler:
    enabled: true            # 스케줄러 활성(BatchSchedulerRegistrar)
```

## 쓰는 법
**Job 실행** — `JobLaunchSupport` 가 `JobOperator` 를 래핑하고 `run.id` 파라미터로 동일 Job 재실행을 보장한다.
```java
private final JobLaunchSupport launcher;
launcher.launch(myJob);     // 표준 로깅 리스너(LoggingJobExecutionListener) 자동 부착
```
**스케줄** — `framework.batch.jobs[]` 에 선언한 cron 으로 `BatchJobQuartzJob` 이 트리거.

> **다중 파드 중복 방지**: Quartz 잡은 `spring.quartz.job-store-type=jdbc` 클러스터링으로 막는다. 평범한 `@Scheduled` 의 중복방지는 `framework-lock` 의 `@SchedulerLock`.


## 실전 사용 예 (코드)

잡 본문은 표준 Spring Batch 로 정의하고, **수동 기동은 `JobLaunchSupport`** 로 한다(파라미터를 `Map` 으로 넘기면 잡 파라미터로 변환).
```java
// com.company.framework.batch.JobLaunchSupport
private final JobLaunchSupport launcher;
private final Job settlementJob;

public void runToday() {
    JobExecution exec = launcher.launch(settlementJob,
        Map.of("baseDate", LocalDate.now().toString(), "runId", System.currentTimeMillis()));
    log.info("status={}", exec.getStatus());
}
```
스케줄 기동은 Quartz 연동(`framework.scheduler.*`)으로 yml 만으로 등록한다:
```yaml
framework:
  batch: { enabled: true }
  scheduler:
    enabled: true
    jobs:
      - name: settlementJob
        cron: "0 0 2 * * ?"   # 매일 02:00
```

## 끄는 법
`framework.batch.enabled: false` / `framework.scheduler.enabled: false` 또는 의존성 미포함.

## 덮어쓰기(프로젝트 커스텀)
`Job`/`Step` 정의는 프로젝트가 구성. 리스너는 빈 등록으로 추가 가능.

## 버전 관리
spring-boot-starter-batch / -quartz 는 Boot BOM. 신규 외부 의존성 없음.
