# batch-cookbook — 실무 배치 패턴 5종 (실행 가능한 예제)

`framework-task`(Spring Cloud Task) + Spring Batch 6 으로 **실제 프로젝트에서 자주 쓰는 배치 패턴**을 한 앱에 담았다.
각 Job 은 독립적으로 실행되며, `--spring.batch.job.name=<jobName>` 으로 골라 1회 실행한다(run-once 태스크).

> 짝꿍 예제: 가장 단순한 단일 잡 엔드투엔드는 [`../batch-task-reference`](../batch-task-reference). 스케줄링/모니터링 UI 선택은 [`docs/guide/BATCH_SCHEDULING_AND_UI.md`](../../docs/guide/BATCH_SCHEDULING_AND_UI.md).

## 담긴 Job 한눈에

| jobName | 패턴 | 읽기→쓰기 | 실무 예 |
|---|---|---|---|
| `fileIngestJob` | 파일 → DB 적재 | `FlatFileItemReader`(CSV) → `JdbcBatchItemWriter` | 외부기관/가맹점 거래파일 수신 적재 |
| `reportExportJob` | DB → 파일 추출 | `JdbcCursorItemReader` → `FlatFileItemWriter`(CSV) | 대외 송신·EAI 연계 파일 생성 |
| `dormantAccountJob` | 다단계(Tasklet→Chunk) | step1 마킹 → step2 전환 | 휴면계좌 전환(선 마킹, 후 일괄처리) |
| `purgeJob` | 단일 Tasklet | `DELETE`(JdbcTemplate) | 보관기한 지난 로그/데이터 정리 |
| `interestAccrualJob` | 내결함 Chunk + 재실행 | reader → processor → writer (`skip`/`retry`/`RunIdIncrementer`) | 이자 계산(불량행 skip, 일시장애 retry) |

각 잡의 코드는 `src/main/java/com/example/batchcookbook/jobs/<패턴>/` 에 패턴별로 분리돼 있다.

## 실행 (로컬)

1. PostgreSQL 하나 띄우기:
```bash
docker run -d --name cookbook-pg -p 5432:5432 \
  -e POSTGRES_DB=appdb -e POSTGRES_USER=app -e POSTGRES_PASSWORD=app postgres:16
```

2. (한 번) 프레임워크 태스크 모듈을 로컬에 publish — 이 예제가 `com.company:framework-task:1.0.0` 을 소비:
```bash
# si-msa-framework 루트에서
./gradlew :framework:framework-task:publishToMavenLocal
```

3. 원하는 Job 실행 (Flyway 가 첫 실행에서 TASK_*/BATCH_*/도메인 스키마를 만든다):
```bash
./gradlew bootRun --args='--spring.batch.job.name=fileIngestJob'
./gradlew bootRun --args='--spring.batch.job.name=reportExportJob'
./gradlew bootRun --args='--spring.batch.job.name=dormantAccountJob'
./gradlew bootRun --args='--spring.batch.job.name=purgeJob'
./gradlew bootRun --args='--spring.batch.job.name=interestAccrualJob'
```

## 패턴별 설명과 "돌리면 무슨 일이 일어나나"

### 1) `fileIngestJob` — 파일(CSV) → DB
- `src/main/resources/sample/transactions.csv`(5행, 헤더 1줄)를 읽어 `incoming_transaction` 에 적재.
- 헤더는 `linesToSkip(1)`, 컬럼은 `delimited().names(...)`, 값 파싱은 `fieldSetMapper`(LocalDate/BigDecimal 직접 변환).
- 입력을 바꾸려면: `--app.ingest.input=file:/path/to/real.csv`.
- 결과: `SELECT count(*) FROM incoming_transaction;` → 5.

### 2) `reportExportJob` — DB → 파일(CSV)
- `settlement_result`(샘플 4행)를 읽어 `/tmp/settlement-report.csv` 로 추출(헤더 + 쉼표 구분).
- 출력 경로: `--app.report.output=/원하는/경로.csv`. 재실행 시 `shouldDeleteIfExists(true)` 로 덮어쓴다.
- 결과: `head /tmp/settlement-report.csv` → `merchant_id,trade_date,net_amount` + 4행.

