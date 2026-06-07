# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**🟢 prod GitOps 4단계 bash promote 파이프라인 end-to-end 증명 + 4서비스 중 3(+redis) green — admin-service CrashLoop 만 잔여(다음 섹션 진입 과제)(2026-06-08, devops · kind 멀티클러스터).** 빌드 주체 = **호스트 docker**(Chae 결정, Kaniko 아님). **신규 자산 5**(`deploy/k8s/prod-kind/`): `30-harbor-hub-install`(08 멀티클러스터 적응 — kind-cicd Harbor Helm + si-msa public) · `40-promote`(호스트 docker 4서비스 `:<sha>` → push → overlay images **sed** 치환[placeholder newName+sha newTag, kustomize 불요] → **git commit/push**[prod 반전] → ArgoCD refresh; 0단계 워킹트리 clean + git identity 선점검) · `41-verify-promote`(G11~G13) · `35-seed-secrets`(리허설 시크릿 4개, DB=siuser/siuser_pw) · `36-reset-sidb-rehearsal`(sidb 오염 정리, PG16 WITH FORCE). **매니페스트 픽스 2**: deployment-hardening `FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads`(문서-구현 드리프트 보정) · prod overlay admin DB_URL `sidb→admindb`(Flyway 분리). **promote 증명** = ArgoCD 가 master 새 커밋 sync → `:520856b` 새 ReplicaSet(빌드→push→핀→commit→sync→새 RS 전 구간 실측). **실측 트러블슈팅 순차**: ① git identity→commit 깨짐(40 0단계 선점검) ② CreateContainerConfigError=prod 시크릿 미커밋(35) ③ admin `/application/uploads` read-only=file base-path 누락(보정) ④ user `Flyway checksum mismatch`=sidb 공유(admin→admindb+sidb 재생성=user Running). **현재 green**: gateway·auth-server·user-service·redis. **잔여: admin-service CrashLoop**(file/admindb 적용 후에도 — 원인 미규명).

## 최종 갱신
- 일자: 2026-06-08 · 갱신자: 세션(prod GitOps 4단계 promote 증명 + 부분 green) — **이번 섹션 마무리**
- 대상 브랜치: master · 환경: 프레임워크/스택 무변경(devops). **drop-in zip 전달(스크립트 5 + 매니페스트 2 + 문서 4=11파일).** ⚠️ `40-promote` 는 overlays/prod(images 핀)만 자동 commit/push — deployment-hardening·admin DB_URL 등 base/overlay 변경은 **별도 commit/push** 필요(ArgoCD=master 진실).

## 직전에 한 것 (Done)
| 항목 | 산출/검증 |
|---|---|
| promote 파이프라인 | ✅ end-to-end 증명 — ArgoCD master 새 커밋 sync → :520856b 새 RS. 빌드(호스트 docker)→push→sed핀→commit→sync. |
| 30/40/41/35/36 | 5 스크립트 작성·bash -n PASS·정적검증(sed 핀 멱등·G12 grep·YAML). 받는 쪽 실측. |
| 매니페스트 픽스 | deployment-hardening file base-path + prod overlay admin→admindb. |
| 실측 green | gateway·auth-server·user-service·redis Running. user 는 sidb 분리/재생성으로 Flyway 해소 확인. |
| 문서 | HANDOFF §7 누적 + SUMMARY + spec §0/§3-4 + PITFALLS §9 6건 + README §4. |

## 현재 상태 (적용/검증)
- **promote 파이프라인**: ✅ 증명. git(master)=진실, ArgoCD reconcile.
- **파드 green**: gateway ✅ · auth-server ✅ · user-service ✅ · redis ✅ / **admin-service ❌ CrashLoop(잔여)**.
- **매니페스트**: deployment-hardening(file base-path) · prod overlay(admin admindb) · images(:520856b) 커밋/push/sync.
- **이번 섹션 마무리** — admin CrashLoop 은 다음 섹션 첫 과제로 인계.

## 바로 다음 할 일 (Next) — 다음 섹션 진입
1. **admin-service CrashLoop 원인 규명** — `kubectl --context kind-svc -n si-msa logs <admin-pod> --previous --tail=80`. file(/tmp/uploads)·admindb 적용 후 잔여 원인 후보: admindb Flyway · TokenStore redis 의존(PITFALLS §9, build.gradle framework-redis) · 기타 startup. gateway/auth/user 가 green 이라 admin 고유 문제.
2. **5단계 데이터/관측 정합** — file storage 전략 통일(local /tmp vs s3/PVC) · DB 분리 확정(user=sidb/admin=admindb) · 관측 분산형(워크로드 agent → 중앙 Grafana).
3. **6단계 문서 캐스케이드** — `docs/ops/PROD_GITOPS_ARGOCD.md` 신설 + branch-per-env 가이드.
4. (선택) Jenkins `Jenkinsfile.promote` 파이프라인화 — bash 흐름 증명 완료.

## 빈칸 / 잔여
- **admin-service CrashLoop**: 미규명. 다음 섹션 logs 진입.
- **운영 클러스터 VM**: prod 전용 VM 미생성(stg 폐기). master=prod 실 클러스터 위치 = VM 이전 시점 결정.
- **AUTH 트랙(별개 진행 중, 본 세션 미관여)**: `AUTH_SERVER.md` `NEXT_K8S_REAL_DEPLOY §S3'` 평문 잔여 1줄 + `modules/{AUTH_SERVER,OIDC_HARDENING}.md` `planning/→archive/` 깨진 링크.

## 이번 섹션 함정/원칙 (되돌리지 말 것 · 상세 PITFALLS §9)
- **prod 핀은 newName 까지(sed)**: placeholder name `registry.example.com/...` → newTag 만 바꾸면 가짜 레지스트리 pull. overlay images 줄 sed 통째 교체로 newName+newTag 동시(kustomize 불요).
- **prod=git 커밋이 진실(dev 반전)**: dev 워크스페이스만 핀(되커밋 X), prod 는 핀을 git commit/push 해야 ArgoCD sync.
- **promote 선결조건 fail-fast**: git identity 없으면 빌드·push 후 commit 에서 깨짐 → 40 0단계 선점검.
- **prod 시크릿 git 밖 → 클러스터 사전존재가 기동 선결**: 없으면 CreateContainerConfigError(ImagePullBackOff 아님). 리허설=35, 운영=ESO/SealedSecrets.
- **문서-구현 드리프트 경계**: PITFALLS 에 "해결"이라 적어도 실제 매니페스트 반영 여부를 grep 확인(deployment-hardening file base-path 가 k8s 에 빠져 있었음).
- **한 DB·한 flyway_history 에 두 서비스 금지**: user↔admin sidb 공유 = checksum mismatch. 분리(admin→admindb) + 오염 시 DB 재생성.
- **base/overlay 변경은 40 이 자동커밋 안 함**: images 핀만. deployment-hardening·DB_URL 은 별도 commit/push.
<!-- 갱신 끝 -->
