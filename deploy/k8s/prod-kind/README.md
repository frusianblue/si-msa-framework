# prod-kind — 운영(prod) GitOps 리허설 인프라 (멀티클러스터)

> 진입 스펙: [`../../../docs/_internal/planning/NEXT_PROD_GITOPS_ARGOCD.md`](../../../docs/_internal/planning/NEXT_PROD_GITOPS_ARGOCD.md)
> 이 디렉터리 = **1단계: 인프라 토대**(§3-1). 기존 단일클러스터 [`../standalone-kind/`](../standalone-kind/) 의 멀티클러스터판.
> 설계는 **잠김**(되돌리지 말 것 — 스펙 §1). 순서 = **무조건 인프라부터**.

## 토폴로지 (실 prod 흉내)

| 역할 | kind 리허설 | 호스트 포트 | 비고 |
|---|---|---|---|
| **cicd(hub)** | `kind-cicd` | **80 / 443** | Harbor · Jenkins · ArgoCD (브라우저 UI: harbor/jenkins/argocd.local) |
| **서비스** | `kind-svc` | **8080 / 8443** | stateless 4앱(gateway·auth-server·user/admin-service) |
| **데이터(K8s 밖)** | 도커 컨테이너 `prod-postgres`·`prod-redis` (`--network kind`) | — | DBA 분리관리 현장모델의 리허설. 클러스터로 안 만듦 |

- 두 클러스터는 kind 기본 도커망 **`kind`** 를 공유 → 노드/컨테이너 IP 로 상호 도달.
- **호스트 80/443 ↔ 8080/8443 분리**: 두 클러스터가 같은 호스트 포트를 못 잡으므로 cicd=80/443, svc=8080/8443. `extraPortMappings` 는 생성 시 고정이라 **지금 박아둠**(나중 재생성 회피).
- **파드 → 클러스터 밖 DB**: 파드는 CoreDNS 로 해소(도커 임베드 DNS 안 봄) → `02-coredns-db.sh` 가 `prod-postgres.internal`/`prod-redis.internal` 을 데이터 컨테이너 kind-IP 로 CoreDNS 에 주입. prod overlay 의 `DB_URL=...prod-postgres.internal...` **무수정** 동작.
- **노드 Harbor 신뢰**: ArgoCD 는 노드 pull 을 안 풀어줌(스펙 §2) → `03-cross-trust.sh` 가 registry-trust DaemonSet(standalone-kind 자산 재사용)으로 kind-svc 노드가 `harbor.local → kind-cicd CP IP` 를 신뢰하게 함.

## 실행 순서 (PASS 게이트 단위)

```bash
cd deploy/k8s/prod-kind
bash 00-up-clusters.sh     # kind-cicd + kind-svc 생성, 공유 kind 망 확인
bash 01-data-containers.sh # 도커 postgres/redis(--network kind) 기동 + initdb(authdb/userdb/admindb)
bash 02-coredns-db.sh      # kind-svc CoreDNS 에 DB 엔드포인트 주입(파드 해소)
bash 03-cross-trust.sh     # kind-svc 노드가 Harbor(cicd) 신뢰하도록 배선
bash 90-verify.sh          # G1~G4 PASS 게이트
```

## PASS 게이트 (1단계 종료 조건 — `90-verify.sh`)

| 게이트 | 검증 |
|---|---|
| **G1** | 두 클러스터 노드 Ready + cicd/svc CP 가 공유 `kind` 망에 존재 |
| **G2** | kind-svc 파드 → kind-cicd API(`:6443/healthz`) 도달 = cross-cluster L3/L4 |
| **G3** | kind-svc 노드 certs.d/harbor.local + /etc/hosts(→cicd CP IP) 배선 (실 pull 은 2단계) |
| **G4** | kind-svc 파드 → `prod-postgres.internal` pg_isready + `prod-redis.internal` PONG |

## 주의 (되돌리지 말 것)

