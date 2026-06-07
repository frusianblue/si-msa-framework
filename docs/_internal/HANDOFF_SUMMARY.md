# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**🟡 prod GitOps 4단계(Harbor hub + bash promote) 자산 작성·정적검증 완료 — 받는 쪽 실측(G11~G13) 대기(2026-06-08, devops · kind 멀티클러스터).** 빌드 주체 = **호스트 docker**(Chae 결정, Kaniko 아님 — "흐름부터 증명" 목적, 미해결 Kaniko 멀티빌드 회피). 신규 자산 3 (`deploy/k8s/prod-kind/`): **`30-harbor-hub-install.sh`**(standalone `08-harbor-install` 의 멀티클러스터 적응판 — kind-cicd 에 Harbor Helm[standalone `harbor-values.yaml` 단일소스 재사용] + si-msa public 프로젝트. cicd 노드 certs.d/CoreDNS **불요**: push=호스트 docker→127.0.0.1:80→ingress, pull=kind-svc 노드[03-cross-trust 배선]. kind-svc 신뢰 유효성 재확인 포함). **`40-promote.sh`**(① 호스트 docker `Dockerfile.build` 4서비스 `:<sha>` 빌드[builder SERVICE 무관 → 1회 컴파일 재사용] → ② `harbor.local/si-msa/<svc>:<sha>` push → ③ overlay images 줄 sed 치환[placeholder newName + sha newTag, flow 유지, 멱등, kustomize 불요] → ④ **git commit/push**[prod 반전 — dev "되커밋 X"와 정반대] → ⑤ ArgoCD `refresh=hard`. 0단계 워킹트리 clean 가드). **`41-verify-promote.sh`**(G11 Harbor repo `:<sha>` 아티팩트 · G12 핀[newTag sentinel 소거+newName harbor]+커밋·푸시 · G13 si-msa-prod Synced/Healthy+kind-svc 파드 green). 정적검증: bash -n PASS, sed 핀을 실측 overlay 복사본에 적용해 첫/재promote 멱등 + G12 grep 패턴(주석 `__GITSHA__` 오탐 회피 = `newTag: __GITSHA__` 한정) + YAML 유효성 검증. 문서 캐스케이드: prod-kind README §4 추가 · spec §0/§3-4 진행 표기 · PITFALLS §9 +4(placeholder 핀 · 검증 grep 오탐 · git identity fail-fast · CreateContainerConfigError 시크릿). **실측 경과(2026-06-08)**: ① git identity 미설정→commit 깨짐(빌드/push/핀은 성공, Harbor `:520856b` 적재) → 40 0단계 identity 선점검 추가 + 즉시 수동 커밋. ② 커밋·push 후 **promote 파이프라인 실측 증명** — ArgoCD 가 새 커밋 sync → 새 ReplicaSet(:520856b) 생성. ③ 새 파드 `CreateContainerConfigError`(prod 시크릿 미커밋) → **`35-seed-secrets.sh`** 신설(리허설 시크릿 4개 주입, DB=siuser/siuser_pw initdb 일치). **green 마무리 = 35 주입 후 41 재실행 대기.**

## 최종 갱신
- 일자: 2026-06-08 · 갱신자: 세션(prod GitOps 4단계 자산 작성·정적검증)
- 대상 브랜치: master · 환경: 프레임워크/스택 무변경(devops). **신규 prod-kind 스크립트 3 + 문서 4파일은 drop-in zip 전달 → 받는 쪽 적용·커밋.** ⚠️ `40-promote` 가 실행 시 overlays/prod 를 **자동 commit/push**(promote=GitOps 트리거).

## 직전에 한 것 (Done)
| 항목 | 산출/검증 |
|---|---|
| 4단계 설계 확정 | 빌드 주체 = 호스트 docker(Chae GO). Kaniko/Jenkins 파이프라인화는 흐름 증명 후 후속. |
| 30-harbor-hub-install | 08 멀티클러스터 적응(kind-cicd Harbor Helm + si-msa public). cicd 노드 신뢰 불요 논증 + kind-svc 신뢰 재확인. bash -n PASS. |
| 40-promote | 빌드→push→kustomize 핀(newName+newTag)→git commit/push(prod 반전)→ArgoCD refresh. 워킹트리 clean 가드. bash -n PASS. |
| 41-verify-promote | G11~G13 게이트. G12 sentinel grep 을 `newTag:` 한정(주석 오탐 회피, PyYAML 시뮬레이션 검증). bash -n PASS. |
| 문서 캐스케이드 | README §4 + spec §0/§3-4 + PITFALLS §9 +2항목. |

