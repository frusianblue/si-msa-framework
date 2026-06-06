# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**✅ §S3' 2·3단계 PASS, 결정 B 확정, 4단계 산출물 드롭(2026-06-06 세션6).** ② `01-pull-sanity.sh` PASS(node 가 reg.local 직접 pull) → ③ `02-auth-pull-sanity.sh` **PASS**(node=sanity-worker 가 비공개 `harbor.local/si-msa/busybox:authtest` 를 harbor-cred(imagePullSecrets)로 pull — docker-desktop kind 에서 도달층에 막혀 못 봤던 *인증 경로*가 standalone 에선 닫힘) → **결정 B**(인증 레지스트리로 충분, Harbor 제품 미설치) → ④ `03-dev-overlay-up.sh` 드롭: `docker compose build`→`localhost:5443/si-msa/<svc>:dev` push(노드는 harbor.local pull)→`kubectl apply -k overlays/dev`→postgres/redis/4앱 rollout→6파드 Ready·앱 Pulled>0·authdb/sidb/admindb 검증, `--smoke` 면 시드 클라이언트+demo-service client_credentials access_token. **prod 프로파일 그린 전제 레포 확인됨**(ProdAuthenticatorConfig·AS actuator permitAll·user/admin framework-redis·REDIS_HOST·DB/admindb 패치·파일저장 /tmp). **standalone kind=NetworkPolicy 비집행(kindnet)** → base default-deny 가 apps→postgres 안 막음(docker-desktop kind 와 결정적 차이). **다음: 받는 쪽이 `03-dev-overlay-up.sh [--smoke]` 실행.**

## 최종 갱신
- 일자: 2026-06-06 · 갱신자: 운영 리허설 세션6(02 PASS·B 결정·4단계 빌드/push/apply 스크립트)
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / SF7 / SS7 / SC 2025.1.1 / Jackson 3 — 스택 무변경(devops 스크립트/문서만).

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| §S3' 3단계 PASS | `02-auth-pull-sanity.sh` 실행 → ✅ node 가 비공개(Basic auth) harbor.local 레지스트리를 harbor-cred 로 pull. 인증 경로 실증. registry:2 컨테이너 2개(kind-registry=01 무인증/reg.local, harbor-auth-reg=02 인증/harbor.local)는 정상 — 01 것은 정리 가능. |
| 결정 B 확정 | 4단계 레지스트리 = 02 의 인증 레지스트리로 충분(프레임워크 검증 목적). Harbor 제품 미설치. |
| §S3' 4단계 드롭 | `03-dev-overlay-up.sh`(빌드 compose→push localhost:5443→dev apply→6파드/DB 검증, `--smoke`=AS 토큰). 정적검증 통과(bash -n). prod 그린 전제 레포 확인(ProdAuthenticatorConfig/actuator permitAll/framework-redis 등). |
| 문서 동반 | NEXT(S3' B 결정·4단계·prod 그린 전제) · README(03 추가) · HANDOFF_SUMMARY. |
| **4단계 실행 중 결함 수정** | dev apply 시 `serviceaccounts "default" already exists`(fresh ns 레이스) → harbor-cred 미부착 → 앱 ImagePullBackOff(401). **수정**: `pull-secret-dev.yaml` 의 default SA 변경 제거 + `kustomization.yaml` 에 imagePullSecrets 를 앱 파드 템플릿(component=service)에 직접 주입하는 patch 추가. PITFALLS §9 기록. |

## 현재 상태 (적용/검증)
- **클러스터**: standalone `kind-sanity` 3노드. 01·02 PASS. dev overlay 는 아직 미apply(받는 쪽 03 실행).
- **레지스트리**: `harbor-auth-reg`(harbor.local, htpasswd admin/Harbor12345) 가동. `kind-registry`(01 무인증) 잔존 — 정리 가능.
- **이미지**: 실 si-msa/<svc>:dev 는 03 실행 시 빌드→push. dev overlay 핀=`:dev`.

## 바로 다음 할 일 (Next)
1. **SA 수정본 재적용**: unzip 후 `bash deploy/k8s/standalone-kind/03-dev-overlay-up.sh --smoke` 재실행(imagePullSecrets 템플릿 패치 → apply 가 자동 rollout → 신규 파드가 harbor-cred 로 pull). 즉시 우회: `kubectl -n si-msa patch sa default -p '{"imagePullSecrets":[{"name":"harbor-cred"}]}'` + `rollout restart deploy/{gateway,auth-server,user-service,admin-service}`.
2. **그린 확인**: 6파드 Ready + 앱 Pulled>0 + authdb/sidb/admindb + access_token. FAIL 시 PITFALLS §9 자가진단(initdb/redis/Authenticator 등).
3. 그린이면 **S4 애드온**(metrics-server/HPA → kube-prometheus-stack) → S5 prod-rehearsal → S6 상위 흐름(OIDC RP·이중 발급기) → S7 Jenkins(sha 핀 자동 주입, `kustomize edit set image`).

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **인증 노드 pull = imagePullSecrets(kubelet) + certs.d(도달층) 둘 다** — harbor-cred docker-server 키(harbor.local)가 이미지 레지스트리명과 일치해야 kubelet 이 cred 선택. http Basic 은 hosts.toml 이 http://.
- **standalone kind(kindnet)=NetworkPolicy 비집행** — base default-deny 무력(apps→postgres OK). docker-desktop kind(집행 CNI)와 정반대. Connect timed out/08001 나오면 그때만 allow-postgres 추가.
- **dev overlay=prod 프로파일** — ProdAuthenticatorConfig(@Profile("!local"))·AS /actuator permitAll·user/admin framework-redis 의존·seed 비번 {bcrypt} 가 전제(레포 확인됨).
- **호스트 push(localhost:5443) ≠ 노드 pull(harbor.local)나 리포지토리 경로 동일=같은 블롭** — overlay 핀 harbor.local 무수정으로 붙음.
- **이론 맹신 금지(유지)** · **ArgoCD/GitOps ≠ pull**(pull 은 언제나 노드 containerd).

<!-- 갱신 끝 -->
