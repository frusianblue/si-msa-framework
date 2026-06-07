# batch-types — Spring Batch 6 처리 방식(유형) · DB 리더/라이터 카탈로그

청크(chunk) 한 가지가 아니라, **스텝을 처리하는 방식**과 **DB를 읽고 쓰는 방식**이 여러 가지임을
패턴별 실행 가능한 잡으로 보여 준다. 각 잡은 `--spring.batch.job.name=<잡>` 으로 골라 1회 실행하며,
샘플 데이터(`txn` 12행)로 **독립 실행**된다.

> 기본 청크/태스크릿/멀티스텝/내결함의 기초는 `examples/batch-cookbook`, 단발 정산은 `examples/batch-task-reference` 참고.
> 이 예제는 그 위에 **멀티스레드·파티셔닝·플로우·처리기체인**과 **JdbcPaging·MyBatis·StoredProcedure** 를 더한다.

---

## 처리 방식(스텝 모델)

| 잡 이름 | 방식 | 핵심 |
|---|---|---|
| `multiThreadedChunkJob` | 멀티스레드 청크 | 한 스텝을 여러 스레드로(`.taskExecutor`). 리더는 반드시 **페이징**(커서는 스레드 불안전) |
| `partitionedJob` | 로컬 파티셔닝 | `Partitioner` 로 id 범위 분할 → 워커 스텝을 파티션마다 병렬 실행. 대용량/원격 분산 확장 |
| `flowControlJob` | 플로우 제어 | 종료 상태로 분기(`on/to`) + 두 스텝 병렬(`split`) |
| `compositeProcessorJob` | 처리기 체인 | `CompositeItemProcessor` 로 검증→보강을 순차 합성 |

## DB 리더/라이터 종류

| 잡 이름 | 리더 → 라이터 | 핵심 |
|---|---|---|
| `multiThreadedChunkJob` | `JdbcPagingItemReader` → `JdbcBatchItemWriter(beanMapped)` | 페이징(스레드 안전), 빈 매핑 |
| `mybatisJob` | `MyBatisPagingItemReader` → `MyBatisBatchItemWriter` | 매퍼 XML statement, 프레임워크 영속 스택 |
| `storedProcedureJob` | `StoredProcedureItemReader`(refcursor) → `JdbcBatchItemWriter(itemPreparedStatementSetter)` | 저장 프로시저 결과 집합, 위치형 `?` 매핑(record 그대로) |

---

## 실행

```bash
# 0) (한 번) 프레임워크 태스크 모듈을 로컬에 publish — 이 예제가 com.company:framework-task 를 소비
./gradlew :framework:framework-task:publishToMavenLocal      # repo 루트에서

# 1) PostgreSQL
docker run -d --name types-pg -p 5432:5432 \
  -e POSTGRES_DB=appdb -e POSTGRES_USER=app -e POSTGRES_PASSWORD=app postgres:16

# 2) 잡 선택 실행
cd examples/batch-types
./gradlew bootRun --args='--spring.batch.job.name=multiThreadedChunkJob'
./gradlew bootRun --args='--spring.batch.job.name=partitionedJob'
./gradlew bootRun --args='--spring.batch.job.name=flowControlJob'
./gradlew bootRun --args='--spring.batch.job.name=compositeProcessorJob'
./gradlew bootRun --args='--spring.batch.job.name=mybatisJob'
./gradlew bootRun --args='--spring.batch.job.name=storedProcedureJob'
```

CronJob(6종)은 `k8s/cronjobs.yaml` 참고.

---

## 잡별 "돌리면 무슨 일이 일어나나" + 확인 SQL

### 1) `multiThreadedChunkJob` — 멀티스레드 청크 + 페이징 리더
`txn` 전체를 4스레드로 나눠 금액 등급(A≥100만, B≥10만, C)을 매겨 `txn_graded` 적재.
페이징 리더를 쓰는 이유: 멀티스레드 스텝에서 **커서 리더는 안전하지 않다**(단일 ResultSet 공유).
페이징은 페이지 단위로 끊어 읽고 결정적 정렬(`sortKeys`)이 필수.
```sql
SELECT grade, count(*) FROM txn_graded GROUP BY grade ORDER BY grade;
-- A=2(120만,250만 건), B=5, C=5
```

