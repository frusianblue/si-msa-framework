# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**✅ CI/CD 1차 완주 + 관측(Grafana/Prometheus) ingress 노출(2026-06-07 세션5–8, devops).** (5)태그 B 불변sha 주입 (6)Kaniko 서비스별 컨테이너 다중빌드 → **실잡 4서비스 build→push 성공** (7)deploy 2종 결함(ns ClusterRole + SM `$patch:delete`/05 직접 apply) 수정 → **6파드 `1/1 Running`, 4서비스 전부 `harbor.local/si-msa/*:cd161c73c135` 단일 sha 핀 실측(가변 :dev 자취 없음)**. (8)**관측 호스트 접속 = B안 ingress**: `monitoring-values.yaml`(grafana/prometheus.ingress + grafana.ini domain/root_url + ssl-redirect=false) 신규, `05` 가 `-f` 로 머지, `05`/`06` 접속안내를 ingress(`grafana.local`/`prometheus.local`) 우선으로. **노드 이름해소 불요**(Harbor 와 달리 pull 대상 아님) — 호스트 hosts `127.0.0.1 grafana.local prometheus.local` 한 줄. 오프라인 검증: bash -n·values YAML 키·helm -f 연결 PASS.

## 최종 갱신
- 일자: 2026-06-07 · 갱신자: 세션5–8(태그B·Kaniko·deploy수정·관측ingress)
- 대상 브랜치: master · 환경: 프레임워크/스택 무변경(devops). 세션5–7 커밋됨(실배포 그린 근거). 세션8 미커밋.

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| (5–7) CI/CD 완주 | 태그B(sentinel+pin-image-tag.sh)·Kaniko 4컨테이너 순차·ns ClusterRole·SM 분리 → **6파드 Running + :cd161c73c135 단일 sha 실측**. |
| (8) monitoring-values.yaml (신규) | kube-prometheus-stack values: grafana/prometheus ingress(ssl-redirect=false) + grafana.ini(domain/root_url) + prometheus externalUrl. |
| (8) 05 연결 | helm `-f monitoring-values.yaml` 머지 + 접속안내 ingress 우선(+ ingress 생성/도달 체크, hosts 안내). |
| (8) 06 안내 | Grafana 접속을 `grafana.local` 우선으로. |
| 검증(오프라인) | bash -n(05/06)·values 키·helm -f 연결·PITFALLS/§10 구조. |
| 문서 | PITFALLS §9 관측-ingress ★항목 + 자가진단 1행 · README 관측 섹션 · 이 SUMMARY · HANDOFF §7 append(-F). |

## 현재 상태 (적용/검증)
- **CI/CD**: ✅ 완주(6파드 Running, 불변 sha 핀). 세션5–7 커밋됨.
- **관측**: 파일 준비 완료. **받는 쪽: `05 --grafana` 재실행(helm upgrade) → hosts 1줄 → `http://grafana.local`/`http://prometheus.local`**. 06 으로 JVM 대시보드 + 4/4 타깃.
- **커밋**: 세션8 미커밋(+ 누적 백로그).

## 바로 다음 할 일 (Next)
1. **관측 올리기** — `bash 05-prometheus-stack.sh --grafana`(helm upgrade) → hosts `127.0.0.1 grafana.local prometheus.local` → `bash 06-grafana-jvm-dashboard.sh --grafana`(4/4 UP + JVM 대시보드). 브라우저 `http://grafana.local`.
2. **세션8 commit & push**(그린 박제).
3. **prod overlay `:latest` → sentinel/주입 전환**(가변-태그 부채).
4. **정리** — `NEXT_CI_KANIKO_MULTIBUILD.md` archive, HANDOFF §7 append(-C/-D/-E/-F) 본문 병합.

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **ingress 노출 = ssl-redirect:false 필수**(평문) — 없으면 http→https 308. Harbor 와 동일.
- **Grafana 는 server.domain/root_url 을 호스트에 맞춰야**(리다이렉트/링크 정확). Prometheus 는 externalUrl.
- **관측 UI 는 노드 이름해소 불요** — 노드가 pull 하는 레지스트리(Harbor)와 달리 호스트 브라우저만 보면 됨 → **호스트 hosts 한 줄**. certs.d/노드 hosts/split-horizon 전부 불필요.
- **CI/CD 완주 원칙(되돌리지 말 것)**: 불변 sha declarative 핀(5)·Kaniko 컨테이너당 1회·순차(6)·CI SA 는 ns ClusterRole 필요+옵셔널 CRD 리소스는 add-on 단독소유(7).
- **써머리 위치 = `docs/_internal/HANDOFF_SUMMARY.md`**. 배치 = `HANDOFF_BATCH_SUMMARY.md` 독립.

<!-- 갱신 끝 -->
