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
bash 01-data-containers.sh # 도커 postgres/redis(--network kind) 기동 + initdb(authdb/sidb/admindb)
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
- **데이터 = 리허설 시드**: `initdb-prod.sql` 의 siuser/비번/DB 는 리허설용. 실 prod 는 DBA 가 분리관리. user↔admin 의 sidb 공유는 Flyway 충돌 소지(PITFALLS §9) → admindb 미리 생성, 분리는 step5(데이터 정합)에서.

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

## 다음 단계 (스펙 §3-3~)

3. `deploy/argocd/` GitOps 자산(AppProject + prod Application `targetRevision:master` + app-of-apps)
4. promote 배선(`Jenkinsfile.promote`: 불변 `:<sha>` → prod overlay `kustomize edit set image` + git commit/push → ArgoCD sync)
5. 데이터/관측 정합 (prod overlay DB/Redis 를 K8s밖 엔드포인트로, 관측 분산형)
6. 문서 캐스케이드(`docs/ops/PROD_GITOPS_ARGOCD.md` 신설)

## teardown

```bash
bash 00-down.sh        # 데이터 컨테이너만
bash 00-down.sh --all  # 클러스터(cicd,svc) + 데이터 컨테이너 전부
```