### 2) `partitionedJob` — 로컬 파티셔닝
`IdRangePartitioner` 가 `txn.id` 의 [min,max] 를 4구간으로 쪼개고, 각 구간을 **워커 스텝**이 병렬로
읽어 수수료 1.5% 차감 후 `txn_settled` 적재. 워커 리더는 `@StepScope` 라 파티션마다 새로 생성되고
`stepExecutionContext` 의 `minId/maxId` 를 주입받는다(자기 범위만 읽음).
```sql
SELECT count(*), round(sum(net_amount),0) FROM txn_settled;  -- 12행
-- 로그에서 "partition0 = id 1..3" 식 4개 파티션 라인 확인
```

### 3) `flowControlJob` — 조건 분기 + 병렬 split
`validate → (성공) → [enrichA ‖ notifyB] → finalize`. 검증이 FAILED 면 `failAlert` 로 분기.
기본은 정상 경로(로그로 흐름 확인). 데이터 변경 없음(흐름 데모).
```text
로그: validateStep → [병렬A]/[병렬B](스레드 batch-flow-*) → finalizeStep
```

### 4) `compositeProcessorJob` — 처리기 체인
커서로 `txn` 을 읽어 **validate(취소 거래 필터) → enrich(수수료/실수령/카테고리)** 순으로 합성 처리해
`txn_enriched` 적재. CANCELED 2건은 제외된다.
```sql
SELECT category, count(*) FROM txn_enriched GROUP BY category;  -- LARGE=2, NORMAL=8 (취소 2건 제외 → 10행)
```

### 5) `mybatisJob` — MyBatis 리더/라이터
매퍼 XML(`mapper/SettlementMapper.xml`)의 `selectTxnPage`(페이징)로 읽어 `insertOut`(배치)으로
`txn_mybatis_out` 적재. 이 프레임워크의 영속 스택(MyBatis)으로 배치를 구성하는 정석.
```sql
SELECT count(*) FROM txn_mybatis_out;  -- 12행
```
> ⚠️ Batch 6 에선 **mybatis-spring 4.0.0+** 필수(배치 클래스가 신패키지 `infrastructure.item.*` 사용).
> 3.0.x 는 Batch 5 패키지라 비호환. mybatis-spring-boot-starter 는 아직 3.0.5 를 끌어오므로
> `build.gradle` 에서 `org.mybatis:mybatis-spring:4.0.0` 을 직접 선언했다.

### 6) `storedProcedureJob` — 저장 프로시저 리더
PL/pgSQL 함수 `get_approved_txn()` 이 APPROVED 거래를 `refcursor` 로 돌려주고,
`StoredProcedureItemReader` 가 `.function()` + `refCursorPosition(1)` 로 한 행씩 읽어 `txn_proc_out` 적재.
라이터는 `itemPreparedStatementSetter`(위치형 `?`)라 record(TxnRow)를 게터 없이 그대로 쓴다.
```sql
SELECT count(*) FROM txn_proc_out;  -- 10행 (APPROVED 만; 취소 2건 제외)
```
> ⚠️ PostgreSQL `refcursor` 는 **트랜잭션 안에서만** 유효 — 청크 스텝의 트랜잭션 경계 안에서 호출/소비된다.

---

## 공통 동작/이력 확인

```sql
SELECT task_name, exit_code, start_time, end_time FROM TASK_EXECUTION ORDER BY task_execution_id DESC;
SELECT job_name, status, exit_code FROM BATCH_JOB_EXECUTION
  JOIN BATCH_JOB_INSTANCE USING (job_instance_id) ORDER BY job_execution_id DESC;
```

## 메모(설계 선택)
- **빈 매핑 vs 위치형**: `beanMapped()`/MyBatis 파라미터는 JavaBean 게터가 필요(record 불가). record 입력을
  그대로 쓰려면 `itemPreparedStatementSetter`(위치형 `?`). 그래서 출력 타입은 게터 클래스, refcursor 잡은 record+위치형으로 구성했다.
- **멀티스레드 vs 파티셔닝**: 전자는 "한 스텝 내부 동시성", 후자는 "스텝을 N개로 복제". 후자가 더 큰 규모와
  원격 분산(remote partitioning)으로 확장된다.
- **자동 DDL off**: `TASK_*`/`BATCH_*`/도메인/함수 전부 Flyway 소유(`initialize-schema=never`, `initialize-enabled=false`).
