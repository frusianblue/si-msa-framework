# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**✅ 배치 트랙 일단락(2026-06-07).** `framework-task`(Spring Cloud Task 5.0.1, Boot4 네이티브) + Batch6 결합을 **실투입 가능한 예제 3종**으로 완성: `examples/batch-task-reference`(단일 정산 잡 엔드투엔드) · `examples/batch-cookbook`(실무 패턴 5종: 파일↔DB·다단계·Tasklet·내결함) · `examples/batch-types`(처리 방식·DB 리더/라이터 종류 6종: 멀티스레드·파티셔닝·플로우·처리기체인·MyBatis·StoredProcedure). 사용자 결정: **배치는 여기서 일단락, 나중에 소스 보고 필요한 것만 추가**. 모든 배치 API 는 v6.0.3 raw + mybatis-spring 4.0.0 raw 로 실측(날조 0). **배치 트랙 상세는 `HANDOFF_BATCH_SUMMARY.md` 가 권위 있는 한 장** — 이 문서는 포인터만.

## 최종 갱신
- 일자: 2026-06-07 · 갱신자: 배치 트랙(task + cookbook + types) 일단락 세션
- 대상 브랜치: master · 환경: framework-task 신모듈 + 예제 3종 + 문서. (메인 인증/클러스터 트랙 무변경.)

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| framework-task 모듈 | SCT 5.0.1(Boot4) `@EnableFrameworkTask` + `FrameworkTaskExecutionListener`. 빌드 와이어링 5곳(settings·archtest·jacoco·SCT BOM·version catalog). |
| 예제 3종 | reference(정산 청크) · cookbook(패턴 5) · types(유형 6). 전부 독립 빌드, mavenLocal `com.company:framework-task:1.0.0` 소비. `--spring.batch.job.name` 선택 실행, Flyway 샘플로 독립 실행. |
| API 실측 | Batch6 `infrastructure.item.*` 재배치 · FlatFile/Tasklet/RunIdIncrementer · 멀티스레드(페이징 필수)·파티셔닝(`Partitioner`/`@StepScope`)·플로우(`on/to`/`split`)·CompositeItemProcessor·StoredProcedure(PG refcursor)·**mybatis-spring 4.0.0**(Batch6 신패키지; starter 3.0.5 비호환) 전부 raw 소스 검증. |
| 정적 검증 | 예제 전체: 금지 import 0 · 구 batch 패키지 0 · 필드주입 0 · 잡 이름(코드↔yml↔k8s) 일치 · 패키지=경로 · 괄호 균형 · 핵심 import 경로 cookbook 동일. |
| 문서 | PITFALLS §3·§6 · `HANDOFF_BATCH_SUMMARY` · `BATCH_SCHEDULING_AND_UI` · `FRAMEWORK_MODULES` · `MODULE_COMPOSITION` · `STACK` · HANDOFF §7 · 이 SUMMARY 갱신. |

## 현재 상태 (적용/검증)
- **배치 트랙**: framework-task + 예제 3종 = 정적 검증 완료(작성 환경). 일단락.
- **메인 트랙**(직전 세션): standalone `kind-sanity` 6파드 Running · authorization_code+PKCE+DbAuthenticator 실클러스터 e2e ✅(② 종료) — 변동 없음.
- **미커밋**: 이번 배치 산출물(framework-task + 예제 3종 + 문서 갱신)은 로컬 working tree.

## 바로 다음 할 일 (Next)
1. **이번 배치 산출물 commit/push** — framework-task 모듈 + 빌드 와이어링 + 예제 3종 + 문서.
2. **받는 쪽(Chae) 검증** — `:framework:framework-task:test` · `:framework-archtest:test` · `spotlessApply` · 예제 독립 빌드(`publishToMavenLocal` 선행) · (선택) 컨테이너→k8s CronJob 1회 기동으로 종료코드/`TASK_EXECUTION` 확인. 특히 `mybatisJob` 은 mybatis-spring 4.0.0 이 starter 의 3.0.5 를 실제로 대체하는지(또는 강제 필요) 의존성 해석 확인.
3. **배치 추가는 소스 검토 후 필요 시에만** — 사용자 결정대로 일단락. (남는 후보: 원격 파티셔닝/청크는 메시징 인프라 필요라 보류.)
4. **메인 트랙 재개** 시 직전 세션 Next(S4 metrics-server/HPA → kube-prometheus-stack → S5 prod-rehearsal …)로 복귀 — `HANDOFF.md §7` 참조.

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **mybatis-spring 은 Batch 6 에서 4.0.0+ 필수** — 배치 리더/라이터가 신패키지 `infrastructure.item.*` 를 import. 3.0.x 는 Batch 5 전용. starter 가 3.0.5 를 전이하므로 `org.mybatis:mybatis-spring:4.0.0` 직접 선언. (PITFALLS §6)
- **멀티스레드 청크는 커서 리더 금지 → 페이징 리더 + `saveState(false)` + `sortKeys`.** (PITFALLS §3)
- **`beanMapped()`/MyBatis 파라미터는 게터 필요(record 불가)** — record 를 그대로 쓰려면 `itemPreparedStatementSetter`(위치형 `?`). 출력 타입은 게터 클래스로.
- **API 날조 금지** — 배치/MyBatis FQCN·빌더 시그니처는 전부 해당 버전 raw 소스(`v6.0.3`·`mybatis-spring-4.0.0`)로 확인 후 작성. 컴파일 미검증 환경의 핵심 안전장치.

<!-- 갱신 끝 -->
