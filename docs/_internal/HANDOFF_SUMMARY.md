# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)
> **배치·태스크·스케줄(Spring Cloud Task + Batch + Quartz UI) 트랙은 별도 한 장 `HANDOFF_BATCH_SUMMARY.md`(같은 폴더) 참조.**

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**🟢 문서 정리 + 운영(prod) GitOps 리허설 1~3단계 PASS(2026-06-08, devops · kind 멀티클러스터).** ① **문서 정리**: stale 7파일 삭제(루트 `HANDOFF_SUMMARY`/`APPLY_HARBOR_JENKINS` 중복 · `apply-notes/` 3 · `NEXT_K8S_REAL_DEPLOY` · `_PITFALLS_APPEND` 2) + append 2파일을 `PITFALLS.md §8/§9/부록`에 병합 + 끊긴 포인터 정정(INDEX·GAP_AUDIT·FRAMEWORK_MODULES·HARBOR_SETUP). **AUTH 트랙 미관여**(`AUTH_SERVER.md` NEXT_K8S 잔여 1줄 + `planning/→archive/` 깨진 링크는 AUTH 작업 시 정리 권고). ② **prod GitOps 1~3단계 실측 완주** — 새 자산 `deploy/k8s/prod-kind/`(멀티클러스터 리허설 하네스) + `deploy/argocd/`(GitOps 정본). **1단계** kind-cicd(80/443)+kind-svc(8080/8443)+도커 pg/redis(`--network kind`)+CoreDNS DB주입+노드 Harbor신뢰(registry-trust 멀티클러스터판) **G1~G4 PASS**. **2단계** ArgoCD(Helm)+`argocd.local` ingress+kind-svc 선언형 cluster Secret(server=컨테이너 IP, insecure=false 정상) **G5~G7 PASS**. **3단계** AppProject+prod Application(`targetRevision:master`, dest=kind-svc, 자동sync+selfHeal+prune)+app-of-apps → **commit/push → ArgoCD reconcile → kind-svc 에 ns+Deployment 5개 G8~G10 PASS**(파드는 sentinel `__GITSHA__`+Harbor 미설치로 ImagePullBackOff=정상 fail-loud, redis만 Running). **다음 섹션 = 4단계**(Harbor hub + promote 배선). 진입 스펙 = `planning/NEXT_PROD_GITOPS_ARGOCD.md` §3-4.

## 최종 갱신
- 일자: 2026-06-08 · 갱신자: 세션(문서 정리 + prod GitOps 1~3단계)
- 대상 브랜치: master · 환경: 프레임워크/스택 무변경(devops). **`deploy/argocd/` + prod overlay SM 수정은 push 됨**(ArgoCD 가 master 를 읽어야 reconcile). `deploy/k8s/prod-kind/` 스크립트는 받는 쪽 커밋 정리.

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| 문서 정리 | stale 7파일 git rm · PITFALLS append 2→본문 §8/§9/부록 병합 · 끊긴 포인터 4건 정정. AUTH 트랙 미관여(2건 플래그). |
| prod 1단계 | `prod-kind/`: kind-cicd/svc config·`00-up`/`01-data`/`02-coredns`/`03-cross-trust`/`90-verify`. **G1~G4 받는 쪽 PASS**. |
| prod 2단계 | `10-cicd-ingress`·`11-argocd-install`(Helm)·`12-register-svc`(선언형 Secret)·`91-verify`. **G5~G7 PASS** + UI Clusters 에 kind-svc. |
| prod 3단계 | `deploy/argocd/`(project/app/bootstrap)·`20-gitops-bootstrap`·`92-verify`. **G8~G10 PASS**(reconcile→kind-svc). 파드 ImagePullBackOff=정상. |
| 3단계 트리아지 | prod overlay 에 ServiceMonitor `$patch:delete` 누락 → ArgoCD `tasks are not valid` → 추가(dev/local 과 동일). PITFALLS §9 재발 등록. |

