# framework-task

**Spring Cloud Task 5.0.x(Spring Boot 4 네이티브)** 위에 얹는 "오케스트레이션용 단발(run-once) 실행" 표준.
배치 잡을 **외부 오케스트레이터(SCDF) 또는 k8s Job/CronJob 이 기동하고 한 번 실행 후 종료**하는 모델로 만들고,
실행 이력(시작/종료/종료코드/오류)을 태스크 저장소(JDBC=PostgreSQL, DataSource 없으면 인메모리)에 기록한다.

> **`framework-batch` 와의 관계** — 둘은 형제이며 층위가 다르다.
> - `framework-batch` = **인-프로세스 상주** 앱에서 Quartz cron 스케줄 + `JobOperator` 온디맨드 기동(앱이 떠 있는 동안 반복 실행).
> - `framework-task` = **단발 프로세스**(뜨자마자 잡 1회 실행 → 종료). 스케줄/오케스트레이션은 **바깥**(SCDF·k8s CronJob)이 담당.
>
> 같은 `Job` 정의를 두 모델로 모두 굴릴 수 있다. 상주형이면 batch, 컨테이너-잡 1회성이면 task.

---

## ⚠️ 먼저 읽을 것 — SCDF(오케스트레이터)의 현재 상태

- **Spring Cloud Data Flow(SCDF) 서버 본체는 2025-04 오픈소스 유지보수가 종료**되었고 GitHub 레포는 아카이브(`spring-attic`)됐다.
  마지막 OSS 라인은 **2.11.x(Spring Boot 2/3 기반)**, 이후 릴리스는 **Tanzu 상용 전용**(Boot 3.5 방향).
- 반면 **Spring Cloud Task 5.0.x 는 살아 있고 Spring Boot 4.0.x 네이티브**다(spring-cloud-build 5.0.1 → spring-boot 4.0.2). SCDF 가 오케스트레이션하는 **"태스크 단위"는 여전히 Boot 4 로 만들 수 있다.**
- 따라서 본 모듈은 **죽은 SCDF 서버를 번들하지 않는다.** 대신 **Boot 4 네이티브 태스크 앱을 표준화**하고, 오케스트레이션은 환경에 맞게 고른다:

| 오케스트레이션 옵션 | 권장도(금융/공공) | 메모 |
|---|---|---|
| **k8s `CronJob`/`Job` 으로 태스크 앱 직접 기동** | ✅ 1순위(이 스택 기준) | 패치/보안 부담 0, 본 모듈 태스크가 그대로 배포물. 이력은 태스크 저장소(PostgreSQL)·`framework-observability` 로 관측. |
| **Tanzu 상용 SCDF(Boot 3.5 서버) + 본 모듈 Boot 4 태스크 등록** | ⚠️ 라이선스 보유 시 | 서버↔태스크 크로스-부트(actuator 계약·스키마) 검증 필요. UI/DSL/컴포즈드 태스크가 필요할 때. |
| **OSS SCDF 2.11.x(EOL) 서버** | ❌ 비권장 | 보안 패치 없음 → 금융/공공 부적합. |

> **즉, "오늘 실제 투입"의 1차 형태 = k8s CronJob 으로 도는 표준 태스크 앱.** SCDF UI 가 정책상 필요해지면 위 표의 상용/EOL 경로로 같은 태스크를 등록만 하면 된다(태스크 앱은 오케스트레이터-중립적으로 만들어 둠).

---

## 켜는 법
```gradle
dependencies { implementation project(':framework:framework-task') }   // spring-boot-starter-batch + spring-cloud-task(core/batch) 전이
```
```java
// 앱 메인에 부착 → 태스크 실행 이력 기록 활성(= Spring Cloud Task @EnableTask 의 프레임워크 별칭)
@SpringBootApplication
@EnableFrameworkTask
public class SettlementTaskApplication { /* ... */ }
```
```yaml
framework:
  task:
    enabled: true                 # 기본 false. 켜면 표준 감사 로깅 리스너(FrameworkTaskExecutionListener) 등록.
spring:
  batch:
    job:
      name: settlementJob         # 태스크 모델: 기동 시 이 Job 1회 자동 실행
  cloud:
    task:
      # 단일 인스턴스 락(같은 이름 태스크 동시 1개만) — 멀티 파드/중복 트리거 방지
      single-instance-enabled: true
      batch:
        # 배치 실패 → 프로세스 비정상 종료코드(≠0). k8s Job/SCDF 가 실패를 감지하는 핵심 스위치.
        fail-on-job-failure: true
```

> **역할 분리 주의**
> - `@EnableFrameworkTask` = 태스크 **실행 이력 기록**을 켠다(필수).
> - `framework.task.enabled=true` = 그 위에 **프레임워크 표준 감사 로깅**(`FrameworkTaskExecutionListener`)을 더한다.
> - 태스크 동작 세부(테이블 prefix·단일 인스턴스·실패 종료코드)는 **Spring Cloud Task 표준 프로퍼티 `spring.cloud.task.*`** 로 제어한다(프레임워크가 재정의하지 않음).

## 쓰는 법
- **이력 기록**: `@EnableFrameworkTask` 가 `TaskLifecycleListener` 를 활성화 → 프로세스 1회 실행이 `TaskExecution`(시작/종료/종료코드/오류)으로 저장소에 남는다.
- **표준 로깅**: `FrameworkTaskExecutionListener` 가 시작/종료/실패를 표준 포맷으로 로깅(SCT 가 `TaskExecutionListener` 빈을 자동 수집).
- **배치 연동**: 앱에 `Job` 빈 + `@EnableFrameworkTask` 가 있으면 SCT 의 `TaskBatchAutoConfiguration` 이 `TaskBatchExecutionListener` 를 자동 구성 → **배치 `JobExecution` 이 `TaskExecution` 에 연결**(오케스트레이터가 태스크 안에서 배치 스텝 상세를 본다).
- **DDL**: 태스크 메타 테이블(`TASK_EXECUTION` 등)은 Spring Cloud Task 의 초기화기가 만든다(운영은 Flyway 로 선반영 권장 — 아래 PITFALLS 참조).

