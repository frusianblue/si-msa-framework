# HANDOFF_BATCH_SUMMARY.md — 배치·태스크·스케줄 트랙 한 장

> 메인 트랙 인계는 같은 폴더 `HANDOFF_SUMMARY.md`, 누적 정본은 `HANDOFF.md`.
> 이 문서는 **"Spring Cloud Task + Spring Batch 조합 / 스케줄링 / Quartz UI"** 트랙만 떼어 본 별도 한 장이다.
> 모듈 카탈로그=`../FRAMEWORK_MODULES.md` · 스택=`../reference/STACK.md` · 설계 가이드=`../guide/BATCH_SCHEDULING_AND_UI.md` · 함정=`../guide/PITFALLS.md §3·§10`.

---

## 한 줄 요약 (2026-06-06)
**`framework-task`(Spring Cloud Task 5.0.1, Boot4 네이티브) 신설 + `framework-batch`(Batch6) 결합을 "실제 프로젝트에 쓸 수 있는" 레퍼런스 앱(`examples/batch-task-reference/`)으로 완성.** 사용자가 제안한 모니터링 UI(QuartzDesk·Quartz Manager)를 정직하게 매핑: UI 는 **Quartz 스케줄러(=framework-batch 모델)** 전용이고, **QuartzDesk 는 상용**(OSS 아님), 본 스택 OSS 후보는 `fabioformosa/quartz-manager`. **새 외부 의존성**: SCT BOM 1개(`spring-cloud-task-dependencies:5.0.1`, 메인 Oakwood BOM 밖).

## 핵심 결정 (Decisions)
1. **SCDF 서버는 쓰지 않는다** — Spring Cloud Data Flow **서버 본체는 OSS EOL**(2025-04 Broadcom 중단, `spring-attic` 아카이브; 마지막 OSS 2.11.x=Boot2/3, 이후 Tanzu 상용). 그러나 **Spring Cloud Task 라이브러리(5.0.1)는 Boot4 네이티브로 생존** → 실행이력 영속 목적에 SCDF 없이 단독 사용.
2. **두 실행 모델을 분리해서 제공** —
   - **모델 A `framework-task`(+batch)**: run-once. **외부 스케줄러(k8s CronJob 등)** 가 깨우고, 시작·종료·**종료코드**·파라미터를 `TASK_EXECUTION` 에 영속. Batch6 결합 시 SCT `TaskBatchAutoConfiguration` 이 Job↔Task 자동 연결(`TASK_TASK_BATCH`).
   - **모델 B `framework-batch`(Quartz)**: 상주 프로세스 안 cron 반복. yaml 선언만으로 Job 기동. **Quartz UI 연동은 이 모델에만** 귀속.
3. **Quartz UI 정확 매핑(사용자 제안 정정)** — UI 는 Quartz 스케줄러 테이블을 보는 도구라 **JDBC JobStore + 클러스터** 전제(`spring.quartz.job-store-type=jdbc`, `isClustered=true`, PostgreSQLDelegate, QRTZ_* 11테이블). **QuartzDesk=상용**(무료 Lite 만 제한), **`fabioformosa/quartz-manager`=OSS(Apache, Boot3.5/4.0 호환)=본 스택 후보**, Quartzmin/CrystalQuartz/quartznet-admin=**Quartz.NET 전용→부적합**.

## 완료 산출물 (Done)
| 산출물 | 경로 | 비고 |
|---|---|---|
| `framework-task` 모듈 | `framework/framework-task/` | `@EnableFrameworkTask`(=`@EnableTask` 메타합성)·`FrameworkTaskExecutionListener`(표준 감사로깅)·`FrameworkTaskProperties`(`framework.task.enabled` 기본 off)·AutoConfiguration(`afterName=SimpleTaskAutoConfiguration`+`@ConditionalOnClass(TaskExecutionListener)`+빈 `@ConditionalOnBean(TaskRepository)`)·`.imports`·guard test·README(SCDF EOL 표+켜는/쓰는/끄는법+실전 사용 예) |
| 빌드 와이어링 5곳 | `settings.gradle`·`framework-archtest/build.gradle`·루트 `build.gradle`(jacocoAggregation+`spring-cloud-task-dependencies` BOM 2곳)·`gradle/libs.versions.toml`(`springCloudTask="5.0.1"`) | 모듈 등록 5요건 충족 |
| 레퍼런스 앱 | `examples/batch-task-reference/` | **독립 빌드**(루트 settings.gradle 밖, mavenLocal 의 `com.company:framework-task:1.0.0` 소비). 정산 청크잡(JdbcCursor리더→수수료1.5%처리→JdbcBatch라이터 `beanMapped`)·Flyway 3개(SCT/Batch6/도메인 DDL 실소스)·`application.yml`(자동 DDL off·`fail-on-job-failure`·`single-instance`)·Dockerfile(종료코드 전파)·k8s CronJob(`Forbid`/`Never`/`backoffLimit:2`)·README |
| **패턴 모음 앱** | `examples/batch-cookbook/` | **실무 배치 패턴 5종**(2026-06-07): `fileIngestJob`(파일CSV→DB, FlatFileItemReader→JdbcBatchItemWriter)·`reportExportJob`(DB→파일CSV, JdbcCursorItemReader→FlatFileItemWriter)·`dormantAccountJob`(다단계 Tasklet→Chunk 휴면전환)·`purgeJob`(단일 Tasklet 정리 DELETE)·`interestAccrualJob`(내결함 skip+retry+RunIdIncrementer). `--spring.batch.job.name` 으로 선택 실행, V3 샘플데이터로 각 잡 독립 실행 가능. FlatFile/Tasklet/Incrementer API 전부 v6.0.3 raw 실측 |
| Quartz DDL 배포본 | `deploy/db/quartz/V1__quartz_tables_postgres.sql` | Quartz 2.5.x 11 QRTZ_* (DROP/COMMIT 제거=Flyway 트랜잭션 안전) |
| 스케줄/UI 가이드 | `docs/guide/BATCH_SCHEDULING_AND_UI.md` | 두 모델 표·Quartz UI 정확매핑·JDBC JobStore 전제·SCDF EOL·결정트리 |
| 문서 동시갱신 | `FRAMEWORK_MODULES.md`(§0·§2.4·§4)·`framework/README.md`·`MODULE_COMPOSITION.md`(§4-1·§4-2·트리·함정)·`PITFALLS.md`(§1·§3·신규 §10)·`STACK.md`(§3.1·§5) | 코드변경=문서동시갱신 철칙 |

