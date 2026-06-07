# 배치 스케줄링 & 관리 UI 가이드 (Spring Cloud Task vs Quartz)

> 배치를 "언제·어떻게 기동하고, 무엇으로 보느냐"를 정리한다. 핵심 질문: **스케줄을 누가 소유하나?**
> 그 답이 모듈(framework-task vs framework-batch)과 UI(관측 vs Quartz UI)를 가른다.

---

## 1. 두 실행 모델

| | **모델 A — 단발 태스크** | **모델 B — 상주 + Quartz cron** |
|---|---|---|
| 엔진 | Spring Batch 6 + **Spring Cloud Task 5.0.x** | Spring Batch 6 + **Quartz**(in-process) |
| 모듈 | **framework-task** | **framework-batch** |
| 프로세스 | 떠서 Job 1회 실행 → **종료** | **계속 떠 있고** cron 트리거가 반복 기동 |
| 스케줄 소유 | **앱 바깥**(k8s CronJob / SCDF) | **앱 안**(Quartz) |
| 실행 이력 | `TASK_EXECUTION` / `BATCH_*` | `BATCH_*` (+ Quartz `QRTZ_*` 트리거 상태) |
| 관리/모니터 UI | k8s + 관측(`framework-observability`) + 이력 조회 / (SCDF 대시보드는 EOL — 아래) | **Quartz UI**(QuartzDesk / Quartz Manager) |
| 적합 | **k8s 환경 1순위.** 컨테이너-네이티브, 상주·패치 부담 0 | 런타임에 스케줄 보고/일시정지/즉시실행, VM·단일 상주 배포 |

> 배타가 아니다. 같은 `Job` 정의를 두 모델로 모두 굴릴 수 있다 — 배포 형태(CronJob vs 상주 Deployment)로 고른다.
> 실투입 예제: `examples/batch-task-reference`(모델 A, 단일 정산 잡 엔드투엔드) · `examples/batch-cookbook`(모델 A, 실무 패턴 5종: 파일→DB·DB→파일·다단계·Tasklet·내결함) · `examples/batch-types`(처리 방식·DB 리더/라이터 종류 6종: 멀티스레드·파티셔닝·플로우·처리기체인·MyBatis·StoredProcedure). framework-batch README(모델 B).

---

## 2. Quartz 관리 UI — 무엇이 무엇에 붙나 (정확히)

**Quartz UI 는 Quartz 스케줄러를 관리한다 → 모델 B(framework-batch)에만 붙는다.**
모델 A(Spring Cloud Task)는 Quartz 를 쓰지 않으므로 Quartz UI 의 대상이 아니다(태스크 이력은 `TASK_EXECUTION`·관측으로 본다).

| 도구 | 라이선스 (2026-06 확인) | 형태 | 비고 |
|---|---|---|---|
| **Quartz Manager** `fabioformosa/quartz-manager` | **오픈소스** | Spring Boot 라이브러리(REST API) + 임베드 UI(webjar) **또는** 단독 웹앱 | **Java 21+ / Spring Boot 3.5.x·4.0.x 호환**(업스트림 명시) → 본 스택(Boot 4.0.6)의 **OSS 우선 후보**. 좌표·연동은 §2-1. |
| **QuartzDesk** | **상용** (무료 *Lite* 는 기능 제한·지원 제한) | 자가호스팅 웹앱 + JVM 에이전트(코드 변경 없이 부착) | 모든 Quartz ≥ 1.8.5 관리, 실행이력 30+ 파라미터, 로그 인터셉트. 엔터프라이즈 기능/지원·예산이 있을 때. |

> ⚠️ **QuartzDesk 는 오픈소스가 아니다.** 무료 Lite 에디션이 있으나 기능·지원이 제한되고, Standard/Enterprise 는 유료 라이선스다(스타트업/학술/비영리/OSS 할인 프로그램 별도). 금융·공공 반입 시 라이선스·지원 조건과 보안 심사를 반드시 확인할 것.

다른 후보(.NET 전용이라 본 스택에 부적합): Quartzmin·CrystalQuartz·quartznet-admin 은 모두 **Quartz.NET** 용이다. Java Quartz 에는 쓸 수 없다.

### 2-1. Quartz Manager 좌표·연동 (업스트림 README 실측, 2026-06)

groupId 는 **`it.fabioformosa.quartz-manager`**. 모듈 구성(스타터):

| 아티팩트 | 필수/선택 | 역할 |
|---|---|---|
| `quartz-manager-starter-api` | **필수** | REST API(`/quartz-manager/**`)·스케줄러/잡/트리거/캘린더 관리·OpenAPI·WebSocket 갱신 |
| `quartz-manager-starter-ui` | 선택 | 임베드 관리 UI(webjar) — `/quartz-manager-ui/index.html` |
| `quartz-manager-starter-security` | 선택 | Quartz Manager 자체 JWT 인증(또는 호스트 앱 Spring Security 에 위임) |
| `quartz-manager-starter-persistence` | 선택 | PostgreSQL 영속 + Liquibase 스키마 |
| `quartz-manager-web-showcase` | 선택 | 단독 데모/콘솔 앱 |

```xml
<dependency>
  <groupId>it.fabioformosa.quartz-manager</groupId>
  <artifactId>quartz-manager-starter-api</artifactId>
  <version>VERSION</version> <!-- 업스트림 GitHub Releases / Maven Central 확인 후 고정(master 기준 프로젝트 버전은 5.0.1 계열) -->
</dependency>
```

