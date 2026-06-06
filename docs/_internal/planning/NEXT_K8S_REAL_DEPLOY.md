# NEXT_K8S_REAL_DEPLOY.md — 실배포 검증 + 애드온 (착수 설계 노트, 2026-06-06)

> 선행: kind 첫 배포 ✅(`overlays/local` 6파드 `1/1 Running`) + DbAuthenticator 토큰 플로우 ✅. **이 스펙은 그 위에서 "운영 수준"으로 끌어올린다.**
> 전체 맥락 `../HANDOFF.md`, 갭 출처 `GAP_AUDIT.md`(§k8s 실배포 검증 범위), 절차 `../../ops/LOCAL_K8S_TEST.md`·`../../ops/K8S_ADDONS.md`.
> 공통 제약: 작성 환경은 Maven Central/배포서버 차단 → 빌드·`kubectl`·`helm` 직접 실행 불가. 매니페스트는 정적(렌더/구조) 검증만, 실 apply·파드 로그는 받는 쪽.

---

## 0. 현재 사실 (왜 "kind ✅"가 곧 "운영 준비"는 아닌가)

| 항목 | 현 상태 | 운영 부적합 사유 |
|---|---|---|
| 이미지 태그 | `si-msa/<svc>:local` + `imagePullPolicy: IfNotPresent` | **가변 태그**(`:local` 재빌드 시 같은 태그 다른 digest → 노드 stale 재사용, PITFALLS §9). 실 레지스트리 아님 |
| 볼륨 | 업로드/로그 `/tmp`(emptyDir 성격), postgres `redis` 자체호스팅 | **비영속** — 파드 재시작/리스케줄 시 유실. 운영=PVC/외부 매니지드 |
| overlays | `local`만 실배포 검증 | `dev`/`prod` 는 렌더/`--dry-run=server`까지만 |
| 애드온 | 매니페스트/가이드만 | metrics-server·kube-prometheus-stack·ingress-nginx **클러스터 실증 0**(local 은 ServiceMonitor 제거) |
| 상위 흐름 | e2e/JDK 레벨 | OIDC RP·SAML·SLO·이중 발급기·webauthn 의 **kind 실배포 위 스모크 미실시** |

## 1. 작업 갈래 (권장 순서)

### 1-A. 이미지 태그 정책 (운영 정합) — 선행 권장
- **불변 태그**로 전환: `:local` → `:${GIT_SHA}`(또는 semver). 재태깅으로 같은 digest 보장.
- **실 레지스트리** 전제: overlays 의 `images.newName` 을 사내 레지스트리(`registry.example.com/si-msa/<svc>`)로, `imagePullPolicy: IfNotPresent`(불변태그면 안전) 유지하되 `imagePullSecrets` 추가.
- dev/prod overlay 의 `images` 블록에 `newTag: <불변>` 패치(kustomize `images:`).
- ⚠️ 결정 필요: ① 태그 소스(CI 의 `GIT_SHA` vs 릴리스 semver) ② 레지스트리 좌표/시크릿 주입 방식(외부 시크릿 오퍼레이터 연동 여부). CI 연동은 `deploy/cicd/Jenkinsfile`·`ci-cd.yml` 와 정합.

### 1-B. 볼륨/영속 (PVC)
- **업로드 경로**: 현재 `FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads`(emptyDir). user/admin 업로드 영속이 필요하면 **PVC**(`ReadWriteOnce`/`ReadWriteMany` 결정) 또는 **file 모듈 off + S3**(framework-file-s3, 권장 — 운영은 객체스토리지). 멀티파드 RWX 회피하려면 S3 우선.
- **postgres**: 현재 `base/redis/redis.yaml`·postgres 자체호스팅(예시). 운영=**외부 매니지드 DB**(RDS/CloudSQL/사내) 권장 → overlay 에서 `DB_URL` 만 교체, in-cluster postgres 매니페스트는 local 한정.
- **redis**: 동일 — 운영은 매니지드/HA, in-cluster 는 local.
- **서명키(auth-server)**: 현재 DB 영속(`signing_key` 테이블, AES 암호화). PVC 불요. (A3 KMS/Vault 는 별건.)
- ⚠️ 결정 필요: 업로드 영속을 PVC vs S3 중 무엇으로 — 멀티파드면 S3 가 단순.

### 1-C. dev/prod overlay 실 apply 검증
- `kubectl kustomize deploy/k8s/overlays/{dev,prod}` 렌더 → `--dry-run=server` → 실 apply.
- 확인 포인트: prod 시크릿 주입(`secrets-prod.example.yaml` → 실 시크릿/오퍼레이터), `hpa.yaml`(metrics-server 선행), `ingress-prod.yaml`(TLS/실도메인), 이미지 불변태그(1-A).

### 1-D. 애드온 클러스터 실증 (`K8S_ADDONS.md` 매핑)
- **metrics-server** — HPA 전제. 설치 후 `kubectl top` → `hpa.yaml` 동작 확인.
- **kube-prometheus-stack**(Helm) — ServiceMonitor CRD 생성 후 overlay 에서 SM 복원(local 은 제거 상태) → `/actuator/prometheus` 스크랩 확인.
- **ingress-nginx**(선택) — `base/common/ingress.yaml`(host `si-msa.example.com`) 동작 → port-forward 대체.
- 순서: metrics-server → (관측) kube-prometheus-stack → (외부노출) ingress-nginx.

### 1-E. 상위 인증 흐름 kind 스모크 (port-forward)
- AS authorization_code+PKCE(이미 ✅) 외에: OIDC RP 콜백·SAML SP(IdP 메타데이터 필요)·이중 발급기(게이트웨이 `iss` 분기)·webauthn(HTTPS/SecureContext 전제) 을 실배포 위에서 1회씩 스모크. SAML/webauthn 은 외부 IdP/HTTPS 전제라 환경 준비 필요 → 우선순위는 OIDC RP·이중 발급기.

## 2. 완료 정의 (Done)
- 불변 이미지 태그 + 실 레지스트리 좌표로 overlays 렌더 정합(1-A).
- 업로드 영속 방식 결정·반영(PVC or S3) + 운영 DB/redis 외부화 가이드(1-B).
- dev/prod overlay 실 apply 그린(1-C).
- 애드온 3종 중 최소 metrics-server+HPA 실증, (관측) Prometheus 스크랩 확인(1-D).
- 동반 문서: `K8S_ADDONS.md`·`LOCAL_K8S_TEST.md`(or 신규 `PROD_DEPLOY.md`) + `GAP_AUDIT` §k8s 범위 갱신 + `PITFALLS`(태그/볼륨 함정).

## 3. 관련 좌표
- `deploy/k8s/base/*`(deployment/service/configmap), `overlays/{local,dev,prod}/*`(images/secrets/hpa/ingress 패치).
- `deploy/cicd/Jenkinsfile`·`ci-cd.yml`(태그 소스 연동), `deploy/docker/Dockerfile`(운영 이미지).
- `framework-file-s3`(업로드 S3 대안), auth-server `signing_key`(키 영속).