- **IP 변동성**: 데이터 컨테이너/노드 IP 는 재기동마다 바뀔 수 있음 → 데이터 컨테이너 재기동 후엔 `02` 재실행, 클러스터 재생성 후엔 `03` 재실행(둘 다 `docker inspect` 로 매번 재산출 = 멱등).
- **Harbor pull 은 2단계**: 1단계는 신뢰 *배선* 까지. Harbor 본체는 hub(`kind-cicd`)에 2단계에서 설치 → 그때 실제 `harbor.local` pull 실증.
- **호스트 포트 점유**: 80/443/8080/8443 중 점유된 게 있으면 `kind create` 가 bind 실패. `00-up-clusters.sh` 0.5단계가 미리 잡아 명확히 실패시킨다. **가장 흔한 범인 = 기존 `kind-sanity`**(standalone-kind B안, 호스트 80/443 게시). 해결 택1 — A) 미사용 시 `kind delete cluster --name sanity` 후 재실행(권장, 스펙 §6 "둘 동시에 안 돌림"), B) sanity 유지 시 `kind-cicd-config.yaml` 의 hostPort 를 8000/8043 등으로 변경 + hosts 도 그 포트로.
- **데이터 = 리허설 시드**: `initdb-prod.sql` 의 서비스별 계정(auth_app/user_app/admin_app)/비번/DB 는 리허설용. 실 prod 는 DBA 가 분리관리. user↔admin 의 DB 공유는 Flyway 충돌 소지(PITFALLS §9) → admindb 미리 생성, 분리는 step5(데이터 정합)에서.

## 2단계: ArgoCD(hub) + kind-svc 원격등록

> 전제: 1단계 PASS(`90-verify.sh`). ArgoCD=Helm, 등록=선언형 cluster Secret(argocd CLI 불요).

```bash
cd deploy/k8s/prod-kind
bash 10-cicd-ingress.sh    # kind-cicd 에 ingress-nginx(호스트 localhost:80)
bash 11-argocd-install.sh  # ArgoCD(Helm) + argocd.local ingress + admin 비번 출력
bash 12-register-svc.sh    # kind-svc 를 cluster Secret 로 등록(server=svc CP 컨테이너 IP)
bash 91-verify-argocd.sh   # G5~G7 PASS 게이트
```

호스트 접속: Windows + WSL hosts 에 `127.0.0.1 argocd.local` 추가 → `http://argocd.local`(admin/위 비번).

| 게이트 | 검증 |
|---|---|
| **G5** | cicd ingress-nginx Ready + 호스트 `localhost:80` = 404 |
| **G6** | argocd-server Ready + `argocd.local` 도달(Host 라우팅) |
| **G7** | kind-svc cluster Secret 존재 + cicd 파드 → svc API(`:6443/healthz`) 도달 |

- **kind 멀티클러스터 함정**: kubeconfig server 가 `127.0.0.1:<hostport>`(파드가 못 감) / `svc-control-plane`(파드가 이름해소 못 함)이라, `12` 가 `--internal` kubeconfig 의 CA/인증서는 쓰되 **server 만 svc CP 컨테이너 IP** 로 바꿔 등록.
- **TLS SAN 폴백**: `91` G7 이 TLS 오류면 `SVC_TLS_INSECURE=true bash 12-register-svc.sh` 재실행(리허설 한정 skip-verify). 실 prod 는 apiserver certSANs 에 접속 IP/도메인 명시 → 항상 CA 검증.
- **클러스터 재생성 후**: svc CP IP 가 바뀌면 `12` 재실행(server 재산출).

## 3단계: GitOps 자산 (`deploy/argocd/`)

> 전제: 2단계 PASS. **ArgoCD = git(master) 진실** → `deploy/argocd/` 를 **commit + push 먼저**.