## 검증 상태
- **정적 검증 완료**(작성 환경): 예제 Java 5파일 괄호 균형 OK · `com.fasterxml` import 0 · `@Autowired` 필드주입 0 · Batch6 import 전부 `infrastructure.item.*`(구 `batch.item.*` 0, 매치된 1건은 패키지 재배치를 설명하는 Javadoc 주석).
- **API 근거**: SCT 5.0.1 = sparse checkout 실소스(`@EnableTask`/`SimpleTaskAutoConfiguration`/`TaskExecutionListener`/`TaskBatchAutoConfiguration`/`TaskRepository`/프로퍼티/DDL). Batch 6.0.3 = raw 소스(패키지·빌더 시그니처·PostgreSQL DDL). Quartz 2.5.2 = `tables_postgres.sql` 실소스.
- **받는 쪽(Chae) 실행 필요**: `./gradlew :framework:framework-task:test` · `:framework-archtest:test` · `./gradlew spotlessApply` · 예제 독립빌드(`cd examples/batch-task-reference && ./gradlew bootJar`, 단 mavenLocal 에 `framework-task:1.0.0` publish 선행) · (선택) 예제 컨테이너→로컬 k8s CronJob 1회 기동으로 종료코드/`TASK_EXECUTION` 적재 확인.

## 함정 요약 (상세 PITFALLS §3·§10)
- **SCDF 서버 OSS EOL** — 서버 깔지 말 것, Task 라이브러리만.
- **`@EnableTask` 없으면 실행이력 시작 안 됨** — `framework.task.enabled=true` 만으론 부족.
- ★ **Batch6 `infrastructure.item.*` 패키지 재배치** — 5.x `batch.item.*` 아님. core 는 `batch.core.*` 그대로.
- **`beanMapped()` 는 record 불가**(JavaBean 게터 필요) — 라이터 입력은 게터 클래스.
- **종료코드 전파** — `fail-on-job-failure=true` + `System.exit(SpringApplication.exit(ctx))` + Dockerfile `exec` 라야 CronJob 성공/실패 판정.
- **Quartz UI = 모델 B·JDBC JobStore 전용** — RAM JobStore 면 외부 도구가 볼 테이블 없음.
- **QuartzDesk 는 상용** — OSS 후보는 `fabioformosa/quartz-manager`.
- **SCT 는 Oakwood BOM 밖** — `spring-cloud-task-dependencies:5.0.1` 별도 BOM, starter 는 stream 전이라 미사용.

## 바로 다음 할 일 (Next)
1. **커밋/푸시** — 이번 트랙 산출물(framework-task 모듈 + 빌드 와이어링 + 예제 + 가이드 + 문서 갱신)은 로컬 working tree(미커밋).
2. **받는 쪽 검증 패스** — 위 "검증 상태"의 gradle 명령 + 예제 독립빌드(필요 시 `framework-task` 를 `publishToMavenLocal`).
3. **(선택) `quartz-manager` 연동 PoC** — 좌표 확정됨(`it.fabioformosa.quartz-manager:quartz-manager-starter-api` (+`-ui`/`-security`/`-persistence`), Java21+/Boot 3.5·4.0 호환, master 기준 5.0.1 계열 — 릴리스 버전은 GitHub Releases/Central 확인). 연동 경로 = `quartz-manager.quartz.enabled=false` + framework-batch 스케줄러를 `@Bean("quartzManagerScheduler")` 로 노출(§2-1). ⚠️ 한계(업스트림 명시): 기존 스케줄러 연동이 현재 유일 통합점(자동발견은 로드맵), 신규 잡 생성은 `AbstractQuartzManagerJob` 모델 요구 → framework-batch yaml 잡과 모델 차이. 현실적 용도=스케줄러 상태제어·트리거 가시화/수동트리거(잡 정의 단일소스는 framework-batch). PoC 시 `quartz-manager-compatibility-cases` Boot4 케이스 확인.
4. **(선택) `deploy/k8s/task/` 독립 매니페스트** — 현재 CronJob 은 예제 인라인(`examples/batch-task-reference/k8s/cronjob.yaml`). 공통 배포 디렉터리에 파라미터화 버전 분리.

## 메인 트랙과의 교차참조
- `HANDOFF_SUMMARY.md` 의 다음 후보 중 **"그릇 정비(k8s 멀티서비스/CI-CD)"** 와 이 트랙의 CronJob/스케줄 배포가 맞닿음. 인증/SSO 메인 흐름과는 독립.
