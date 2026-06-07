# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**✅ 이미지 태그 전략 B + Kaniko 다중빌드 둘 다 정리(2026-06-07 세션5–6, devops).** (1) **태그**: 가변 `:dev` 핀 + 명령형 `kubectl set image` 폐기 → **불변 git-sha 단일 태그**를 apply 시점에 overlay 에 declarative 주입(sentinel `__GITSHA__` + 공용 헬퍼 `deploy/k8s/pin-image-tag.sh`, 워크스페이스만 치환·되커밋 X, 미주입은 ImagePullBackOff fail-loud). Jenkinsfile/03/07 정합. (2) **Kaniko 다중빌드 해결**: 한 컨테이너 루프 2회차 `sh` 클로버(executor 가 / 를 대상 이미지로 덮어씀)를 **서비스별 kaniko 컨테이너 4개 + 각 container 블록 executor 1회**로 해소. **순차**(병렬 금지 — `Dockerfile.build` 공유 builder 캐시 재사용 보존). push 는 `:<sha>` 단일. 오프라인 검증: 셸 문법·헬퍼 동작·pod YAML 파싱(컨테이너 5)·Groovy 괄호 균형 PASS. **실잡 실행·캐시 재사용·6파드 rollout 실측은 받는 쪽 로컬.**

## 최종 갱신
- 일자: 2026-06-07 · 갱신자: 세션5–6(이미지 태그 B + Kaniko 다중빌드)
- 대상 브랜치: master · 환경: 프레임워크/스택 무변경(devops). 미커밋(누적 + 세션4 + 세션5–6).

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| 태그: overlays/dev | `newTag: dev`→sentinel `__GITSHA__`(4) + 주석 재작성(주입 계약·fail-loud). |
| 태그: pin-image-tag.sh (신규) | 공용 주입 헬퍼. sed·태그형식 방어·멱등. CI·수동 공용. |
| 태그: 03/07 정합 | 03=불변태그 push+주입→apply→작업트리 복원(— :dev). 07=라이브 Deployment image ref 태그로 kind load(— 하드코딩 :dev). 02 안내 주석 정정. |
| Kaniko: Jenkinsfile.kind | podTemplate kaniko 컨테이너 1→4(`kaniko-<svc>`) + kubectl. Build&Push=서비스별 `container("kaniko-${svc}")` 순차 executor 1회. push `:<sha>` 단일. Deploy=pin→apply -k(— set image). |
| 검증(오프라인) | bash -n 3종·헬퍼 치환/멱등/거부·작업트리 복원·코드경로 :dev 0건·pod YAML 파싱(컨테이너 5)·Groovy 괄호 균형·루프↔컨테이너 매핑. |
| 문서 | PITFALLS §9 ★ 2항목(태그·Kaniko) + 자가진단 3행 · 이 SUMMARY · standalone README 메모 · HANDOFF §7 append(-C 태그, -D Kaniko). |

## 현재 상태 (적용/검증)
- **태그 흐름**: 가변 `:dev` 배포핀 전면 폐기. 무엇이 뜨는가 = 불변 `:<sha>`, apply 시 declarative 주입. git 기본값=sentinel(fail-loud).
- **CI(`si-msa-cd`)**: Build&Push(서비스별 kaniko 컨테이너 순차) + Deploy(pin→apply) **파일 재설계 완료**. 실잡 실행은 받는 쪽.
- **클러스터/앱 6파드**: 매니페스트/스크립트/파이프라인만 변경(미배포). 새 흐름 실증은 받는 쪽 로컬.
- **커밋**: 누적 + 세션4 + 세션5–6 **미커밋**.

## 바로 다음 할 일 (Next)
1. **실클러스터 완주(최우선)** — `si-msa-cd` Build Now → 4서비스 `:<sha>` build→push(첫 빌드만 풀 Gradle, 3개 cache 재사용 확인) → `pin→apply -k` → 6파드 rollout 그린. 미주입 `apply -k` 가 ImagePullBackOff(fail-loud) 인지도 1회 확인.
2. **prod overlay `:latest` → sentinel/주입 전환**(동일 가변-태그 부채 청산).
3. **commit/push 누적분 정리**(그린 박제) — 세션2 S5 이후 전부 미커밋.
4. **정리** — `NEXT_CI_KANIKO_MULTIBUILD.md` archive(해결), HANDOFF §7 append(-C/-D) 본문 병합.

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **무엇이 뜨는가 = 불변 태그, declarative 한 곳 핀.** 가변 채널 태그를 배포 진실로 쓰거나 선언형을 명령형 `set image` 로 덮지 않는다(드리프트).
- **overlay 기본값 = fail-loud sentinel(`__GITSHA__`)** — 미주입 apply 는 조용한 stale 대신 ImagePullBackOff.
- **주입은 워크스페이스만(되커밋 X)** — 단일 지점 `pin-image-tag.sh`. overlay 는 `../../base` 상대참조라 임시복사 apply 불가(03=in-place→복원).
- **Kaniko = 컨테이너당 executor 1회.** 다중빌드는 서비스별 컨테이너 분리. **순차**(공유 builder 캐시 보존 — 병렬은 풀빌드 N회). 서비스 추가 시 podTemplate 컨테이너 + 빌드 루프 동시 갱신.
- **`IfNotPresent` + 불변 sha = 정합**(매 sha 새 pull 1회); 가변 태그와 만나면 stale 함정.
- **써머리 위치 = `docs/_internal/HANDOFF_SUMMARY.md`**(레포 루트 아님).
- **배치 트랙 ≠ 메인 트랙** — `HANDOFF_BATCH_SUMMARY.md` 독립 갱신.

<!-- 갱신 끝 -->
