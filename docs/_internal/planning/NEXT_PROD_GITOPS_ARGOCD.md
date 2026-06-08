# NEXT — 운영(prod) 프로비저닝: ArgoCD GitOps + 3-클러스터 분리 토폴로지

> 상태: **설계 잠김(2026-06-08, 대화로 확정) · 1~3단계 실측 PASS · 4단계 promote 파이프라인 실측 증명 + 부분 green(gateway·auth-server·user-service·redis Running, admin-service CrashLoop 잔여 = 다음 섹션 진입 과제).**
> 다음 섹션 진입점. **순서 = 무조건 인프라부터**(경험 먼저로 새다 인프라에서 되돌리는 비용 회피 — Chae 방침).
> 이번 섹션 산출(이미 적용): `deploy/k8s/overlays/prod/kustomization.yaml` 가변 `:latest` → 커밋식 `__GITSHA__` 전환(아래 §4 참조).

---

## 0. 한 줄

운영은 **ArgoCD GitOps** 로 한다. `master` 브랜치 = **운영 즉시반영**. 토폴로지는 **역할별 3묶음 분리**(cicd / 데이터 / 서비스), DB·Redis 는 **K8s 밖 독립**(현장 정석). kind 로 이 분리 토폴로지를 리허설한 뒤 VirtualBox VM(실 prod)으로 옮긴다. 다음 섹션은 **전부 prod 기준**(dev 아님).

## 1. 잠긴 결정 (되돌리지 말 것 — 대화로 확정)

- **배포 방식 = ArgoCD GitOps(pull).** `kubectl apply` 가 아니라, git 커밋 상태를 ArgoCD 가 클러스터로 reconcile. 매니페스트(kustomize base/overlays)는 **그대로** — 바뀌는 건 "누가 언제 apply 하고 드리프트를 누가 고치냐"는 운영 레이어뿐.
- **트리거 = `master` 머지 = 운영 즉시반영.** prod Application `targetRevision: master`, sync 자동(+ selfHeal + prune). 별도 release-태그 게이트는 **안 씀**(Chae 결정). (대안인 release-태그 방식은 문서에 선택지로만 남김 — 프레임워크는 토폴로지/정책을 못박지 않는다.)
- **`develop` 브랜치 = 운영 완주 *후* 별도 생성**(Chae 가 직접). dev 셋팅은 그 다음 섹션. **이번 섹션엔 dev 작업 없음.**
- **stg = 제거.** dev/stg/prod 는 어차피 로컬이라 동일 취급. 분리 실험 안 함.
- **토폴로지 = 역할별 3 클러스터(묶음):**
  - **cicd(hub)** — Harbor · Jenkins · ArgoCD.
  - **데이터** — PostgreSQL · Redis. **K8s 밖 독립**(전용 서버/RDS/별도 관리, DBA 라이프사이클 분리 — finance/공공 SI 정석). cross-cluster 메시 불필요(서비스는 `DB_URL` 로 그 엔드포인트에 평범히 접속).
  - **서비스** — gateway · auth-server · user-service · admin-service(stateless).
- **kind 리허설 매핑(실 prod 흉내):**
  - `kind-cicd`(hub: Harbor·Jenkins·ArgoCD)
  - `kind-svc`(서비스 4개) ← prod 서비스 클러스터 역할
  - **우분투(WSL) 도커 컨테이너 postgres/redis** ← prod 데이터(K8s 밖) 역할. `docker run --network kind …` 로 kind 네트워크에 붙여 파드에서 `DB_URL=jdbc:postgresql://<컨테이너명>:5432/…` 로 해소.
  - 데이터를 굳이 kind 클러스터로 만들지 **않음** — 클러스터 하나 더 세우는 부대비용(별도 kubeconfig/CNI/DNS + cross-cluster 도달 + ArgoCD 추가 등록)만 늘고, "DB=K8s 밖" 현장 모델과 더 안 맞음.
- **컴포넌트 배치 원칙:**
  - CI/CD 도구(Jenkins·Harbor·ArgoCD) → hub.
  - 관측 = 워크로드 클러스터마다 수집 agent(Prometheus) + **중앙 Grafana 는 hub**(분산형).
  - 운영 애드온(ingress-nginx·metrics-server·cert-manager 등) → **각 워크로드 클러스터마다**(트래픽·HPA 가 거기 있음 — hub 에 몰면 안 됨).

## 2. dev↔prod 핀 모델 차이 (핵심 — PITFALLS §9 와 한 묶음)

