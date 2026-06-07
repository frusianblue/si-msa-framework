# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**🟢 운영(prod) 프로비저닝 설계 잠김 + prod 가변태그 청산(2026-06-08, devops · 대화 확정, 구현은 다음 섹션).** 운영을 **ArgoCD GitOps(pull)** 로 가기로 확정 — 매니페스트(kustomize)는 무변경이고 바뀌는 건 운영 레이어(드리프트 자동교정·CI 자격증명 분리·git=진실·sync 감사)뿐. **`master` 머지 = 운영 즉시반영**(prod Application `targetRevision: master`, 자동sync). **토폴로지 = 역할별 3클러스터** cicd(hub: Harbor·Jenkins·ArgoCD) / 데이터(PG·Redis, **K8s 밖 독립** = 현장 정석) / 서비스(stateless 4앱). kind 리허설 = `kind-cicd`+`kind-svc`+**우분투 도커 postgres/redis(`--network kind`)**. **stg 제거**, develop·dev 는 운영 완주 후. 이번 적용 산출 = **prod overlay `:latest`×4 → 커밋식 `__GITSHA__`**(`Always` 패치 제거→base IfNotPresent 상속, GitOps 전제 주석). **다음 섹션은 전부 prod 기준 · 무조건 인프라부터.** 진입 스펙 = `planning/NEXT_PROD_GITOPS_ARGOCD.md`.

## 최종 갱신
- 일자: 2026-06-08 · 갱신자: 세션(운영 설계 확정 + prod overlay 전환)
- 대상 브랜치: master · 환경: 프레임워크/스택 무변경(devops). **이번 산출(prod overlay + 문서) 미커밋 — 받는 쪽 적용/commit/push.**

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| 운영 설계 확정 | ArgoCD GitOps · master=즉시반영 · 3클러스터 분리 · DB=K8s밖 · stg제거 — `NEXT_PROD_GITOPS_ARGOCD.md` 에 잠금. |
| prod 가변태그 청산 | prod kustomization `:latest`→`__GITSHA__`(커밋식 핀), `imagePullPolicy: Always` 패치 제거. PyYAML 검증 OK. |
| 문서 | PITFALLS §9 신규 3건(push↔pull 핀 반전·멀티클러스터 배치·DB는 K8s밖) · HANDOFF §7 항목 추가 · 이 SUMMARY. |

## 현재 상태 (적용/검증)
- **설계**: ✅ 잠김(대화 확정). 구현 0 — 다음 섹션 인프라부터.
- **prod overlay**: ✅ 가변태그 청산(정적 검증). dev 핀 모델과 **반대**(prod=커밋, dev=워크스페이스). 미커밋.
- **문서**: PITFALLS §9 누적 · HANDOFF §7 갱신 · planning 스펙 신설.
- **커밋**: 이번 산출(prod overlay + planning + PITFALLS + HANDOFF + 이 SUMMARY) **미커밋** — 받는 쪽 `unzip -o` 적용 후 commit/push.

## 바로 다음 할 일 (Next) — 다음 섹션 = prod, 무조건 인프라부터
> 진입점 = `docs/_internal/planning/NEXT_PROD_GITOPS_ARGOCD.md` §3.
1. **인프라 토대** — kind 2클러스터(`kind-cicd`+`kind-svc`) 생성 스크립트 + 우분투 도커 postgres/redis(`--network kind`) 기동·연결. 클러스터간 네트워크·노드 Harbor 신뢰·서비스→DB 도달 PASS 게이트.
2. **ArgoCD(hub)** — `kind-cicd` 설치(`argocd.local` B안 ingress) + `kind-svc` 원격 등록(kubeconfig server=컨테이너 IP).
3. **GitOps 자산** — `deploy/argocd/`(AppProject + prod Application `targetRevision:master` + app-of-apps bootstrap).
4. **promote 배선** — `Jenkinsfile.promote`: CD 의 불변 `:<sha>` → prod overlay `kustomize edit set image` + **git commit/push** → ArgoCD sync.
5. **데이터/관측 정합** — DB/Redis=K8s밖 엔드포인트, 관측 분산형(워크로드 agent+중앙 Grafana).
6. **문서** — `docs/ops/PROD_GITOPS_ARGOCD.md` + branch-per-env 가이드(master=prod 레퍼런스 + release-태그 대안).

## 빈칸 / 잔여
- **운영 클러스터 VM**: cicd/dev VM 은 있으나 **prod 전용 미생성**(stg 폐기). master=prod 실 클러스터 위치 → VM 이전 시점 결정.
- **문서 잔여물 정리(2026-06-08 완료)**: `docs/_internal/apply-notes/`(3파일) · 루트 `HANDOFF_SUMMARY.md`/`APPLY_HARBOR_JENKINS.md`(stale 중복) · `NEXT_K8S_REAL_DEPLOY.md`(docker-desktop kind 전제 은퇴) → **삭제 완료**. PITFALLS append 2파일 → 본문 §8/§9/부록 병합 후 삭제. INDEX·GAP_AUDIT·FRAMEWORK_MODULES·HARBOR_SETUP 끊긴 포인터 정정. (AUTH 트랙 문서는 미관여 — `AUTH_SERVER.md` 의 `NEXT_K8S_REAL_DEPLOY §S3'` 잔여 1줄은 AUTH 작업 시 정리 권고.)

## 이번 섹션 함정/원칙 (되돌리지 말 것 · 상세 PITFALLS §9)
- **dev=push-CD(워크스페이스 핀, 되커밋X) ↔ prod=pull-GitOps(커밋 핀)**. ArgoCD 진실=git 커밋 상태 → prod 는 핀을 커밋해야 함(dev 규칙 반전). sentinel 기본값=fail-loud(공통).
- **ArgoCD ≠ pull** — 노드→레지스트리 pull 은 인프라 레이어(클러스터마다 Harbor 신뢰). CD 는 apply 만.
- **클러스터는 폭발반경/라이프사이클로 가른다**(CI/CD·데이터·워크로드). 운영 애드온은 그 기능 필요한 클러스터에. **DB 는 K8s 밖이 기본값**(현장 정석, 운영부담 아님).
- **kind 데이터 = 도커 컨테이너 `--network kind`**(클러스터로 안 뺌). hub→워크로드 = kubeconfig server 를 컨테이너 IP 로.
- **다음 섹션 전부 prod 기준 · 인프라부터**(경험먼저로 새다 되돌리는 비용 회피).


<!-- 갱신 끝 -->
