# batch-task-reference — Spring Cloud Task + Spring Batch 실투입 레퍼런스

> **"Spring Cloud Task + Spring Batch" 조합을 실제 프로젝트에 바로 가져다 쓸 수 있는** 최소·완결 예제.
> 청크지향 정산 배치를 **단발(run-once) 태스크**로 실행하고, 실행 이력을 PostgreSQL 에 남긴다.
> 스택: Spring Boot 4.0.6 / Java 21 / Spring Batch 6.0.x / Spring Cloud Task 5.0.1 / PostgreSQL / Flyway.

이 디렉터리는 **독립(standalone) Gradle 빌드**다(루트 멀티모듈 `settings.gradle` 에 포함하지 않음). 실제 프로젝트가 프레임워크를 *라이브러리로 소비*하는 모습을 그대로 보여 주기 위함이다.

---

## 무엇이 들어 있나

| 파일 | 역할 |
|---|---|
| `SettlementTaskApplication` | `@EnableFrameworkTask` 부착. 뜨면 `settlementJob` 1회 실행 → 종료. 종료코드를 컨테이너로 전파. |
| `batch/SettlementJobConfig` | 청크지향 Job: `JdbcCursorItemReader`(커서 스트리밍) → `SettlementProcessor` → `JdbcBatchItemWriter`(beanMapped). `faultTolerant().skip(...)`. |
| `batch/RawTransaction` / `SettlementRecord` / `SettlementProcessor` | 리더 입력 / 라이터 출력(게터) / 수수료 계산·필터·스킵 로직. |
| `db/migration/V1__task_schema.sql` | **Spring Cloud Task** 메타(TASK_*) — Flyway 선반영. |
| `db/migration/V2__batch_schema.sql` | **Spring Batch 6** 메타(BATCH_*) — Flyway 선반영. |
| `db/migration/V3__settlement_domain.sql` | 도메인 테이블 + 샘플 데이터. |
| `Dockerfile` / `k8s/cronjob.yaml` | 컨테이너화 + **k8s CronJob**(매일 02:00, 중복금지, 종료코드 기반 실패감지). |

핵심 운영 스위치(`application.yml`):
- `spring.batch.jdbc.initialize-schema=never` + `spring.cloud.task.initialize-enabled=false` → **자동 DDL 끔, Flyway 가 스키마 소유**(운영 표준).
- `spring.cloud.task.batch.fail-on-job-failure=true` → 배치 실패 시 **프로세스 종료코드 ≠0**(k8s/SCDF 가 실패 감지).
- `spring.cloud.task.single-instance-enabled=true` → 같은 이름 태스크 **동시 1개만**(TASK_LOCK).
- `framework.task.enabled=true` → 표준 감사 로깅 리스너.

---

## 로컬 실행

```bash
# 1) PostgreSQL 준비(예: 도커)
docker run -d --name pg -e POSTGRES_DB=appdb -e POSTGRES_USER=app -e POSTGRES_PASSWORD=app -p 5432:5432 postgres:16

# 2) 태스크 1회 실행 (Flyway 가 TASK_*/BATCH_*/도메인 생성 → settlementJob 실행 → 종료)
./gradlew bootRun

# 3) 결과 확인
psql 'postgresql://app:app@localhost:5432/appdb' -c 'SELECT * FROM settlement_result;'
psql 'postgresql://app:app@localhost:5432/appdb' -c 'SELECT task_name, exit_code, start_time, end_time FROM task_execution ORDER BY task_execution_id DESC LIMIT 5;'

# 종료코드 확인(성공 0, 실패 ≠0)
echo $?
```

샘플 데이터 기준 기대 결과: APPROVED 3건 정산(M001 2건·M002 1건), CANCELED 1건은 필터(write 제외), 음수 1건은 `skip`(WRITE/PROCESS_SKIP_COUNT 에 반영). `task_execution.exit_code=0`.

---

## 두 가지 실행 모델 — 같은 Job, 다른 기동 방식

같은 `settlementJob` 정의를 두 모델로 굴릴 수 있다. 무엇을 쓸지는 **스케줄을 누가 소유하느냐**로 갈린다.

### 모델 A — 단발 태스크 + 외부 오케스트레이션 (이 예제)
- 프로세스가 떠서 Job 1회 실행 후 **종료**. 스케줄은 **앱 바깥**(k8s CronJob / SCDF)이 소유.
- 모듈: **framework-task**. UI: k8s + 관측(`framework-observability`) + `TASK_EXECUTION` 조회. (SCDF 대시보드는 아래 "주의" 참조.)
- 컨테이너-네이티브. 패치/상주 부담 0. **k8s 환경의 1순위.**