- dev(현행) = **push-CD**: CI 가 *워크스페이스에서만* `__GITSHA__` 치환(되커밋 X) → `kubectl apply`. git 엔 sentinel 영존.
- prod(ArgoCD) = **pull-GitOps**: ArgoCD 의 단일 진실 = **git 커밋 상태**. → 핀을 **git 에 커밋**해야 한다(dev 의 "되커밋 X" 규칙을 *반전*). promote 단계가 overlay images 줄을 sed 로 치환(pin-image-tag.sh 류, kustomize 불요) 후 **git commit/push** → ArgoCD 가 그 diff 를 sync.
- git 기본값이 `__GITSHA__` sentinel 인 이유는 dev 와 동일 = **fail-loud**(promote 전 prod 는 sync/pull 실패 → stale 차단).
- ⚠️ ArgoCD 는 **노드→레지스트리 pull 을 풀어주지 않는다**(PITFALLS §9). 각 워크로드 클러스터 노드의 containerd 가 Harbor(운영 레지스트리)에 도달·신뢰해야 함(certs.d/insecure-registry/hosts) — kind 의 `registry-trust` DaemonSet 패턴의 멀티클러스터판. + ArgoCD(hub)→각 클러스터 API 서버 네트워크 도달(kind 는 클러스터마다 별도 docker 네트워크라 kubeconfig 의 server 주소를 컨테이너 IP 로 등록해야 함 — kind 멀티클러스터 함정).

## 3. 다음 섹션 작업 순서 (인프라 → GitOps → 문서)

1. **인프라 토대(먼저)** — kind 2클러스터(`kind-cicd` + `kind-svc`) 생성 스크립트 + 우분투 도커 postgres/redis(`--network kind`) 기동/연결. 클러스터간 네트워크·노드 Harbor 신뢰·서비스→DB 도달 검증(PASS 게이트). **✅ PASS 검증 완료(2026-06-08, 받는 쪽 실측 G1~G4)** — `deploy/k8s/prod-kind/`(kind-cicd/svc config·`00`~`03`·`90-verify` G1~G4·`00-down`·README). 결정: 호스트 포트 cicd=80/443·svc=8080/8443, 파드→밖DB=CoreDNS 주입(`prod-postgres.internal` 무수정), 노드 Harbor 신뢰=registry-trust DaemonSet 멀티클러스터판.
2. **ArgoCD(hub)** — `kind-cicd` 에 ArgoCD 설치(`argocd.local` B안 ingress) + `kind-svc` 원격 클러스터 등록(kubeconfig server=컨테이너 IP). **✅ PASS 검증 완료(2026-06-08, G5~G7 · UI Clusters 에 kind-svc 등록 확인)** — `10-cicd-ingress`·`11-argocd-install`(Helm)·`12-register-svc`(선언형 cluster Secret, insecure=false 정상)·`91-verify-argocd`.
3. **GitOps 자산** — `deploy/argocd/`: AppProject(`projects/si-msa.yaml`) + prod Application(`apps/si-msa-prod.yaml`, source=`overlays/prod`, targetRevision=`master`, dest=`kind-svc`, sync 자동+selfHeal+prune) + app-of-apps(`bootstrap.yaml`). **✅ PASS 검증 완료(2026-06-08, G8~G10 · si-msa-prod Synced · kind-svc Successful)** — `20-gitops-bootstrap`(commit-first)·`92-verify-gitops`. 트리아지: prod overlay 에 ServiceMonitor `$patch:delete` 누락 → ArgoCD `tasks are not valid` → 추가(dev/local 과 동일, PITFALLS §9). 파드 ImagePullBackOff=fail-loud(sentinel `__GITSHA__`, 4단계 promote+Harbor 후 green).
4. **promote 배선** — bash 우선(Chae 결정), 빌드 주체 = **호스트 docker**(Kaniko 아님). **🟢 promote 파이프라인 실측 증명(2026-06-08) + 부분 green · admin-service CrashLoop 잔여(다음 섹션)** — 자산: `30-harbor-hub-install`(08 멀티클러스터 적응) · `40-promote`(호스트 docker `:<sha>` → push → overlay images **sed** 치환[placeholder newName+sha newTag, kustomize 불요] → git commit/push[prod 반전] → ArgoCD refresh; 0단계 워킹트리 clean+git identity 선점검) · `41-verify-promote`(G11~G13) · `35-seed-secrets`(리허설 시크릿 4) · `36-reset-userdb-rehearsal`(개명 후 userdb 리허설 초기화 — 구 36-reset-sidb-rehearsal). 매니페스트 픽스: deployment-hardening `FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads`(문서-구현 드리프트 보정) · prod overlay admin DB_URL `sidb→admindb`(Flyway 분리). **증명**: ArgoCD master 새 커밋 sync → `:520856b` 새 RS(빌드→push→핀→commit→sync 전 구간). **실측 트러블슈팅 4건 순차 해소**(git identity·시크릿·file base-path·sidb Flyway) → gateway·auth-server·user-service·redis green. **잔여**: admin-service CrashLoop(file/admindb 후에도, 원인 미규명) → 5단계 진입 전 `logs --previous` 규명. Jenkins(`Jenkinsfile.promote`) 파이프라인화는 흐름 증명 후 후속.
5. **데이터/관측 정합** — prod overlay 의 `DB_URL`/Redis 호스트를 데이터(K8s 밖) 엔드포인트로(이미 `prod-postgres.internal` 전제) + 관측 분산형(워크로드 agent → 중앙 Grafana).
   - ① **DB/role 서비스별 개명 완료**(2026-06-08): `sidb→userdb`·`siuser→user_app/admin_app`·`authuser→auth_app`(owner 각 짝, 최소권한). initdb 4종/secrets/configmap/compose/overlay/35-seed/서비스 소스/현행 가이드 + PITFALLS §9. 공유 sidb Flyway 충돌 설계 차단. PyYAML 정합 PASS. ⚠️ 기존 클러스터 적용 = initdb 1회성 → `01` 재실행 또는 `36-reset-userdb-rehearsal.sh`.
   - ② **파일 영속 NFS RWX 산출**(2026-06-08): `13-nfs-provisioner.sh`(in-cluster ganesha `nfs-server-provisioner`, StorageClass `nfs`, RWX) + `components/file-storage-nfs`(PVC×2 + user/admin patch, `/tmp`→`/mnt/uploads`). application.yml env placeholder → promote 불요. 구조 PASS, 형님 PASS 게이트(helm install·PVC Bound·파드 재기동) 대기. 운영은 관리형 NFS/EFS promote.
   - ③ 관측 분산형 — 미착수.