## 현재 상태 (적용/검증)
- **prod GitOps 파이프라인**: ✅ 살아 동작 — git(master)=진실, ArgoCD 가 폴링(~3분)해 kind-svc 자동 reconcile. si-msa-prod=Synced, kind-svc=Successful.
- **파드**: redis Running, 4서비스×2 ImagePullBackOff = **설계대로**(promote 전 sentinel + Harbor 미설치). 4단계에서 green.
- **문서**: PITFALLS 병합·정리 완료 · 스펙 1~3단계 ✅ 표기 · INDEX/포인터 정합.
- **커밋**: `deploy/argocd`·`overlays/prod`(SM)·`PITFALLS`·spec push 됨. `prod-kind/` 스크립트는 받는 쪽 커밋.

## 바로 다음 할 일 (Next) — 다음 섹션 = 4단계
> 진입점 = `docs/_internal/planning/NEXT_PROD_GITOPS_ARGOCD.md` §3-4.
1. **Harbor(hub) 설치** — `kind-cicd` 에 Harbor(기존 `08-harbor-install` 멀티클러스터 적응판). 1단계에서 kind-svc 노드가 harbor.local→cicd CP IP 신뢰 배선은 이미 완료 → 실제 pull 검증.
2. **promote 배선** — 불변 `:<sha>` 빌드/push → `overlays/prod` 에 `kustomize edit set image`(name+tag = `harbor.local/si-msa/<svc>:<sha>`) → **git commit/push** → ArgoCD sync → 파드 green. ⚠️ **미정 결정**: 빌드 주체 = 기존 Jenkins(`Jenkinsfile.kind`) 재활용 vs promote 전용(`Jenkinsfile.promote`/bash). hub 에 Jenkins 또 세우면 무거움 → bash promote 로 흐름부터 증명 후 Jenkins 후속 고려(4단계 착수 시 Chae 결정).
3. (이후) 5단계 데이터/관측 정합 · 6단계 문서 캐스케이드(`docs/ops/PROD_GITOPS_ARGOCD.md`).

## 빈칸 / 잔여
- **운영 클러스터 VM**: prod 전용 VM 미생성(stg 폐기). master=prod 실 클러스터 위치 = VM 이전 시점 결정.
- **AUTH 트랙(별개 진행 중)**: `AUTH_SERVER.md` 의 `NEXT_K8S_REAL_DEPLOY §S3'` 평문 잔여 1줄 + `modules/{AUTH_SERVER,OIDC_HARDENING}.md` 의 `_internal/planning/NEXT_{RP_IDTOKEN_LINK,OIDC_ID_TOKEN,SIGNING_KEY_ROTATION,KIND_AUTH_TOKEN_FLOW}.md` 깨진 링크(실제는 `archive/`) — AUTH 작업 시 `planning/`→`archive/` 로 정정.

## 이번 섹션 함정/원칙 (되돌리지 말 것 · 상세 PITFALLS §8/§9)
- **prod overlay 도 ServiceMonitor `$patch:delete` 필요**(dev/local 만 처리됐었음). ArgoCD 증상 = `one or more synchronization tasks are not valid` + health=Missing + conditions 빈칸 **(x509 없음 = 연결 정상 = CRD 문제 단서)**. 옵셔널 operator CRD 의존 리소스는 모든 배포 경로(dev/prod/CI/ArgoCD)에서 코어 overlay 밖으로.
- **ArgoCD git 폴링 ~3분**(즉시 아님). 강제는 `annotate application <app> argocd.argoproj.io/refresh=hard --overwrite`. (sleep 30 은 짧음 — 충분히 대기.)
- **kind 멀티클러스터**: 공유 `kind` 도커망 / 호스트 포트 분배(cicd 80/443·svc 8080/8443, extraPortMappings 생성시 고정) / 파드→클러스터밖 DB = CoreDNS 주입(파드는 도커 임베드 DNS 안 봄) / hub→워크로드 등록 = kubeconfig server 를 **컨테이너 IP** 로(127.0.0.1/노드명 둘 다 파드에서 못 감).
- **kubectl 함정**: `get secret <name> -l <selector>` 동시 불가(이름이냐 셀렉터냐). 짧은 명령 파드 출력은 `run -i --rm` attach 가 놓침 → `run`(detached)+`logs`.
- **commit-first(pull-GitOps)**: ArgoCD 는 로컬 apply 가 아니라 **origin/master** 를 본다. `deploy/argocd`·overlay 변경은 push 가 트리거.


<!-- 갱신 끝 -->