## 실전 사용 예 (코드)

**① 단발 정산 태스크 앱** — 뜨면 `settlementJob` 을 1회 실행하고 종료, 이력은 PostgreSQL 태스크 저장소에 기록.
```java
// com.company.framework.task.EnableFrameworkTask
@SpringBootApplication
@EnableFrameworkTask
public class SettlementTaskApplication {
    public static void main(String[] args) {
        SpringApplication.run(SettlementTaskApplication.class, args);
    }

    // 표준 Spring Batch 6 Job 정의 (org.springframework.batch.core.job.Job)
    @Bean
    Job settlementJob(JobRepository repo, Step step) {
        return new JobBuilder("settlementJob", repo).start(step).build();
    }

    @Bean
    Step step(JobRepository repo, PlatformTransactionManager tx) {
        return new StepBuilder("settle", repo)
                .tasklet((contribution, chunkContext) -> {
                    // ... 정산 로직 ...
                    return RepeatStatus.FINISHED;
                }, tx)
                .build();
    }
}
```
```yaml
framework: { task: { enabled: true } }
spring:
  batch: { job: { name: settlementJob } }     # 기동 시 1회 실행
  cloud:
    task:
      single-instance-enabled: true            # 동시 1개만
      batch: { fail-on-job-failure: true }      # 실패 시 exit code ≠ 0
```
실행 시 표준 로그:
```
[task]  start name=settlementJob executionId=42 externalExecutionId=null args=[--baseDate=2026-06-07]
[batch] start job=settlementJob executionId=101 params=...
[batch] end   job=settlementJob executionId=101 status=COMPLETED exitCode=COMPLETED durationMs=812
[task]  end   name=settlementJob executionId=42 exitCode=0 durationMs=False...   # ← 정상 0, 실패 시 ≠0
```

**② 인자 전달 실행(컨테이너/CLI)** — 태스크 인자는 표준 Boot 인자로 전달, 잡 파라미터는 `--`.
```bash
java -jar settlement-task.jar \
  --spring.batch.job.name=settlementJob \
  --baseDate=2026-06-07
# 종료코드로 성공/실패 판별:  echo $?   (0=성공, ≠0=실패; fail-on-job-failure=true 필요)
```

**③ k8s `CronJob` 으로 매일 02:00 기동(= 오늘의 1순위 오케스트레이션)** — Docker Desktop kind/실클러스터 공통.
```yaml
apiVersion: batch/v1
kind: CronJob
metadata: { name: settlement-task, namespace: apps }
spec:
  schedule: "0 2 * * *"          # 매일 02:00 (kube 표준 cron, 클러스터 TZ 주의)
  concurrencyPolicy: Forbid       # 중복 실행 금지(앱쪽 single-instance-enabled 와 이중 안전망)
  jobTemplate:
    spec:
      backoffLimit: 2             # 실패 시 재시도(태스크는 멱등 설계 권장)
      template:
        spec:
          restartPolicy: Never
          containers:
            - name: settlement
              image: registry.local/settlement-task:1.0.0
              args: ["--spring.batch.job.name=settlementJob"]
              envFrom: [{ secretRef: { name: settlement-db } }]   # SPRING_DATASOURCE_*
```
> `restartPolicy: Never` + `backoffLimit` 로 k8s 가 종료코드 기반 성공/실패를 판정한다 → **`fail-on-job-failure: true` 가 반드시 필요**(아니면 잡이 던져도 exit 0 으로 "성공" 처리됨).

**④ (선택) SCDF 에 등록할 때** — 상용/EOL SCDF 서버를 쓰는 조직이라면, 위 태스크 앱을 그대로 앱으로 등록:
```bash
# SCDF Shell / REST — 오케스트레이터는 본 태스크 앱(도커 이미지)을 그대로 launch 한다.
dataflow:> app register --name settlement --type task --uri docker:registry.local/settlement-task:1.0.0
dataflow:> task create settlement-task --definition "settlement"
dataflow:> task launch settlement-task --arguments "--spring.batch.job.name=settlementJob"
```

## 끄는 법
`framework.task.enabled: false`(표준 로깅만 끔) 또는 의존성 미포함. 이력 기록까지 끄려면 `@EnableFrameworkTask` 제거(또는 `@EnableTask` 미사용).

## 덮어쓰기(프로젝트 커스텀)
- `Job`/`Step`/`Tasklet` 은 프로젝트가 정의.
- 태스크 저장소/네임/락 커스터마이즈는 `TaskConfigurer`(또는 `DefaultTaskConfigurer`) 빈으로 교체.
- 추가 리스너는 `TaskExecutionListener` 빈을 등록하면 자동 수집된다.

## 버전 관리
- `spring-boot-starter-batch` = Spring Boot BOM.
- **Spring Cloud Task = `spring-cloud-task-dependencies` BOM(별도)** — `gradle/libs.versions.toml` 의 `springCloudTask`(현재 `5.0.1`)로 고정, 루트 `dependencyManagement` 에서 import. Spring Boot BOM / Spring Cloud(Oakwood) BOM 어디에도 없으므로 별도 관리 필수.
- `spring-cloud-starter-task` 는 **의도적으로 미사용**(spring-cloud-task-stream + 스트림 바인더까지 끌고 오므로) → `spring-cloud-task-core` + `spring-cloud-task-batch` 직접 의존.