6. **문서 캐스케이드** — 운영 가이드(`docs/ops/PROD_GITOPS_ARGOCD.md` 신설) + branch-per-env 가이드(master=prod 레퍼런스 + release-태그 대안) + FRAMEWORK_MODULES/README + PITFALLS + HANDOFF.

## 4. 이번 섹션에 이미 적용된 산출 (zip 동봉)

- `deploy/k8s/overlays/prod/kustomization.yaml`: `newTag: latest` ×4 → **`__GITSHA__`**(커밋식 핀, §2). `imagePullPolicy: Always` 패치 제거(그 전제 = `:latest` 가변태그가 사라짐 → base `IfNotPresent` 상속, 불변 sha 는 노드 캐시 재사용 안전). 헤더 주석 = ArgoCD/GitOps 전제 + dev 와의 되커밋 차이 명시.
- PyYAML 파싱 검증 OK(images 4× `__GITSHA__`, Always 패치 없음, 나머지 issuer/DB/ingress 패치 유지).

## 5. 빈칸 (진행 중 결정 — 지금 안 정해도 됨)

1. **운영 클러스터 VM** — VirtualBox 에 cicd/dev VM 은 있으나 **prod 전용 VM 미생성**(stg 는 폐기). master=prod 인데 실 prod 클러스터를 어디로 둘지(prod VM 신설 vs 기존 VM 재명명) → VM 이전 시점에 결정.
2. ~~stg 소스~~ — **해소(stg 제거)**.

## 6. 환경 메모

- kind-sanity(WSL, 32GB)와 VirtualBox VM 7대(호스트 RAM)는 **별개 메모리**. VM 트랙 갈 땐 WSL kind 안 띄워도 됨 → 둘을 동시에 풀로 안 돌리면 자원 충분(64GB/2TB).
- 문서 잔여물 정리(2026-06-08 완료): `docs/_internal/apply-notes/`(3파일) · `docs/_internal/planning/NEXT_K8S_REAL_DEPLOY.md`(docker-desktop kind 실배포 전제 = 은퇴, 이 ArgoCD 멀티클러스터 트랙이 대체) → **삭제 완료**. PITFALLS append 2파일은 본문 §8/§9/부록에 병합 후 삭제.
