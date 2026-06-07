# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**🟡 S5 관측 마감(2026-06-07).** S5 세 갈래 중 **관측 마감분 처리**, 나머지는 합의 재배치. **(A) 자작 JVM/Micrometer Grafana 대시보드**(`deploy/k8s/observability/grafana-dashboard-jvm.yaml`, uid `si-msa-jvm`, 패널 16+행 6) = 라벨 `grafana_dashboard:"1"` 단 ConfigMap → kube-prometheus-stack Grafana **sidecar 자동 적재**(수동 import·helm 변경 불필요. 차트 정본 values 확인: label `grafana_dashboard`/labelValue `"1"`/searchNamespace `ALL`/데이터소스 uid `prometheus`). **(B) Prometheus 4/4 타깃 정밀검증** — `05`(UP≥1 에서 멈춤) 보완해 `06-grafana-jvm-dashboard.sh` 가 `service` 라벨 단위 **gateway/auth-server/user-service/admin-service 4/4** 명시 요구. **재배치(합의)**: prod overlay **live 리허설 = S7 흡수**(외부 DB/issuer 미사용 → live apply 무의미; dry-run 렌더검증+SHA 태그 승격은 S7 Jenkins 자동화 대상), 실부하 HPA 스케일업 = **최종 수용시험 연기**(파이프라인은 S4-1 실측 완료). 정적검증만(bash `-n`·임베드 파이썬 타깃파싱·대시보드 JSON·ConfigMap 역파싱) — 실 적용/관측은 받는 쪽. **다음 = S6 상위흐름 실클러스터(OIDC RP·이중발급기) → S7 Jenkins.**

## 최종 갱신
- 일자: 2026-06-07 · 갱신자: S5(관측 마감) 세션
- 대상 브랜치: master · 환경: 코드/스택 무변경(devops 신규 = ConfigMap 1 + 스크립트 1 + 문서). 미커밋(이번 산출물).

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| S5-A Grafana JVM 대시보드 | `deploy/k8s/observability/grafana-dashboard-jvm.yaml` — sidecar 자동적재용 ConfigMap(라벨 `grafana_dashboard:"1"`). 자작 16패널(heap/nonheap/buffer/GC pause·freq/threads·states/CPU·uptime/HTTP rate·avg latency/HikariCP/logback), Service 템플릿변수(`service` 라벨). 차트 values 직접 확인으로 sidecar 동작 검증(추측 아님). 대시보드 JSON 유효·ConfigMap 역파싱 OK. |
| S5-B 4/4 타깃 검증 | `deploy/k8s/standalone-kind/06-grafana-jvm-dashboard.sh` — 대시보드 apply + port-forward 9090 → `/api/v1/targets` 에서 `si-msa-services` scrapePool·ns=si-msa·health=up 의 `service` 집합이 4서비스 전부 포함하는지 폴링(누락 시 어느 서비스인지 출력). `--grafana` 접속정보. bash `-n` OK·임베드 파이썬 파싱 오프라인 검증 OK. |
| 문서 정합 | standalone-kind README(06 행/실행순서)·`docs/ops/K8S_ADDONS.md`(Grafana 대시보드 자동적재 절)·PITFALLS §8 3건(sidecar 자동적재 / UP≥1≠4/4)·HANDOFF §7 S5 항목·NEXT_K8S_REAL_DEPLOY S5 행/§S5. |

## 현재 상태 (적용/검증)
- **클러스터**: standalone `kind-sanity` 3노드. dev overlay 6파드 + metrics-server + kube-prometheus-stack(monitoring, Grafana 포함). ServiceMonitor `si-msa-services` 활성.
- **그린 기준**: 6파드 ✅ · ② 토큰플로우 ✅ · metrics→HPA ✅ · Prometheus 스크랩 UP ✅ · **(작성환경 정적검증) JVM 대시보드/4-타깃 검증 스크립트** — 실 적용/4-UP 실측은 받는 쪽.
- **외부 노출**: 여전히 `kubectl port-forward`(ingress-nginx 미설치 클러스터). 호스트네임 접속은 ingress-nginx 설치 이후.

## 바로 다음 할 일 (Next)
1. **받는 쪽 실행**: `bash deploy/k8s/standalone-kind/06-grafana-jvm-dashboard.sh --grafana` → 4/4 UP 그린 + Grafana 'si-msa' 폴더에 JVM 대시보드 확인.
2. **이 세션 commit/push**(스크립트+ConfigMap+문서).
3. **S6 상위흐름 실클러스터** — OIDC RP·이중 발급기 standalone 스모크.
4. 이후 **S7 Jenkins**(build→양태그→Harbor push→`kustomize edit set image …:<sha>`→dry-run 렌더검증→apply). **prod overlay 리허설은 여기서 흡수.**
5. **(최종) 실부하 HPA 스케일업** 수용시험 — `04-metrics-hpa.sh --load` 또는 부하도구로 2→N 관찰.

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **Grafana 대시보드 = sidecar 라벨 ConfigMap 으로 자동 적재** — kube-prometheus-stack 기본값 label `grafana_dashboard`/labelValue `"1"`/searchNamespace `ALL`. 라벨만 달면 어느 ns 든 자동 import(수동 import·helm 변경 불필요). 데이터소스 uid=`prometheus`. 커뮤니티 대시보드 수동 import(휘발·라이선스 모호) 대신 선언적 ConfigMap.
- **관측 스모크 "UP≥1" ≠ "4서비스 전부 UP"** — 05 는 파이프라인 생존만(1개라도 UP 이면 종료). 한 서비스만 누락돼도 통과 → 06 이 `service` 라벨 4/4 명시 요구. SM 은 component=service 만 잡아 redis 제외=4 정원.
- **prod overlay live 리허설 ≠ 별도 S5 작업** — 외부 DB/issuer 미사용 토폴로지면 live apply 무의미. 실가치(dry-run 렌더검증+SHA 태그 승격)는 S7 자동화 그 자체 → S7 로 흡수. prod overlay 는 문서용 예시 유지.
- **실부하 HPA 스케일업 = 최종 수용시험** — metrics→HPA 파이프라인은 S4-1 에서 실측 완료. 실 2→N 거동은 전체 끝낸 뒤 한 번.
- **배치 트랙 ≠ 메인 트랙** — 배치 상세=`HANDOFF_BATCH_SUMMARY.md`. 독립 갱신.

<!-- 갱신 끝 -->