### 3) `dormantAccountJob` — 다단계(Tasklet → Chunk)
- step1(Tasklet): 1년 넘게 활동 없는 ACTIVE 계좌를 `dormant_candidate=true` 로 마킹(집합 UPDATE 1방).
- step2(Chunk): 후보를 한 건씩 읽어 `status='DORMANT'` 로 전환.
- 스텝 경계 = 재시작 지점. step1 성공 후 step2 실패 시 step2 부터 재시작된다.
- 결과: 샘플상 account #2, #3 이 DORMANT 로 바뀐다(#1 최근활동·#4 이미 휴면 제외).

### 4) `purgeJob` — 단일 Tasklet
- `audit_log` 에서 보관기한(`app.purge.retention-days`, 기본 90) 초과 행을 DELETE.
- 결과: 샘플 4행 중 100일·200일 된 2행 삭제, 5일·1일 된 2행 보존.

### 5) `interestAccrualJob` — 내결함 Chunk + 재실행
- `deposit`(샘플 5행)에서 이자(`잔액×이율`) 계산 → `interest_accrual` 적재.
- **skip**: 음수 잔액 행(#4)은 `IllegalArgumentException` → 건너뜀(`skipLimit=50`).
- **필터**: 잔액 0 행(#3)은 processor 가 `null` 반환 → 라이터로 안 감.
- **retry**: 일시적 장애(`TransientDataAccessException`)는 `retryLimit=3` 재시도(영구 오류는 대상 아님).
- **RunIdIncrementer**: 같은 파라미터로 다시 돌려도 `run.id` 증가 → 새 JobInstance(“이미 완료” 거부 회피).
- 결과: `interest_accrual` 에 #1(25000), #2(155000), #5(3750) 3행. #3 필터·#4 skip.

## 실행 이력 확인 (framework-task)

어떤 Job 을 돌리든 `@EnableFrameworkTask` 가 실행 이력을 남긴다:
```sql
SELECT task_name, start_time, end_time, exit_code, exit_message FROM TASK_EXECUTION ORDER BY task_execution_id DESC;
-- 배치 결합 시 어느 JobExecution 이 어느 TaskExecution 에 속했는지:
SELECT * FROM TASK_TASK_BATCH;
-- 배치 자체 이력:
SELECT job_name, status, exit_code FROM BATCH_JOB_EXECUTION e JOIN BATCH_JOB_INSTANCE i USING (job_instance_id);
```
배치가 실패하면(`fail-on-job-failure=true`) 프로세스 종료코드가 0이 아니게 되어 `echo $?` 로 확인되고, k8s CronJob 은 이를 실패로 본다.

## 컨테이너 / k8s

```bash
docker build -t registry.local/batch-cookbook:1.0.0 .
```
Job 별 CronJob 은 `k8s/cronjobs.yaml`(같은 이미지, `--spring.batch.job.name` 인자만 다름). 필요한 것만 적용한다.

## Batch 6 주의 (이 예제가 지키는 것)

- **인프라 아이템 패키지 이동**: reader/writer/processor/`Chunk`/`RepeatStatus` 는 `org.springframework.batch.infrastructure.item.*`(+`infrastructure.repeat`). core(Job/Step/builder)만 `org.springframework.batch.core.*`. `RunIdIncrementer` 는 `core.job.parameters`(5.x `launch.support` 아님).
- **`beanMapped()`/writer `names()` 는 게터 필요** → 라이터 입력 타입은 JavaBean/게터 클래스(record 금지). 리더가 만드는 입력은 record 가능(예: `DepositRow`).
- **`FlatFileItemWriter.resource` 는 WritableResource** → `file:` 로케이션 대신 `FileSystemResource` 직접 생성.
- 자동 DDL 끔(`spring.batch.jdbc.initialize-schema=never`, `spring.cloud.task.initialize-enabled=false`) → 스키마는 Flyway 가 소유.
