# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**🔀 §S3' standalone kind 트랙 2단계(최소 pull sanity) 산출물 드롭 — 받는 쪽 실행 대기(2026-06-06 세션4).** Docker Desktop kind 미러 인터셉트로 BLOCKED 된 "외부 레지스트리→노드 직접 pull" 을, `kind` CLI 직접 생성 클러스터에서 *생성 시 선언*(containerdConfigPatches+certs.d extraMounts)으로 닫는 트랙의 첫 실증 스텝. **Docker Desktop "Modify Kubernetes Cluster" GUI 확인 결과 노드 containerd 설정 입구 없음 재확인**(Advanced Settings=Show system containers 가시성 토글뿐) → standalone 전환 결정 재확정. **산출물**: `deploy/k8s/standalone-kind/`(`kind-config.yaml` 3노드+config_path+extraMounts, `certs.d/reg.local/hosts.toml`→kind-registry:5000, `01-pull-sanity.sh` 레지스트리+busybox push→**노드 pull→파드 Ready** PASS/FAIL, `00-cleanup.sh` 잔여파드+teardown, `README.md`). **설계 결정 2(Windows 함정 회피)**: ① 레지스트리 이름엔 **점(.) 필수**(`reg.local`; 점 없으면 containerd 가 Docker Hub org 로 파싱) ② certs.d 디렉터리엔 **콜론 금지**(`localhost:5001` 은 NTFS 불가→extraMounts 깨짐) → 포트 없는 이름+hosts.toml 안에서 `:5000` 리다이렉트. push(localhost:5001)≠pull(reg.local)이나 리포지토리 경로 동일=같은 블롭. **다음**: 받는 쪽이 `01-pull-sanity.sh` 실행 → PASS 면 §S3' 3) Harbor/ingress/postgres 풀 재구축 → 4) push→노드 pull(Pull>0)→dev overlay apply.

## 최종 갱신
- 일자: 2026-06-06 · 갱신자: 운영 리허설 세션4(§S3' 2단계 산출물·GUI 한계 재확인·문서 동반)
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / SF7 / SS7 / SC 2025.1.1 / Jackson 3 — 스택 무변경(devops 매니페스트/스크립트·문서만).

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| GUI 한계 재확인 | Docker Desktop "Modify Kubernetes Cluster" 다이얼로그엔 containerd/registry 설정 입구 없음(Advanced=Show system containers 가시성뿐). standalone 전환(§0 락#6) 재확정. |
| §S3' 2단계 산출물 | `deploy/k8s/standalone-kind/` 신규: kind-config(3노드+config_path+certs.d extraMounts)·hosts.toml(reg.local→kind-registry:5000)·01-pull-sanity.sh·00-cleanup.sh·README. 정적검증 통과(bash -n, yaml/toml parse). |
| 문서 동반 | `NEXT_K8S_REAL_DEPLOY.md`(§S3' 예시→실제 산출물 포인터, step2 드롭 표기, 인벤토리) · `PITFALLS.md` §9 신규 2항(레지스트리 이름 점 필수 / certs.d 콜론 금지)+자가진단 2행. |

## 현재 상태 (적용/검증)
- **클러스터**: 현 `docker-desktop` kind 3노드 유지(S1 postgres PVC·S2 Harbor push 정상, S3 노드 pull=BLOCKED). standalone `kind-sanity` 는 **아직 미생성**(받는 쪽이 01-pull-sanity.sh 로 생성·검증).
- **산출물**: `standalone-kind/` 정적검증만(작성환경 docker/kind/kubectl 부재 → 실행은 받는 쪽). 매니페스트(dev overlay)는 기존 완성·정적검증 통과 상태 유지.
- **이미지**: Harbor `si-msa` 에 `6849550`/`dev`/`f370bc7`. dev overlay 핀=`:dev`(sha 핀은 CI 몫).

## 바로 다음 할 일 (Next) — §S3' 2단계 실행 → 3단계
1. (선택) `bash deploy/k8s/standalone-kind/00-cleanup.sh` — docker-desktop kind 잔여 디버그 파드(pulltest, node-debugger-*) 정리.
2. **`bash deploy/k8s/standalone-kind/01-pull-sanity.sh`** — standalone kind+레지스트리+busybox→**노드 pull→파드 Ready**. **✅ PASS 해야** 3단계 착수(이론 맹신 금지). FAIL 시 스크립트 트리아지 힌트(certs.d 마운트/이름규칙/네트워크).
3. PASS 후 **Harbor/ingress/postgres 풀 재구축(스크립트화)** → push→**노드 pull(Harbor Pull>0)**→`kubectl apply -k deploy/k8s/overlays/dev`→DB/admindb/파일저장/AS 토큰(S3 앱·토폴로지 검증 로직 재사용).
- 이후: S4 애드온 → S5 prod-rehearsal → S6 상위 흐름 → S7 Jenkins(sha 핀 자동 주입).

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **Docker Desktop GUI 로는 노드 containerd 못 박는다** — "Modify Kubernetes Cluster" 는 클러스터타입/노드수/k8s버전+Show system containers 뿐. containerd/certs.d 입구 없음 = standalone kind 필수의 재확인.
- **standalone kind: 레지스트리 이름엔 점(.) 필수** — 점 없는 이름(`kind-registry`)은 containerd 가 Docker Hub org 로 파싱 → pull 테스트 무의미. `reg.local`(점 있음)으로.
- **certs.d 디렉터리엔 콜론 금지** — `localhost:5001`(콜론)은 NTFS 디렉터리 불가 → extraMounts 깨짐. 포트 없는 호스트명+hosts.toml 안에서 `:5000`.
- **push 이름 ≠ pull 이름이어도 리포지토리 경로 같으면 같은 블롭** — 호스트는 published 포트(localhost:5001) push, 노드는 reg.local pull. 레지스트리는 호스트명 아닌 리포지토리 경로로 저장.
- **node 설정은 생성 시 선언(extraMounts/containerdConfigPatches)** — 뜬 노드 손수정(`docker exec`/`kubectl debug node`)은 재현 불가·git 밖·운영 아님(콜론 불가피할 때의 예외만).
- **이론 맹신 금지** — 풀 재구축 전 01-pull-sanity.sh PASS 가 게이트.
- **ArgoCD/GitOps ≠ pull** — CD 는 apply 만, pull 은 언제나 노드 containerd.

<!-- 갱신 끝 -->
