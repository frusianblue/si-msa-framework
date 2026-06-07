# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**✅ S5 관측 마감 종료(2026-06-07).** **(A) 자작 JVM/Micrometer Grafana 대시보드**(`deploy/k8s/observability/grafana-dashboard-jvm.yaml`, uid `si-msa-jvm`, 패널 16+행 6) = 라벨 `grafana_dashboard:"1"` 단 ConfigMap → kube-prometheus-stack Grafana **sidecar 자동 적재**(수동 import·helm 변경 불필요; 차트 정본 values 확인: label/labelValue `"1"`/searchNamespace `ALL`/데이터소스 uid `prometheus`). **(B) Prometheus 4/4 타깃 정밀검증** = `06-grafana-jvm-dashboard.sh`(`service` 라벨 단위 4서비스 전부 UP 요구). **받는 쪽 실측 PASS**: `configmap/grafana-dashboard-jvm created` → `t=5s UP=4/4 [gateway auth-server user-service admin-service]`(첫 폴링 4/4, 폴백 불필요) → Grafana 'si-msa' 폴더 대시보드. **재배치(합의)**: prod overlay live 리허설=S7 흡수, 실부하 HPA 스케일업=최종 수용시험. **다음 = S7 Jenkins**(sha 핀 자동주입 + dry-run 게이트). **S6 상위흐름은 연기**(authcode+PKCE+id_token+jwks 는 `smoke-authcode-pkce.sh` 로 이미 실클러스터 실측, 잔여는 테스트 검증됨=저위험).

## 최종 갱신
- 일자: 2026-06-07 · 갱신자: S5(관측 마감) 종료 + S7 킥오프 정리 세션
- 대상 브랜치: master · 환경: 코드/스택 무변경(devops 신규 = ConfigMap 1 + 스크립트 1 + 문서). 미커밋(S5 산출 + 이 정리분).

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| S5-A Grafana JVM 대시보드 | `deploy/k8s/observability/grafana-dashboard-jvm.yaml` — sidecar 자동적재 ConfigMap. 자작 16패널(heap/nonheap/buffer/GC pause·freq/threads·states/CPU·uptime/HTTP rate·avg latency/HikariCP/logback), Service 템플릿변수. **받는 쪽 적용 OK**. |
| S5-B 4/4 타깃 검증 | `deploy/k8s/standalone-kind/06-grafana-jvm-dashboard.sh` — 대시보드 apply + `service` 라벨 4/4 폴링. **받는 쪽 `t=5s UP=4/4` PASS**(폴백 불필요). |
| 재부팅 보존 확인 | dev overlay postgres=StatefulSet+PVC(`components/postgres-persistent`, local-path) → DB·API오브젝트·대시보드 ConfigMap Docker Desktop 재시작 시 자동복귀. port-forward 만 재실행. ⚠️ local overlay 는 emptyDir(휘발) 정반대. PITFALLS §8 등록. |
| 문서 정합 | standalone-kind README·`K8S_ADDONS.md`·PITFALLS §8(자동적재/UP≥1≠4/4/재부팅 보존)·HANDOFF §7(실측 PASS)·NEXT_K8S(S5 ✅·S7 킥오프 체크리스트·상단 포인터). |

## 현재 상태 (적용/검증)
- **클러스터**: standalone `kind-sanity` 3노드. dev overlay 6파드 + metrics-server + kube-prometheus-stack(Grafana 포함) + ServiceMonitor `si-msa-services`. **JVM 대시보드 적재 + 4/4 타깃 UP 실측 ✅**.
- **그린 누적**: 6파드 ✅ · 토큰플로우 ✅ · metrics→HPA ✅ · 스크랩 UP ✅ · **JVM 대시보드 + 4/4 ✅** = S4·S5 종료.
- **외부 노출**: `kubectl port-forward`(리부트 시 재실행). 호스트네임 접속은 ingress-nginx 설치 이후.

## 바로 다음 할 일 (Next)
1. **이 세션 commit/push**(S5 ConfigMap+스크립트+문서 — 그린 박제).
2. **S7 Jenkins 착수** — `NEXT_K8S_REAL_DEPLOY.md` §S7 킥오프 체크리스트. 기존 `deploy/cicd/{Jenkinsfile,harbor-push.sh,ci-cd.yml}`+`redeploy.sh` 검토 → **sha 핀 자동주입**(`kustomize edit set image …:<git-sha>`)+**dry-run 게이트**(prod overlay 렌더검증 흡수)+`apply -k overlays/dev`. 작성환경=정적검증, 실 파이프라인=받는 쪽.
3. (연기) **S6 상위흐름 실클러스터** — confidential RP 풀콜백·게이트웨이 이중 issuer 분기(테스트 검증됨, S7 후 선택).
4. (최종) **실부하 HPA 스케일업** 수용시험.

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **Grafana 대시보드 = sidecar 라벨 ConfigMap 자동 적재** — label `grafana_dashboard`/labelValue `"1"`/searchNamespace `ALL`/데이터소스 uid `prometheus`. 라벨만 달면 자동 import(수동·helm 불필요). 커뮤니티 대시보드 수동 import(휘발·라이선스 모호) 대신 선언적 ConfigMap.
- **관측 스모크 "UP≥1" ≠ "4/4"** — 05 는 생존만, 06 이 `service` 라벨 4/4 명시 요구. SM 은 component=service 만 → redis 제외=4 정원.
- **재부팅 보존 = PVC(DB)·ConfigMap(대시보드)·etcd(API오브젝트) 자동복귀, port-forward 만 재실행** — dev overlay=StatefulSet+PVC(영속), local overlay=emptyDir(휘발) 구분. kind 호스트 리부트 공식 미보장 → wedge 시 teardown(⚠️PVC 삭제) 없이 재구축이 안전.
- **prod overlay live 리허설=S7 흡수 / 실부하 HPA=최종 수용시험 / S6=연기(저위험)** — 메인 트랙 다음은 S7.
- **배치 트랙 ≠ 메인 트랙** — 배치 상세=`HANDOFF_BATCH_SUMMARY.md`. 독립 갱신.

<!-- 갱신 끝 -->