```bash
# ① GitOps 자산 커밋·푸시 (필수 — ArgoCD 는 master 를 읽음)
git add deploy/argocd && git commit -m "argocd: prod GitOps 자산(AppProject+prod App+app-of-apps)" && git push

# ② 부트스트랩 seed + 검증
cd deploy/k8s/prod-kind
bash 20-gitops-bootstrap.sh   # AppProject + app-of-apps 를 ArgoCD 에 seed → git 에서 si-msa-prod 생성
bash 92-verify-gitops.sh      # G8~G10
```

자산: `deploy/argocd/projects/si-msa.yaml`(AppProject) · `apps/si-msa-prod.yaml`(prod Application: source=overlays/prod@master, dest=kind-svc, automated+selfHeal+prune) · `bootstrap.yaml`(app-of-apps).

| 게이트 | 검증 |
|---|---|
| **G8** | AppProject si-msa + app-of-apps si-msa-bootstrap 존재 |
| **G9** | prod Application si-msa-prod 생성(app-of-apps) + dest=kind-svc + sync 시도 |
| **G10** | kind-svc 에 ns si-msa + Deployment reconcile (파드 ImagePullBackOff=정상) |

- **파드 ImagePullBackOff 는 정상(fail-loud)**: prod overlay 이미지 = sentinel `__GITSHA__` + Harbor 미설치. 4단계(promote: 불변 sha 커밋) + Harbor(hub) 설치 후 green. 3단계는 **GitOps reconcile 가 매니페스트를 kind-svc 에 적용하는가**까지만 검증.
- **commit-first**: 로컬 apply 가 아니라 git push 가 트리거. 미푸시면 si-msa-prod 가 안 생김(20 이 경고).
- **dest=kind-svc**: 리허설. 실 prod 는 그 클러스터 이름으로 교체.

## 4단계: Harbor(hub) + bash promote

> 전제: 3단계 PASS(`92-verify-gitops.sh`). 파드 ImagePullBackOff(sentinel `__GITSHA__` + Harbor 미설치)를 **green** 으로 전환.
> 빌드 주체 = **호스트 docker**(Kaniko 아님). "bash 로 흐름부터 증명"이 목적 → 미해결 Kaniko 멀티빌드 이슈를 끌어들이지 않음. Jenkins(`Jenkinsfile.promote`) 파이프라인화는 흐름 증명 후 후속.

```bash
cd deploy/k8s/prod-kind
bash 30-harbor-hub-install.sh   # kind-cicd(hub)에 Harbor(Helm, harbor.local) + si-msa 프로젝트
bash 35-seed-secrets.sh         # 리허설 시크릿 4개(prod 는 시크릿 미커밋 → 없으면 CreateContainerConfigError)
bash 40-promote.sh              # 호스트 docker 빌드 → push → overlay 핀 → git commit/push → ArgoCD sync
# (sync/pull/startup 대기 후)
bash 41-verify-promote.sh       # G11~G13
```

**호스트 push 사전조건**(40-promote 가 docker push harbor.local 하려면): Docker Desktop daemon 에 `insecure-registries: ["harbor.local"]`(HTTP 평문 레지스트리) + Windows/WSL hosts 에 `127.0.0.1 harbor.local`. **git author identity**(`git config user.email/user.name`)도 있어야 promote commit 이 된다(40 의 0단계가 선점검).

**리허설 시크릿**(`35-seed-secrets.sh`): prod overlay 는 시크릿을 git 에 안 넣는다(설계 — ESO/SealedSecrets/운영자 사전주입). base Deployment 의 `envFrom.secretRef`(4개 `*-secret`)가 없으면 파드가 **CreateContainerConfigError**(이미지 pull 이전 config 단계 실패). 리허설에선 서비스별 계정(initdb 일치) 고정값으로 1회 주입.

promote 흐름(`40-promote.sh`): ① 호스트 docker + `Dockerfile.build` 로 4서비스 `:<sha>` 빌드(builder 스테이지 SERVICE 무관 → 1회 컴파일 재사용) → ② `harbor.local/si-msa/<svc>:<sha>` push → ③ `overlays/prod` 의 images 줄을 sed 로 통째 교체(placeholder newName + sha newTag, flow 유지, 멱등 — kustomize CLI 불요) → ④ **git commit/push**(prod 반전 — dev 의 "되커밋 X"와 정반대, ArgoCD 는 master 가 진실) → ⑤ `refresh=hard` 로 즉시 reconcile.

