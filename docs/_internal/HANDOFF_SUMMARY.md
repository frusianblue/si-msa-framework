# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**✅ S4 관측/오토스케일 애드온 종료(2026-06-07).** standalone `kind-sanity` 에서 **S4-1**(metrics-server, kind `--kubelet-insecure-tls`) → `kubectl top` OK + 일회용 HPA 가 CPU 메트릭 수신(TARGETS ≠ `<unknown>`) = **metrics→HPA 파이프라인 실측**, **S4-2**(kube-prometheus-stack Helm 설치 + dev overlay ServiceMonitor `$patch:delete` 해제) → Prometheus 가 `si-msa-services` 타깃 **스크랩 UP 실측**. 신설 `deploy/k8s/standalone-kind/04-metrics-hpa.sh`·`05-prometheus-stack.sh`. **S4 기능 파일은 전부 origin 반영**(이 세션 산출=ledger 문서 정리). **배치 트랙은 일단락**(상세=`HANDOFF_BATCH_SUMMARY.md`). **다음 섹션 = S5 prod-rehearsal**(실부하 HPA 스케일 관찰 + 4타깃 전부 UP 정밀 확인).

## 최종 갱신
- 일자: 2026-06-07 · 갱신자: S4(관측/HPA) 마감 + 문서 정리 세션
- 대상 브랜치: master · 환경: 코드/스택 무변경(devops 스크립트 2 + 문서). 기능 파일 origin 반영, 본 세션 산출=문서.

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| S4-1 metrics/HPA | `04-metrics-hpa.sh` — metrics-server 설치 + kind `--kubelet-insecure-tls` 멱등 보정 → `kubectl top nodes/pods` 응답 → 일회용 HPA TARGETS=cpu n%/70%(≠unknown) **실측 통과**(idle 0% 정상). |
| S4-2 prometheus-stack | `05-prometheus-stack.sh` — Helm `kube-prometheus-stack`(release명=SM `release` 라벨 + `serviceMonitorSelectorNilUsesHelmValues=false`) 설치 → CRD 확립 → dev overlay 재적용으로 `si-msa-services` SM 적용 → Prometheus 타깃 **UP 실측**. dev overlay SM `$patch:delete` 제거. |
| 코드-문서 정합 | (S4-1 동봉) auth-server 2파일 javadoc `§9`→`§5` 교정 — origin 반영. |
| 문서 정리(이 세션) | HANDOFF §7 S4 종료 불릿 + 이 SUMMARY + standalone-kind README(04·05 행/명령) + PITFALLS §8 2건(kind metrics insecure-tls / SM release-label 매칭). |

## 현재 상태 (적용/검증)
- **클러스터**: standalone `kind-sanity` 3노드. dev overlay 6파드 Running + metrics-server + kube-prometheus-stack(monitoring ns). ServiceMonitor `si-msa-services` 활성.
- **그린 기준**: 6파드 ✅ · ② 토큰플로우 ✅ · **metrics→HPA ✅** · **Prometheus 스크랩 UP ✅** = S4 종료.
- **관측 잔여**: S4-2 스모크는 첫 타깃 UP 에서 폴링 종료(UP=1) — 4서비스 전부 UP 정밀 확인은 S5.

## 바로 다음 할 일 (Next)
1. **이 세션 문서 commit/push**(기능 파일은 이미 origin).
2. **S5 prod-rehearsal** — overlays/prod 경로 리허설 + 실부하 HPA 스케일업 관찰(`04-metrics-hpa.sh --load` 또는 부하도구) + Prometheus 4타깃 전부 UP + (선택) Grafana JVM 대시보드.
3. 이후 **S6 상위흐름 실클러스터(OIDC RP·이중발급기) → S7 Jenkins(sha 핀 자동 — `redeploy.sh` 다이제스트 태그 승격)**.

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **kind metrics-server = `--kubelet-insecure-tls` 필수** — kind 노드 kubelet 은 self-signed 서빙 인증서라 기본 metrics-server 는 `kubectl top` 무응답. 매니페스트 apply 후 컨테이너 args 에 멱등 추가.
- **ServiceMonitor 는 release 라벨 매칭(또는 selector 전체수용) 없이는 조용히 스크랩 안 됨** — Prometheus 기본 selector=`release=<helm-release>`. base SM `release` 라벨을 설치 release 명과 맞추거나 `serviceMonitorSelectorNilUsesHelmValues=false`. 안 맞으면 SM 적용돼도 타깃 0(무에러 누락).
- **dev overlay SM `$patch:delete` 는 Operator 설치 후에만 해제** — CRD 없는 클러스터에 SM apply 시 `no matches for kind "ServiceMonitor"` 로 `apply -k` 비클린. 05 스크립트와 순서 결합.
- **배치 트랙 ≠ 메인 트랙** — 메인 SUMMARY=메인(auth/k8s/devops), 배치 상세=`HANDOFF_BATCH_SUMMARY.md`. 독립 갱신.

<!-- 갱신 끝 -->