### 모델 B — 상주 앱 + Quartz cron (in-process)
- 앱이 **계속 떠 있고** Quartz cron 트리거가 Job 을 반복 기동.
- 모듈: **framework-batch**(`framework.scheduler.jobs[*].{name,cron}` 선언). UI: **Quartz 스케줄러 관리 UI**(아래).
- 스케줄을 런타임에 보고/일시정지/즉시실행하고 싶을 때, VM/단일 상주 배포일 때.

> 둘은 배타가 아니다. 같은 jar 에 framework-task 와 framework-batch 를 함께 넣고, 배포 형태(CronJob vs 상주 Deployment)로 모델을 고를 수도 있다.

---

## Quartz 스케줄러 관리 UI — 무엇이 무엇에 붙나 (정확히)

**중요**: Quartz UI(QuartzDesk·Quartz Manager 등)는 **Quartz 스케줄러**를 관리한다 → **모델 B(framework-batch)** 에 붙는다.
**모델 A(Spring Cloud Task, 이 예제)** 는 Quartz 를 쓰지 않으므로 Quartz UI 의 대상이 아니다(태스크 이력은 `TASK_EXECUTION`/관측으로 본다).

| 도구 | 라이선스(2026-06 확인) | 형태 | 적합 |
|---|---|---|---|
| **Quartz Manager** (`fabioformosa/quartz-manager`) | **오픈소스** | Spring Boot 라이브러리(REST API) + 임베드 UI(webjar) **또는** 단독 웹앱. **Java 21 / Spring Boot 4.0.x 호환**. | 본 스택(Boot 4)의 **OSS 우선 후보**. |
| **QuartzDesk** | **상용**(무료 *Lite* 에디션은 기능 제한). | 자가호스팅 웹앱 + JVM 에이전트. 코드 변경 없이 모든 Quartz≥1.8.5 관리, 실행이력 30+ 파라미터. | 엔터프라이즈 기능/지원이 필요하고 **라이선스 예산이 있을 때**. 금융/공공은 라이선스·반입심사 확인 필수. |

> ⚠️ QuartzDesk 는 "오픈소스"가 아니다. 무료 Lite 가 있으나 기능 제한 + 지원 제한이며, 상용 에디션은 유료 라이선스다. 도입 전 라이선스/지원 조건을 반드시 검토할 것.

**전제 — UI 를 쓰려면 Quartz 를 JDBC JobStore 로**: framework-batch 기본은 RAM JobStore(재기동 시 트리거 소멸, 클러스터 공유 불가). UI 로 의미 있게 관리/모니터링하고 다중 파드에서 1회 실행을 보장하려면 영속·클러스터 JobStore 가 필요하다:
```yaml
spring:
  quartz:
    job-store-type: jdbc
    jdbc:
      initialize-schema: never      # QRTZ_* 도 Flyway 선반영(자동 DDL 끔)
    properties:
      org.quartz.jobStore.isClustered: "true"
      org.quartz.jobStore.driverDelegateClass: org.quartz.impl.jdbcjobstore.PostgreSQLDelegate
      org.quartz.scheduler.instanceId: AUTO
```
QRTZ_* PostgreSQL DDL 과 단계별 가이드는 루트 문서 **`docs/guide/BATCH_SCHEDULING_AND_UI.md`** 참조.

---

## SCDF(오케스트레이터) 주의 — 모델 A 의 UI

- **Spring Cloud Data Flow(SCDF) 서버 본체는 2025-04 오픈소스 유지보수 종료**(GitHub 아카이브). 마지막 OSS 라인 2.11.x(Boot 2/3), 이후 Tanzu 상용 전용.
- 반면 **Spring Cloud Task 5.0.x 는 살아 있고 Boot 4 네이티브**다(이 예제). 따라서 SCDF 죽음과 무관하게 **태스크 자체는 Boot 4 로 정상 운영**된다.
- 모델 A 의 "UI/모니터링"은 **SCDF 대시보드 대신** k8s + `framework-observability`(메트릭/로그) + `TASK_EXECUTION` 조회로 대체한다. SCDF UI/컴포즈드 태스크가 정책상 꼭 필요하면 Tanzu 상용 SCDF 에 이 태스크 이미지를 등록(`app register --type task ...`)한다.

상세 배경/결정은 `framework/framework-task/README.md` 와 `docs/_internal/HANDOFF_BATCH_SUMMARY.md`.