## 현재 상태 (적용/검증)
- **4단계 자산**: 🟡 작성·정적검증(bash -n·PyYAML) 완료 — **받는 쪽 실측 미실행**(Maven Central 차단 환경 = 빌드 불가, Chae WSL 에서 docker 빌드/PASS).
- **prod GitOps 1~3단계**: ✅ 살아 동작(직전 세션) — git(master)=진실, ArgoCD 가 kind-svc reconcile. 파드는 sentinel `__GITSHA__`+Harbor 미설치로 ImagePullBackOff(설계대로) → 4단계 promote 로 green 전환 대상.
- **문서**: README/spec/PITFALLS 정합. 4단계 = 🟡(실측 대기) 표기.
- **전달**: drop-in zip(스크립트 3 + 문서 4). 받는 쪽 `unzip -o` 후 30→40→41.

## 바로 다음 할 일 (Next)
> 받는 쪽(Chae WSL) 실측 → 결과 보고 → 그린이면 5단계.
1. **호스트 push 사전조건 확인** — Docker Desktop daemon `insecure-registries: ["harbor.local"]` + Windows/WSL hosts `127.0.0.1 harbor.local`.
2. **실측**: `bash 30-harbor-hub-install.sh` → `bash 35-seed-secrets.sh`(리허설 시크릿) → `bash 40-promote.sh`(자동 commit/push, git identity 선점검) → (대기) → `bash 41-verify-promote.sh`(G11~G13).
3. **그린 후 5단계** — 데이터/관측 정합(prod overlay DB/Redis K8s밖 엔드포인트 확정 + 관측 분산형). 이후 6단계 문서(`docs/ops/PROD_GITOPS_ARGOCD.md`).
4. (선택) Jenkins `Jenkinsfile.promote` 파이프라인화 — bash 흐름 증명 후.

## 빈칸 / 잔여
- **4단계 실측**: 받는 쪽 G11~G13 미수행. ImagePullBackOff/CrashLoop 트리아지 힌트는 41 의 [NOTE] 에 내장(노드신뢰/Harbor/push · DB/issuer/Authenticator).
- **운영 클러스터 VM**: prod 전용 VM 미생성(stg 폐기). master=prod 실 클러스터 위치 = VM 이전 시점 결정.
- **AUTH 트랙(별개 진행 중, 본 세션 미관여)**: `AUTH_SERVER.md` `NEXT_K8S_REAL_DEPLOY §S3'` 평문 잔여 1줄 + `modules/{AUTH_SERVER,OIDC_HARDENING}.md` 의 `planning/→archive/` 깨진 링크 — AUTH 작업 시 정정.

## 이번 섹션 함정/원칙 (되돌리지 말 것 · 상세 PITFALLS §9)
- **prod 핀은 newName 까지 박는다**: prod overlay image name=`registry.example.com/...`(placeholder). newTag 만 바꾸면 가짜 레지스트리 pull=ImagePullBackOff(local overlay 함정의 prod/harbor 판). overlay images 줄을 sed 로 통째 교체해 newName+newTag 동시(flow 유지, kustomize 불요).
- **prod=git 커밋이 진실(dev 반전)**: dev 는 워크스페이스만 핀(되커밋 X), prod 는 핀을 git commit/push 해야 ArgoCD 가 sync. `40-promote` 가 그 커밋을 만든다.
- **sentinel 검증은 키 위치 한정**: overlay 주석에도 `__GITSHA__` 설명이 있어 파일 전체 grep 은 핀 후 오탐 → `newTag: __GITSHA__`(images 한정)로 좁힘.
- **호스트 push = HTTP 평문**: harbor.local 은 insecure registry → daemon `insecure-registries` 필요(없으면 push 거부).
- **워킹트리 clean 전제**: 미커밋이면 `:<sha>` 가 실제 빌드 내용과 어긋남 → 40 의 0단계가 막음(overlay 파일만 예외).
- **promote 선결조건 fail-fast**: git identity(`user.email/name`) 없으면 빌드·push 다 한 뒤 commit 에서 깨짐 → 40 0단계가 빌드 전 선점검. 비싼 작업 앞에 모든 선결조건 점검.
- **prod 시크릿은 git 밖(보안 정석) → 클러스터 사전존재가 기동 선결조건**: overlay 미커밋이라 ns 에 4개 `*-secret` 없으면 `CreateContainerConfigError`(ImagePullBackOff 아님 — config 단계). 리허설=`35-seed-secrets`, 운영=ESO/SealedSecrets. 신호 구분: ImagePullBackOff=이미지, CreateContainerConfigError=Secret/CM 부재.
<!-- 갱신 끝 -->
