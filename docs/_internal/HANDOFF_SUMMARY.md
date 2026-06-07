# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**✅ 이미지 태그 전략 B 확정·구현(2026-06-07 세션5, devops).** 직전 CI 흐름의 냄새(가변 `:dev` 핀 + `kubectl set image :<sha>` 명령형 덮어쓰기 = git↔클러스터 드리프트, 죽은 `:dev` 태그, IfNotPresent stale 잠복)를 제거. **단일 진실 = 불변 git-sha 태그**, apply 시점에 overlay 에 **declarative 주입**. (1) dev overlay `newTag` → sentinel `__GITSHA__`(미주입 apply 는 ImagePullBackOff 로 **fail-loud** → 조용한 stale 구조 차단). (2) 신규 공용 헬퍼 `deploy/k8s/pin-image-tag.sh <overlay-dir> <tag>`(sed·멱등·kustomize 불요)로 주입 단일화 — CI·수동 트랙 공용, **체크아웃 워크스페이스만 치환(되커밋 X)**. (3) `Jenkinsfile.kind`: Kaniko push 를 **`:<sha>` 단일 태그**(가변 `:dev` destination 제거) + deploy 를 `pin → apply -k`(명령형 `set image` 제거). (4) `03-dev-overlay-up.sh`: `:dev` push 폐기 → 불변 태그(커밋 short sha, 미커밋이면 경고) push + in-place 주입→apply→**작업트리 sentinel 복원**. (5) `07-reboot-recover.sh`: 하드코딩 `:dev` 대신 **살아있는 Deployment image ref 의 태그**로 kind load. 오프라인 검증: bash -n 3종 PASS, 헬퍼 치환/멱등/잘못된태그거부 PASS, 작업트리 sentinel 복원 확인. (kustomize build 검증은 받는 쪽 로컬.) ⚠️ prod overlay `:latest` 동일 부채는 차기 동일 전환 예정. **Kaniko 다중빌드 미해결은 그대로** — 다음 세션 #1 유지.

## 최종 갱신
- 일자: 2026-06-07 · 갱신자: 세션5(이미지 태그 전략 B 확정·구현)
- 대상 브랜치: master · 환경: 프레임워크/스택 무변경(devops). 미커밋(누적 + 세션4 + 세션5).

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| overlays/dev | `newTag: dev` → sentinel `__GITSHA__`(4서비스) + 주석 재작성(주입 계약·fail-loud 근거). |
| pin-image-tag.sh (신규) | 공용 주입 헬퍼. sed 기반·태그형식 방어·멱등·`__GITSHA__`→`<tag>`. CI·수동 공용. |
| Jenkinsfile.kind | Build&Push=`:<sha>` 단일(— `:dev`). Deploy=`pin-image-tag.sh` 주입→`apply -k`(— `kubectl set image`). 헤더 주석 갱신. |
| 03-dev-overlay-up.sh | `:dev` push 폐기 → 불변 태그 push + in-place 주입→apply→작업트리 복원(trap). 헤더 갱신. |
| 07-reboot-recover.sh | `:dev` 하드코딩 → 라이브 Deployment image ref 태그로 kind load(미주입 sentinel 가드). |
| 검증(오프라인) | bash -n 3종·헬퍼 동작(치환/멱등/거부)·작업트리 sentinel 복원·코드경로 가변:dev 0건. |
| 문서 | PITFALLS §9 신규 ★항목 + 자가진단 2행 · 이 SUMMARY · standalone README 메모 · HANDOFF §7 append(-C). |

## 현재 상태 (적용/검증)
- **태그 흐름**: 가변 `:dev` 배포핀 전면 폐기. 무엇이 뜨는가 = 불변 `:<sha>`, apply 시 declarative 주입. git 기본값=sentinel(fail-loud).
- **CI(`si-msa-cd`)**: deploy 단계 재설계 완료(파일). **단, Kaniko 다중빌드(gateway 만 빌드) 미해결** — 태그 전략과 독립, 다음 세션 #1.
- **클러스터/앱 6파드**: 직전 세션 상태 그대로(태그 변경은 매니페스트/스크립트만, 미배포). 새 흐름 실증은 받는 쪽 로컬에서.
- **커밋**: 누적 + 세션4 + 세션5 **미커밋**.

## 바로 다음 할 일 (Next)
1. **Kaniko 다중 이미지 빌드 재설계**(세션4에서 이월, **최우선**) — `_internal/planning/NEXT_CI_KANIKO_MULTIBUILD.md`. 서비스별 분리 빌드로 4서비스 모두 build→push(:<sha> 단일 태그). 태그 전략은 이미 정리됨(push 는 `:<sha>` 만).
2. **새 태그 흐름 실증** — 받는 쪽 로컬: `kubectl kustomize overlays/dev`(주입 후) 빌드 확인 → `03-dev-overlay-up.sh`(또는 CI) → 6파드 `:<sha>` 핀 rollout → 미주입 `apply -k` 가 ImagePullBackOff(fail-loud) 로 떨어지는지도 1회 확인.
3. **prod overlay `:latest` → sentinel/주입 전환**(동일 부채 청산).
4. **commit/push 누적분 정리**(그린 박제) — 세션2 S5 이후 전부 미커밋.
5. **실클러스터 완주** — 4서비스 push 후 노드 pull → `apply -k` → 6파드 rollout(세션4 이월).

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **무엇이 뜨는가 = 불변 태그, declarative 한 곳 핀.** 가변 채널 태그(`:dev`)를 배포 진실로 쓰거나, 선언형 매니페스트를 명령형 `set image` 로 사후에 덮지 않는다(= git↔클러스터 드리프트).
- **overlay 기본값은 fail-loud sentinel(`__GITSHA__`)** — 미주입 apply 가 조용한 stale 대신 ImagePullBackOff. "조용한 성공"보다 "시끄러운 실패"가 안전.
- **주입은 워크스페이스만, 되커밋 없음** — CI=잡 워크스페이스, 수동=in-place→apply→복원(overlay 는 `../../base` 상대참조라 임시복사 apply 불가). 헬퍼 단일 지점(`pin-image-tag.sh`).
- **`IfNotPresent` + 불변 sha = 정합**(매 sha 새 pull 1회). `IfNotPresent` + 가변 태그 = stale 함정. 태그 불변성이 풀정책의 전제.
- **redeploy.sh(콘텐츠 다이제스트)는 같은 원칙의 단일-서비스판** — 미커밋 변경까지 추적할 땐 그쪽.
- **써머리 위치 = `docs/_internal/HANDOFF_SUMMARY.md`**(레포 루트 아님). 드롭인 zip 경로 준수.
- **배치 트랙 ≠ 메인 트랙** — 배치 상세=`HANDOFF_BATCH_SUMMARY.md`. 독립 갱신.

<!-- 갱신 끝 -->