**framework-batch 와 붙이는 정확한 경로 — "기존 스케줄러" 모드**: framework-batch 는 `spring-boot-starter-quartz` 로 이미 `Scheduler` 빈을 만든다. Quartz Manager 가 **그 스케줄러**를 관리하게 하려면 자체 스케줄러 생성을 끄고 빈을 이름으로 넘긴다.

```properties
quartz-manager.quartz.enabled=false
```
```java
@Bean("quartzManagerScheduler")     // Quartz Manager 가 이 이름으로 주입
public Scheduler quartzManagerScheduler(Scheduler frameworkBatchScheduler) {
  return frameworkBatchScheduler;   // framework-batch 가 구성한 스케줄러(JobStore/JobFactory/클러스터 포함)
}
```

⚠️ **정직한 한계(업스트림 명시)**: 위 "기존 스케줄러" 연동이 **현재 유일한 통합 지점**이고, 임의의 기존 스케줄러를 자동 발견·관리하는 *first-class* 기능은 **로드맵**이다. 또 Quartz Manager 의 API 로 **새 잡을 생성**하려면 잡이 그쪽 모델(`AbstractQuartzManagerJob`)을 따라야 한다 — 반면 framework-batch 의 잡은 `BatchJobQuartzJob`(`QuartzJobBean`→`JobLaunchSupport`)로 yaml 선언 등록된다. 따라서 **현실적 기대치**: Quartz Manager 로 스케줄러 **상태 제어(start/standby/pause/resume)와 트리거·잡 가시화/일시정지**는 공유 JobStore(아래 §3) 위에서 동작하지만, "framework-batch 가 등록한 잡을 Quartz Manager UI 에서 처음부터 새로 만든다"는 시나리오는 모델 차이로 매끄럽지 않다. 운영 모니터링·수동 트리거 용도면 충분, 잡 정의의 단일 소스는 framework-batch(yaml) 로 둔다. (연동 PoC 전 `quartz-manager-compatibility-cases` 레포의 Boot 4.0.x 케이스 확인 권장.)

---

## 3. 전제 — UI 를 쓰려면 Quartz 를 JDBC JobStore + 클러스터로

framework-batch 기본은 **RAM JobStore**다 — 재기동 시 트리거가 사라지고, 다중 파드 간 공유/배타가 안 된다. UI 로 의미 있게 관리·모니터링하고 멀티 인스턴스에서 1회 실행을 보장하려면 **영속(JDBC) + 클러스터** JobStore 가 필요하다.

```yaml
spring:
  quartz:
    job-store-type: jdbc
    jdbc:
      initialize-schema: never          # QRTZ_* 도 Flyway 선반영(자동 DDL 끔)
    properties:
      org.quartz.scheduler.instanceId: AUTO
      org.quartz.jobStore.isClustered: "true"
      org.quartz.jobStore.driverDelegateClass: org.quartz.impl.jdbcjobstore.PostgreSQLDelegate
      org.quartz.jobStore.tablePrefix: QRTZ_
```

**QRTZ_* DDL(PostgreSQL, Flyway 선반영용)**: `deploy/db/quartz/V1__quartz_tables_postgres.sql` (Quartz 2.5.x 공식 스키마, DROP 제거·트랜잭션 안전). 이 파일을 framework-batch 앱의 `db/migration` 에 넣고 위 설정과 함께 쓴다.

> framework-lock 와의 차이: framework-lock 은 **평범한 `@Scheduled`**(Quartz 아님)의 다중 파드 중복 실행을 막는다. Quartz cron(framework-batch)의 클러스터 배타는 위의 `isClustered=true` 가 담당한다. 둘은 다른 계층이다.

---

## 4. SCDF — 모델 A 의 UI 였으나 OSS 종료

- **Spring Cloud Data Flow(SCDF) 서버 본체는 2025-04 오픈소스 유지보수 종료**(GitHub 아카이브, `spring-attic`). 마지막 OSS 라인 2.11.x(Boot 2/3), 이후 Tanzu 상용 전용.
- 그러나 **Spring Cloud Task 5.0.x 는 살아 있고 Boot 4 네이티브** — SCDF 죽음과 무관하게 태스크 자체는 정상 운영된다.
- 모델 A 의 "UI/모니터링"은 **SCDF 대시보드 대신** k8s + `framework-observability`(메트릭/로그) + `TASK_EXECUTION` 조회로 대체한다. SCDF UI/컴포즈드 태스크가 정책상 꼭 필요하면 Tanzu 상용 SCDF 에 태스크 이미지를 등록한다(`app register --type task ...`). OSS 2.11.x(EOL) 서버는 보안 패치가 없어 금융·공공 부적합.

---

## 5. 빠른 결정 트리

1. **컨테이너(k8s)에서 정해진 시각/이벤트에 1회 돈다** → 모델 A(framework-task) + k8s CronJob. UI 는 관측 + `TASK_EXECUTION`. → `examples/batch-task-reference`.
2. **상주 앱에서 cron 으로 반복, 런타임에 스케줄을 보고/제어하고 싶다** → 모델 B(framework-batch) + Quartz JDBC/클러스터 + **Quartz Manager(OSS)** 또는 **QuartzDesk(상용)**.
3. **SCDF DSL/컴포즈드 태스크/대시보드가 조직 표준** → Tanzu 상용 SCDF + 모델 A 태스크 등록(크로스-부트 검증 필요).

관련 문서: `framework/framework-task/README.md`, `framework/framework-batch/README.md`, `docs/guide/MODULE_COMPOSITION.md`, `docs/_internal/HANDOFF_BATCH_SUMMARY.md`.
