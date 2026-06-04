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

## 끄는 법
`framework.batch.enabled: false` / `framework.scheduler.enabled: false` 또는 의존성 미포함.

## 덮어쓰기(프로젝트 커스텀)
`Job`/`Step` 정의는 프로젝트가 구성. 리스너는 빈 등록으로 추가 가능.

## 버전 관리
spring-boot-starter-batch / -quartz 는 Boot BOM. 신규 외부 의존성 없음.
