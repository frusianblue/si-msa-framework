# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**🚀 S3 dev overlay apply용 매니페스트 완성(2026-06-06 세션3) — 정적검증 통과, 받는 쪽 `apply -k` 대기.** `overlays/dev` 를 운영 리허설용으로 재작성: ① 이미지 핀 `harbor.local/si-msa/<svc>:7e935d6`(newName+newTag ×4) ② DB→인-클러스터 영속 `postgres:5432`(auth=authdb·user=sidb·**admin=admindb** ← 기존 `sidb` 는 user 와 Flyway V1 충돌 잠재버그라 정정) + `resources` 에 `components/postgres-persistent` ③ 파일저장 `/tmp/uploads`(이 환경 S3 없음+readOnlyRootFilesystem → local overlay 미러, 안 하면 user/admin 부팅 깨짐) ④ `pull-secret-dev.yaml`(harbor-cred dockerconfigjson + default SA 부착) = **한 방 `apply -k`**. 핀 `7e935d6`≠HEAD `5d2dc2c` 지만 사이 2커밋 **문서만**(앱동작 동일·`SmokeClientSeeder` 포함 확인). kustomize 바이너리 미수급(release-asset 도메인 차단) → PyYAML 정적검증(리소스 35·패치타깃·이미지매칭·JSON6902 경로·secretRef/SA) **전부 OK**. `harbor-push.sh` 기본 `REGISTRY` 를 폐기된 NodePort→`harbor.local` 로 정정. **다음 = 받는 쪽 apply + pull 트리아지(인증/이름해소 2층) + AS 토큰 스모크(리허설 한정 seed 옵트인).**

## 최종 갱신
- 일자: 2026-06-06 · 갱신자: 운영 리허설 세션3(S3 매니페스트)
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / SF7 / SS7 / SC 2025.1.1 / Jackson 3 — 스택 무변경(devops 매니페스트·문서만).

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| **S3 dev overlay 매니페스트** ◐ | `overlays/dev/kustomization.yaml` 재작성(이미지 핀 7e935d6 + DB→postgres + admin=admindb 정정 + 파일저장 /tmp/uploads + postgres-persistent resources) + `overlays/dev/pull-secret-dev.yaml`(harbor-cred + default SA). PyYAML 정적검증 통과. apply 는 받는 쪽. |
| harbor-push 정정 | `deploy/cicd/harbor-push.sh` 기본 `REGISTRY` localhost:30002(폐기 NodePort)→`harbor.local`(ingress) + 헤더 주석 정정. |
| 문서 | `NEXT_K8S_REAL_DEPLOY.md`(S3 매니페스트 완료·런북 4단계·인벤토리) + `PITFALLS.md`(+private Harbor pull: imagePullSecrets+SA, 인증/이름해소 2층). |

## 현재 상태 (적용/검증)
- **클러스터**: `docker-desktop`(v1.34.3). ns `si-msa`/`harbor`/`ingress-nginx`. S1(영속 postgres PVC Bound)·S2(Harbor 4×2 태그 push) 적용·검증 완료.
- **S3 매니페스트**: master 트리에 완성·정적검증 통과. **아직 apply 안 함** → 앱 4파드·Harbor Pull 수 0 그대로.
- **이미지 git-sha**: 핀 `7e935d6`(소스 HEAD=`5d2dc2c`, 사이 문서만).

## 바로 다음 할 일 (Next)
1. **(섹션 시작 시) 재부팅 검증** — Docker Desktop 재기동 후 ingress/LB·Harbor·postgres PVC 자동 복귀(port-forward 졸업).
2. **S3 apply** — `kubectl apply -k deploy/k8s/overlays/dev` → `get pods -w`. **pull 트리아지**: Events 가 `unauthorized`면 harbor-cred/SA(신규 파드부터 적용), 이름해소/연결이면 노드 hosts.toml(또는 자동노출) — `K8S_INGRESS_HARBOR.md` §6.
3. **AS 토큰 스모크**(리허설 한정): `set env deploy/auth-server FRAMEWORK_AUTH_SEED_SMOKE_CLIENT=true` + `rollout restart` → demo-web PKCE/demo-service cc 토큰 확인. **그린 = 6파드 Ready + Harbor Pull>0 + 토큰 스모크.**
- 이후: S4 애드온(metrics-server/HPA·kube-prometheus-stack) → S5 prod-rehearsal overlay → S6 상위 흐름 스모크 → S7 Jenkins.

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **dev overlay 는 base 의 prod 기본값(s3 파일저장·admin=sidb·외부 DB)을 그대로 두면 이 환경에서 부팅 불가** — local 에서 검증된 패치(admindb 분리·/tmp/uploads·인-클러스터 postgres)를 리허설 overlay 에도 미러해야 함.
- **비공개 레지스트리 pull = 인증(imagePullSecrets) + 노드 이름해소 두 층** — 한쪽만 풀면 여전히 `ErrImagePull`. SA imagePullSecrets 는 신규 파드부터 적용.
- **이미지 핀(7e935d6)과 소스 HEAD 가 어긋날 수 있음** — 차이가 코드/매니페스트면 재빌드·재푸시 필요, 이번엔 문서만이라 안전. 핀 ↔ HEAD diff 확인 습관.
- **kustomize 바이너리 미수급 환경에선 PyYAML 정적검증으로 대체**(리소스/패치타깃/이미지/JSON6902/ref) — 빌드 차단 시 정석.
- **상태는 master 트리/클러스터 실거동 우선** — 스펙 배너보다 `get pods`/`describe`/포털 실측.

<!-- 갱신 끝 -->
