# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**🚀 운영 토폴로지 로컬 리허설 착수 — S1 영속 postgres(PVC) ✅ + S2 Harbor 이미지 푸시 ✅(2026-06-06 세션2).** 현재 Docker Desktop(`docker-desktop` 컨텍스트) 환경에서 운영 경로 전부를 리허설하는 트랙 시작. **S1**: `components/postgres-persistent`(StatefulSet, 기본 StorageClass 동적 PVC) → `data-postgres-0` Bound·`postgres-0` Running(Service headless→ClusterIP 정정). **S2**: NodePort 가 이 환경에서 호스트/데몬 비노출이라 **ingress-nginx(LoadBalancer)+Harbor `expose.type=ingress`(`harbor.local`)** 경로로 전환 → `si-msa` 프로젝트에 4서비스 × (`7e935d6`,`dev`) push 확인. **다음 = S3 dev overlay 실 apply(이미지=Harbor:7e935d6 + DB=인-클러스터 postgres + imagePullSecrets + pull 측 검증).** 또한 직전 세션의 문서 정합화(GAP_AUDIT 재감사: 보충 완료=A1·A4·A9, 남음=A2·A3·A5~A8·B) 반영됨.

## 최종 갱신
- 일자: 2026-06-06 · 갱신자: 운영 리허설 세션2(S1·S2)
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / SF7 / SS7 / SC 2025.1.1 / Jackson 3 — 스택 무변경(devops 매니페스트·문서만).

## 직전에 한 것 (Done)
| 단계 | 산출/검증 |
|---|---|
| **S1 영속 postgres** ✅ | `deploy/k8s/components/postgres-persistent/{postgres.yaml,kustomization.yaml}`. apply → PVC `data-postgres-0` **Bound**(5Gi/RWO/`standard`/local-path), `postgres-0` Running. Service=ClusterIP(headless 시도 시 clusterIP 불변 충돌 → 정정). |
| **S2 Harbor push** ✅ | `docs/ops/K8S_INGRESS_HARBOR.md`(ingress-nginx LoadBalancer + Harbor ingress `harbor.local`) + `deploy/cicd/harbor-push.sh`(양태그). 포털 `si-msa` 4×2 태그(`7e935d6`,`dev`) 확인. `HARBOR_SETUP.md` 는 NodePort 전제 폐기 배너+포인터로 정정. |
| 스펙 갱신 | `NEXT_K8S_REAL_DEPLOY.md` 진행현황(S1·S2 ✅, S3 다음) + 세션2 함정 3건 기록. |

## 현재 상태 (적용/검증)
- **클러스터**: `docker-desktop`(노드 desktop-control-plane/worker×2, v1.34.3). ns `si-msa`(앱), `harbor`(Harbor), `ingress-nginx`(컨트롤러, EXTERNAL-IP=localhost).
- **Harbor**: `harbor.local`(ingress, HTTP). admin/Harbor12345. project `si-msa`(비공개). 4서비스 push 완료, **Pull 수 0**(아직 클러스터 미당김 — S3 에서 >0 되면 pull 경로 실증).
- **이미지 git-sha**: `7e935d6`. dev overlay 핀 대상.

## 바로 다음 할 일 (Next)
1. **(섹션 시작 시) 재부팅 검증** — Docker Desktop 껐다 켠 뒤 ingress/LB·Harbor·postgres PVC 자동 복귀 확인(= port-forward 졸업 확인).
2. **S3 dev overlay 실 apply** — ① `overlays/dev` 이미지 핀 `harbor.local/si-msa/<svc>:7e935d6` ② DB_URL `dev-postgres.internal` → 인-클러스터 `postgres:5432`(auth=authdb·user=sidb·admin=admindb) + `resources` 에 `components/postgres-persistent` ③ `harbor-cred` 시크릿 + default SA 부착 ④ apply 후 **pull 트리아지**(노드 containerd 가 `harbor.local` 못 풀면 hosts.toml; 이 환경 자동노출로 생략될 수도 — `describe pod` Events 로 판단). 그린=6파드 Ready + Harbor Pull>0 + AS 토큰 스모크.
- 이후: S4 애드온(metrics-server/HPA·kube-prometheus-stack) → S5 prod-rehearsal overlay → S6 상위 흐름 스모크 → S7 Jenkins.

## 이번 세션 함정/원칙 (되돌리지 말 것)
- **이 환경 NodePort=호스트/데몬 비노출, LoadBalancer 만 localhost 매핑.** `kubectl port-forward` 는 127.0.0.1 만 바인딩 → **Docker Desktop VM 데몬이 못 봐서** `docker login`/push timeout. 호스트 노출은 **LoadBalancer(ingress)** 로.
- **Docker Desktop 데몬은 Windows hosts 를 참조** — `harbor.local` 은 **Windows hosts 필수**(WSL hosts 는 호스트 curl 전용).
- **Service `clusterIP` 불변** — 기존 ClusterIP svc 를 headless 로 못 바꿈(S1 정정).
- **상태는 master 트리/클러스터 실거동 우선** — 스펙 배너보다 `get pods`/`describe`/포털 실측.
<!-- 갱신 끝 -->
