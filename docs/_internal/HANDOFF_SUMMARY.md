# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**🟢 5단계 ① DB/role 서비스별 개명 — `sidb→userdb` · `siuser→user_app`(user)/`admin_app`(admin) · `authuser→auth_app`, owner 도 각 짝(최소권한). 코드·매니페스트·현행 가이드 전면 개명 + 정합성 정적검증 전체 PASS(2026-06-08, devops).** 배경: user↔admin 가 공유하던 `sidb`/`siuser`(§9 Flyway checksum 충돌의 근원)를 서비스별 전용 DB·계정으로 분리 → 금융권 최소권한 + 공유 충돌 **설계 단계 원천 차단**. prod-kind 의 단일 `siuser`(authdb/sidb/admindb 통합)도 3 role 로 분리 정합. 비번 정책 = dev/local `dev-{auth,user,admin}pass` · prod-kind `{auth,user,admin}_app_pw`(서비스별). **검증 PASS**: secrets `DB_USER/DB_PASSWORD` ↔ initdb role/PW 일치(dev/local 각 3계정) · initdb owner↔role 짝(local/persistent) · prod-kind initdb↔35-seed 비번 일치 · base configmap DB_URL(user=userdb/admin=admindb) · YAML 유효성 10파일 · 비문서 잔존 0(36 의 "과거/전환 설명" 주석만 의도적 보존).

## 최종 갱신
- 일자: 2026-06-08 · 갱신자: 세션(5단계 ① DB/role 서비스별 개명)
- 대상 브랜치: master · 환경: 프레임워크 소스 무변경(서비스 application*.yml·build.gradle 의 DB명/계정 + 매니페스트 + 스크립트 + 문서). **drop-in zip 전달, Chae 가 적용·커밋. ⚠️ 구 `36-reset-sidb-rehearsal.sh` 는 `git rm` 필요(rename — drop-in 은 덮어쓰기만).**

## 직전에 한 것 (Done)
| 항목 | 산출/검증 |
|---|---|
| initdb 4종 | compose/local/persistent/prod-kind — role 3분리(auth_app/user_app/admin_app) + DB authdb/userdb/admindb + owner 짝. |
| secrets/configmap | dev/local secrets DB_USER/PASSWORD 서비스별 분리 · base configmap user=userdb·admin=admindb(거짓 기본값 교정). |
| compose/overlay | docker-compose DB_URL/USER/PASSWORD 서비스별 · dev/prod overlay user DB_URL→userdb · 주석 정합. |
| prod-kind 스크립트 | 35-seed 서비스별 계정(AUTH/USER/ADMIN_DB_USER+PW) · 36 용도변경(오염정리→userdb 리허설 초기화, `36-reset-userdb-rehearsal.sh`) · 01/03 DB 검증목록. |
| 서비스 소스 | user(→userdb/user_app)·admin(→admindb/admin_app)·auth(→auth_app) application*.yml + build.gradle(flyway.url/user) + framework-datasource javadoc. |
| 현행 가이드 | deploy README 3 · docs/ops 6 · SAMPLES · AUTH_SERVER · services README 3 개명. |
| 정합성 검증 | ✅ PyYAML 전체 PASS(secrets↔initdb↔owner, prod 비번, configmap DB_URL, YAML 유효성). |
| 작업기록 | PITFALLS §9 개명 교훈 1건 추가 + 인라인 보정 · SUMMARY · planning 36 파일명 · HANDOFF 누적 엔트리. |

## 현재 상태 (적용/검증)
- **개명 완료(정적)**: 비문서 영역 sidb/siuser/authuser 잔존 0(36 설명 주석 제외). 검증 PASS — Chae 환경 적용 대기.
- **DB/계정 토폴로지**: authdb/auth_app · userdb/user_app · admindb/admin_app(서비스별 전용, owner 각 짝). prod-kind 단일 siuser → 3 분리.
- **② file storage(NFS PVC)·③ 관측 분산형 = 미착수**(5단계 잔여 2축).

## 바로 다음 할 일 (Next) — 다음 섹션
1. **기존 클러스터 개명 적용 안내**: initdb 1회성이라 옛 sidb 만 있고 userdb/신 role 자동생성 안 됨 → prod-postgres `01-data-containers.sh` 재실행(컨테이너 재생성, 리허설 데이터 버림) 또는 `36-reset-userdb-rehearsal.sh`. 구 36 `git rm`.
2. **5단계 ② file storage(NFS PVC)**: in-cluster NFS server + nfs-subdir-external-provisioner(`registry.k8s.io/sig-storage/nfs-subdir-external-provisioner:v4.0.2`) + StorageClass `nfs` + RWX PVC(user/admin replicas:2 공유, local-path 는 RWO 불가) + deployment 마운트(/mnt/uploads)+env override(FRAMEWORK_FILE_STORAGE_BASE_PATH)+type=nas. kustomize component(`components/file-storage-nfs/`) 패턴 권장. application.yml 은 env placeholder → 소스변경/promote 불요(매니페스트만). NFS 이미지 pull 리스크(mirror intercept/harbor 신뢰) 확인.
3. **5단계 ③ 관측 분산형**: kind-svc Prometheus agent(remote_write)+base ServiceMonitor 직접 apply, kind-cicd 중앙 Prometheus+Grafana.
4. **6단계 문서 캐스케이드** — `docs/ops/PROD_GITOPS_ARGOCD.md` 신설.

## 빈칸 / 잔여
- **운영 클러스터 VM**: prod 전용 VM 미생성(stg 폐기).
- **AUTH 트랙(별개 진행 중)**: `AUTH_SERVER.md` `NEXT_K8S_REAL_DEPLOY §S3'` 평문 잔여 1줄 + `modules/{AUTH_SERVER,OIDC_HARDENING}.md` 깨진 링크.
- **운영 영속 업로드**: 현재 /tmp(휘발) — ② NFS PVC 로 전환 예정.

## 이번 섹션 함정/원칙 (되돌리지 말 것 · 상세 PITFALLS §9)
- **DB/role 개명 = 단순 sed 금지**: user 맥락 `sidb→userdb` vs admin base configmap 거짓 기본값 `sidb→admindb` 구분 / `siuser` 는 user_app(user)·admin_app(admin) 갈림 / secrets↔initdb role·PW 한 글자라도 어긋나면 연결 실패.
- **개명 적용 ≠ 그냥 sync**: initdb 1회성 → 기존 클러스터는 컨테이너 재생성(01) 또는 36 으로 신 role/DB 보강. claimed-uncommitted ≠ actually — Chae 환경에서 fresh 상태 확인 후 적용.
- **서비스별 전용 DB·계정이 기본값**: 공유 시 §9 Flyway 충돌 — 분리는 설계로 차단됐으니 되돌리지 말 것.
<!-- 갱신 끝 -->
