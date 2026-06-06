# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**⛔→🔀 S3 가 Docker Desktop kind 에서 노드 pull 구조적 한계로 BLOCKED, standalone kind 트랙으로 전환 결정(2026-06-06 세션3).** dev overlay apply 시 새 파드 `ImagePullBackOff` — `describe` Events 가 `registry-mirror:1273 ... ?ns=harbor.local: 500`. **노드 containerd 가 모든 pull 을 Docker Desktop 내장 미러로 가로채 `harbor.local` 직접 pull 이 500**. 절단으로 확정: `harbor-cred`/default SA/파드 spec 모두 정상(=인증 아님), 호스트·Harbor 둘 다 실재하는 `:dev` 태그로도 동일(=태그 아님), 호스트 `docker pull harbor.local/...` 는 성공(=호스트 데몬과 노드 containerd 는 **다른 주체**, 파드 띄우는 건 후자). 노드 설정은 `kind create --config` 입구가 없어 선언적으로 못 박고, 노드 직접 수정은 재현 불가·운영 패턴 아님. **부수 확인**: 핀 `7e935d6` 은 Harbor 에 부재(stale)였음(실재=6849550/dev/f370bc7) → 수동 sha 핀의 증상, sha 핀은 CI 몫. **ArgoCD 로도 안 풀림**(CD=apply, pull 은 언제나 노드). **결정(합의): ① PITFALLS 제약 못박기(완료) → ② standalone kind 최소 pull sanity → ③ Harbor/ingress/postgres 풀 재구축 → ④ push→노드 pull(Pull>0)→dev apply. 실행은 다음 섹션.**

## 최종 갱신
- 일자: 2026-06-06 · 갱신자: 운영 리허설 세션3(S3 트리아지·standalone 전환 결정·문서 정리)
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / SF7 / SS7 / SC 2025.1.1 / Jackson 3 — 스택 무변경(devops 매니페스트·문서만).

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| S3 노드 pull 트리아지 | apply→`ImagePullBackOff`. Events 로 미러 인터셉트 500 확정. cred/SA 정상·`:dev` 도 실패·호스트 pull 성공으로 3중 절단. **Docker Desktop kind 구조적 한계로 결론**. |
| dev overlay 정정 | 이미지 핀 stale `7e935d6`→`:dev`(채널 태그; sha 핀은 CI). (이전 세션의 admindb·/tmp/uploads·ServiceMonitor `$patch:delete`·postgres-persistent·pull-secret 은 이미 반영됨.) |
| 문서 정리 | `PITFALLS.md`(Docker Desktop kind "미러 인터셉트=외부 pull 불가" 항목 완성 + 지난 private-Harbor 예측을 실측으로 정정 + quick-ref) · `NEXT_K8S_REAL_DEPLOY.md`(S3 BLOCKED 결론 배너 + **§S3' standalone kind 트랙 = 다음 시작점** + §0 락 #6 클러스터 결정 + 인벤토리). |

## 현재 상태 (적용/검증)
- **클러스터**: `docker-desktop` kind 3노드. S1(postgres PVC Bound)·S2(Harbor push 4×2 태그) 정상. **S3 노드 pull = BLOCKED**(미러 인터셉트). 잔여 디버그 파드(`pulltest`, `node-debugger-*`) 정리 필요.
- **매니페스트**: dev overlay 완성·정적검증 통과(DB/admindb/파일저장/SM-delete/pull-secret). **단 Docker Desktop kind 에선 pull 불가 → standalone 에서 검증.**
- **이미지**: Harbor `si-msa` 에 `6849550`/`dev`/`f370bc7`(=현 HEAD). overlay 핀=`:dev`.

## 바로 다음 할 일 (Next) — §S3' standalone kind 트랙
1. **잔여 정리**: `kubectl delete pod pulltest -n si-msa --ignore-not-found`; `kubectl delete pod -n default node-debugger-desktop-worker* --ignore-not-found`.
2. **최소 pull sanity (이론 맹신 금지, 먼저 검증)**: standalone `kind`(`kind-config.yaml` + `certs.d/harbor.local/hosts.toml`) 1클러스터 + 로컬 레지스트리 + 더미 이미지 push→**노드 pull 성공**까지. 통과해야 풀 재구축.
3. **풀 재구축**: Harbor/ingress/postgres 스크립트화 재설치(소스 github 보존이라 기존 자산 소멸 무방).
4. **push→노드 pull(Harbor Pull>0)→dev overlay apply→DB/admindb/파일저장/AS 토큰**(S3 앱·토폴로지 검증 로직 재사용).
- 이후: S4 애드온 → S5 prod-rehearsal → S6 상위 흐름 → S7 Jenkins(여기서 sha 핀 자동 주입).

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **Docker Desktop kind = 이미지 자동노출(편의)의 이면에 외부 레지스트리 직접 pull *불가*(제약).** 노드 containerd 가 미러 인터셉트(`registry-mirror:1273`, `?ns=harbor.local`)→`harbor.local` 한정이름 pull-through 500. 자동노출은 **짧은 이름**만. 레지스트리 pull 실증=standalone kind 필수.
- **"호스트 데몬 pull" ≠ "노드 containerd pull"** — 파드를 띄우는 건 노드. 호스트 `docker pull` 성공이 노드 pull 성공을 뜻하지 않음.
- **노드 설정은 *생성 시 선언*(containerdConfigPatches)이지 *뜬 노드 손수정*이 아니다.** 후자는 재현 불가·운영 아님.
- **ArgoCD/GitOps ≠ pull** — CD 는 매니페스트 apply 만, 이미지 pull 은 언제나 kubelet+노드 containerd. GitOps 는 노드 pull 정상 전제 위에 얹는 층.
- **불변 sha 핀은 CI 몫** — 사람이 매니페스트에 타이핑한 sha 는 stale 된다(7e935d6). 수동 리허설은 채널 `:dev`.
- **standalone kind=단일노드 강제 아님** — `nodes:` 로 현 3노드 그대로 재현. 노드 수와 standalone 여부는 무관.
- **이론 맹신 금지** — standalone 으로 다 갈아엎기 전에 최소 pull sanity 1스텝 먼저.

<!-- 갱신 끝 -->