| 게이트 | 검증 |
|---|---|
| **G11** | Harbor(hub) reachable + si-msa 프로젝트에 4서비스 repo `:<sha>` 아티팩트 존재 |
| **G12** | overlays/prod 핀 완료 — images newTag `__GITSHA__` 소거 + newName=harbor.local + git 커밋·push |
| **G13** | si-msa-prod = Synced + Healthy + kind-svc 파드 4서비스 Running(green) |

- **★ prod 는 핀을 git 에 커밋**: dev(push-CD)는 워크스페이스에서만 핀(되커밋 X)지만, prod(pull-GitOps)는 ArgoCD 가 git(master) 커밋 상태를 진실로 reconcile → 핀이 **반드시 커밋·push** 돼야 sync 됨. `40-promote` 가 그 커밋을 만든다.
- **워킹트리 clean 전제**: 미커밋 변경이 있으면 `:<sha>` 가 실제 빌드 내용과 어긋남 → 40 의 0단계가 막음(overlay 파일만 예외).
- **placeholder name 핀 필수**: prod overlay 의 image name=`registry.example.com/...`(운영 레지스트리 placeholder) → newTag 만 박으면 가짜 레지스트리에서 pull 시도 = 실패. images 줄을 `{ name: ..., newName: harbor.local/..., newTag: <sha> }` 로 통째 교체해 **newName 까지** 박아야 함(PITFALLS §9 local overlay 함정의 prod·harbor 판).
- **sed 핀(kustomize 불요)**: overlay images 는 flow 스타일 한 줄이라 `40-promote` 가 sed 로 줄 통째 교체(첫/재promote 멱등). dev 의 `pin-image-tag.sh`(sed)와 일관 — 별도 CLI 설치 불요.
- **파드 green 의 런타임 선결조건(데이터 정합)**: prod 는 다음이 매니페스트에 반영돼 있어야 4서비스가 뜬다 —
  - **file base-path**: `deployment-hardening.yaml` 공통 env `FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads`(readOnlyRootFilesystem 에서 local FileStorage 가 `/application/uploads` 못 만들어 admin 등이 깨짐).
  - **DB 분리**: user=`userdb`(user_app), admin=`admindb`(admin_app) — 서비스별 전용 DB·계정(최소권한). 같은 DB·계정을 공유하면 Flyway `checksum mismatch` 충돌(PITFALLS §9). initdb-prod.sql 이 3 DB/3 계정을 만들어둔다.
  - ⚠️ **이미 sidb 가 오염된 클러스터**(과거 admin 이 sidb 에 마이그레이션 적용): `36-reset-userdb-rehearsal.sh` 로 sidb 재생성(admin→admindb 분리 sync 후). fresh 클러스터(00~)는 처음부터 분리라 불요.
  - ⚠️ **base/overlay 매니페스트 변경은 40-promote 가 자동 커밋하지 않는다**(images 핀만 커밋) → `deployment-hardening.yaml`·admin DB_URL 변경은 **별도 git commit/push** 해야 ArgoCD 가 본다.

## 다음 단계 (스펙 §3-5~)

5. 데이터/관측 정합 (prod overlay DB/Redis 를 K8s밖 엔드포인트로, 관측 분산형)
6. 문서 캐스케이드(`docs/ops/PROD_GITOPS_ARGOCD.md` 신설)
- (선택) Jenkins(`Jenkinsfile.promote`) 파이프라인화 — bash promote 흐름 증명 후

## teardown

```bash
bash 00-down.sh        # 데이터 컨테이너만
bash 00-down.sh --all  # 클러스터(cicd,svc) + 데이터 컨테이너 전부
```
