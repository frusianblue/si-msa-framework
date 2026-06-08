# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**🟢 인계 과제 admin-service CrashLoop 원인 규명·해결 + user-service 동근원 정합 → promote 재완주로 4서비스+redis 전부 green(2026-06-08, devops · kind 멀티클러스터). 4단계 prod GitOps 종료.** 근인 = **file storage base-path 환경변수 바인딩 실패**(kebab-case relaxed binding 함정). admin 은 application.yml 에 `framework.file.*` 미선언 → base-path 를 env relaxed binding 에만 의존하는데, 매니페스트가 주는 `FRAMEWORK_FILE_STORAGE_BASE_PATH`(언더스코어)가 Spring 정식 형태(대시 제거 = `FRAMEWORK_FILE_STORAGE_BASEPATH`)와 어긋나 **바인딩 미보장** → 기본값 `./uploads`(=`/application/uploads`) 사용 → readOnlyRootFilesystem 에서 createDirectories 실패 → localFileStorage 빈 실패 → 기동 불가(첨부 로그 경로가 `/application/uploads` = 바인딩 실패의 결정적 증거). user 는 별개 양상 동근원 — `FILE_STORAGE_TYPE=s3` 인데 `framework-file-s3` 주석(미의존) → S3FileStorage 빈 부재 + localFileStorage 는 type=local 조건 → **FileStorage 빈 0개** → fileService 미충족 CrashLoop. **해결(이론 의존 X, 명시 placeholder 로 모호성 제거)**: admin/user `application.yml` 에 `base-path: ${FRAMEWORK_FILE_STORAGE_BASE_PATH:./uploads}` 박음(명시 placeholder=env 명 그대로 매칭) + user configmap `FILE_STORAGE_TYPE: s3→local`(s3 모듈 없는 거짓 기본값 제거). **매니페스트는 무변경**(hardening 이 이미 `/tmp/uploads` env 공급). **promote 재완주**: `40-promote` 가 `:e5e1aff` 4서비스 빌드→harbor.local push→prod overlay sed 핀→commit/push→ArgoCD sync. **실측 PASS**: `41-verify-promote` G11~G13 전부 PASS(8파드 1/1 Running, sentinel 소거 + newName 4건 핀, ArgoCD Synced) · `42-diagnose-admin-file`(신규) 가 admin 파드 env `FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads` 주입 확인 + CrashLoop 로그 file 경로 미재현 → 원인 닫힘. 옛 RS(`:__GITSHA__`/`:520856b`) 0 스케일, 새 `:e5e1aff` RS 2/2.

## 최종 갱신
- 일자: 2026-06-08 · 갱신자: 세션(admin/user file storage 정합 + 4단계 prod GitOps green 마감)
- 대상 브랜치: master · 환경: 프레임워크 무변경(서비스 설정 yml 2 + base configmap 1 + 진단 스크립트 1 + 문서). **drop-in zip 전달, Chae 가 적용·커밋·promote 실행.**

## 직전에 한 것 (Done)
| 항목 | 산출/검증 |
|---|---|
| admin CrashLoop 원인 | ✅ 규명 — local FileStorage createDirectories on read-only rootfs + base-path env relaxed binding 미보장(소스 실독). |
| 해결(admin/user) | admin/user `application.yml` 명시 placeholder `${FRAMEWORK_FILE_STORAGE_BASE_PATH:./uploads}` + user configmap type local. 매니페스트 무변경. |
| 진단 스크립트 | `42-diagnose-admin-file.sh` 신규(파드 env·로그 경로·Ready 판정, bash -n PASS). |
| promote 재완주 | `40-promote` :e5e1aff 4서비스 빌드/push/핀/commit/sync. |
| 실측 green | G11~G13 PASS, 8파드 1/1 Running, admin env=/tmp/uploads 주입 + file 에러 미재현. |
| 문서 | HANDOFF §7 누적 + SUMMARY + PITFALLS §9 1건(env 바인딩 함정·기존 항목 보정) + NEXT 진단/해결 기록. |

## 현재 상태 (적용/검증)
- **파드 green**: gateway ✅ · auth-server ✅ · user-service ✅ · admin-service ✅ · redis ✅ (8파드 1/1 Running).
- **promote 파이프라인**: ✅ 재완주. git(master :e5e1aff)=진실, ArgoCD reconcile.
- **file storage**: admin/user 둘 다 type=local + base-path `/tmp/uploads`(hardening env, /tmp emptyDir). ⚠️ /tmp 휘발 = 리허설 한정, 운영 영속은 s3/PVC(5단계).
- **4단계 prod GitOps 종료** — 인계 과제(admin CrashLoop) 닫힘.

## 바로 다음 할 일 (Next) — 다음 섹션
1. **5단계 데이터/관측 정합** — file storage 운영 전략 통일(local /tmp → s3/PVC) · DB 분리 확정(user=sidb / admin=admindb) · 관측 분산형(워크로드 agent → 중앙 Grafana).
2. **6단계 문서 캐스케이드** — `docs/ops/PROD_GITOPS_ARGOCD.md` 신설 + branch-per-env 가이드.
3. (선택) Jenkins `Jenkinsfile.promote` 파이프라인화 — bash 흐름 증명 완료.
4. (위생) user-service s3 복귀 시 = `framework-file-s3` 의존 해제 + configmap type=s3 + S3 자격증명(현재는 local 로 청산).

## 빈칸 / 잔여
- **운영 클러스터 VM**: prod 전용 VM 미생성(stg 폐기). master=prod 실 클러스터 위치 = VM 이전 시점 결정.
- **AUTH 트랙(별개 진행 중, 본 세션 미관여)**: `AUTH_SERVER.md` `NEXT_K8S_REAL_DEPLOY §S3'` 평문 잔여 1줄 + `modules/{AUTH_SERVER,OIDC_HARDENING}.md` `planning/→archive/` 깨진 링크.
- **운영 영속 업로드 미결정**: 현재 /tmp(휘발) — 5단계에서 s3/PVC 확정.

## 이번 섹션 함정/원칙 (되돌리지 말 것 · 상세 PITFALLS §9)
- **env 를 줬다 ≠ 프로퍼티에 바인딩됐다**: kebab-case(`base-path`)의 relaxed binding 정식 env 는 대시 제거형(`BASEPATH`). 매니페스트가 언더스코어형(`BASE_PATH`)을 주면 `base.path` 로 갈려 매칭 보장 안 됨 → application.yml 에 **명시 placeholder** 로 env 명을 고정하라.
- **로그 경로가 진단의 결정타**: file 저장 실패 로그가 `/application/uploads`(기본값)면 env 미바인딩, `/tmp/uploads`면 바인딩됨 — env 주입 여부를 로그 경로로 판별.
- **거짓 기본값 금지**: 모듈(framework-file-s3) 없이 `type=s3` 는 "뜨지 않는 설정". 당장 안 쓰면 type=local 로(또는 모듈 의존 해제 후 s3).
- **소스 변경 = 이미지 재빌드+promote / 매니페스트 변경 = git push 로 ArgoCD 가 읽음**: application.yml 은 이미지에 구워지므로 push 만으론 반영 안 됨 — promote(빌드) 필수.
<!-- 갱신 끝 -->
