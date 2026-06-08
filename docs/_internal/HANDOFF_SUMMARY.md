# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**🟢 5단계 데이터/파일 정합 — ① DB/role 서비스별 개명(검증 PASS) + ② 파일 영속 NFS RWX(산출 완료·적용 대기)(2026-06-08, devops, 프레임워크 소스 무변경).** ① `sidb→userdb`·`siuser→user_app/admin_app`·`authuser→auth_app`(owner 각 짝, 최소권한) — 공유 sidb Flyway 충돌(§9)을 설계 단계 원천 차단, prod-kind 단일 siuser→3 role 분리. ② user/admin 업로드를 `/tmp`(휘발 emptyDir) → NFS PVC(`/mnt/uploads`, RWX)로: `13-nfs-provisioner.sh`(in-cluster ganesha nfs-server-provisioner, StorageClass `nfs`) + `components/file-storage-nfs`(PVC×2 RWX + deployment patch). **application.yml 이 env placeholder(`${FRAMEWORK_FILE_STORAGE_BASE_PATH}`/`${FILE_STORAGE_TYPE}`)라 소스변경·재빌드·promote 불요** — 13 실행 + ArgoCD sync 만으로 휘발→영속 전환. **검증**: ① PyYAML 전체 PASS(secrets↔initdb↔owner·prod 비번·configmap DB_URL·YAML 10) · ② 구조 PASS(Component kind·PVC RWX/sc nfs·patch env override·prod overlay 참조). kustomize build 는 형님 환경 `kubectl kustomize` 위임.

## 최종 갱신
- 일자: 2026-06-08 · 갱신자: 세션(5단계 ① DB/role 개명 + ② 파일 영속 NFS)
- 대상 브랜치: master · 환경: 프레임워크 소스 무변경(매니페스트·스크립트·서비스 설정·문서). **drop-in zip. ⚠️ 구 `36-reset-sidb-rehearsal.sh` 는 `git rm`(rename).**

## 직전에 한 것 (Done)
| 항목 | 산출/검증 |
|---|---|
| ① DB/role 개명 | initdb 4종(role 3분리+DB+owner) · secrets/configmap/compose/overlay · 35-seed 서비스별 · 36 용도변경(userdb 리허설) · 서비스 소스·현행 가이드. PyYAML 정합 PASS. |
| ② NFS provisioner | `13-nfs-provisioner.sh` — kind-svc 애드온, nfs-server-provisioner(ganesha built-in, RWX), StorageClass `nfs`. bash -n PASS. |
| ② file-storage component | `components/file-storage-nfs`: PVC×2(user-uploads/admin-uploads, RWX) + user/admin deployment patch(/mnt/uploads 마운트·`FRAMEWORK_FILE_STORAGE_BASE_PATH` override·`FILE_STORAGE_TYPE=nas`). prod overlay `components:` 끼움. 구조 PASS. |
| 문서 | PITFALLS §9 2건(개명·NFS RWX) · prod-kind README(13 시퀀스+5단계②) · SUMMARY · planning · HANDOFF. |

## 현재 상태 (적용/검증)
- **① 개명: 정적 PASS, 적용 대기**(비문서 잔존 0). **② 파일 영속: 산출 완료, 형님 PASS 게이트 대기**(helm install·PVC Bound·파드 재기동).
- **③ 관측 분산형 = 미착수**(5단계 잔여 1축).

## 바로 다음 할 일 (Next) — 다음 섹션
1. **② 적용·검증(형님 환경 PASS 게이트)**: `kubectl kustomize overlays/prod` 렌더 확인 → `helm show values`로 nfs-server-provisioner values 키 확인(편차 대비) → `bash 13-nfs-provisioner.sh`(SC `nfs`) → ArgoCD sync → `kubectl -n si-msa get pvc`(user-uploads/admin-uploads Bound) → 파드 재기동 후 `/mnt/uploads` 마운트·업로드 영속 확인. ⚠️ provisioner 이미지 노드 pull(mirror intercept §8) 점검.
2. **① 적용**: `unzip -o` + `git rm` 구 36. 기존 클러스터는 initdb 1회성 → `01-data-containers.sh` 재실행 또는 `36-reset-userdb-rehearsal.sh`.
3. **5단계 ③ 관측 분산형**: kind-svc Prometheus agent(remote_write)+base ServiceMonitor 직접 apply, kind-cicd 중앙 Prometheus+Grafana.
4. **6단계 문서 캐스케이드** — `docs/ops/PROD_GITOPS_ARGOCD.md` 신설.

## 빈칸 / 잔여
- **② 미검증**: helm chart values 키(`storageClass.name` 등)·provisioner 이미지 버전은 형님 환경 확인 필요(버전 날조 금지 — chart default + helm show values).
- **운영 클러스터 VM**: prod 전용 VM 미생성.
- **AUTH 트랙(별개)**: `AUTH_SERVER.md` 평문 잔여 1줄 + `modules/*` 깨진 링크.

## 이번 섹션 함정/원칙 (되돌리지 말 것 · 상세 PITFALLS §9)
- **개명 = 단순 sed 금지**: user `sidb→userdb` vs admin base configmap 거짓 기본값 `sidb→admindb` / `siuser`=user_app·admin_app / secrets↔initdb 정합 필수. initdb 1회성 → 기존 클러스터 재생성.
- **파일 RWX 필수**: replicas:2 공유엔 RWX(NFS), RWO(local-path)는 단일 파드만. in-cluster ganesha(userspace)로 커널 nfsd 회피(WSL2). hardening `/tmp/uploads` → component `/mnt/uploads` override(kustomize 순서상 component 승리). 휘발은 리허설, 운영은 관리형 NFS/EFS/S3.
- **env placeholder 설계의 보상**: application.yml 이 env placeholder 라 스토리지 전환이 매니페스트만으로(소스변경/promote 불요) — 직전 세션의 "명시 placeholder" 결정이 이번에 회수됨.
<!-- 갱신 끝 -->
