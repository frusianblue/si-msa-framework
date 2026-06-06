# NEXT_K8S_REAL_DEPLOY.md — 현재 환경에서 운영 토폴로지 전체 리허설 (착수 설계, 2026-06-06 락)

> 목표(확정): **Docker Desktop kind 단일 환경에서 "운영(prod)으로 할 수 있는 것 전부"를 리허설**한다. 외부 인프라(레지스트리/DB/TLS/시크릿)는 **로컬 대역(stand-in)**으로 끼운다. S3 는 이번 범위 제외(나머지 검증 후 별도).
> 선행: kind `overlays/local` 6파드 `1/1 Running` ✅ + DbAuthenticator 토큰 플로우 ✅.
> 제약: 작성 환경 빌드/`kubectl`/`helm` 직접 실행 불가 → 매니페스트 정적(렌더/구조) 검증만, 실 apply·파드 로그·Helm 설치는 받는 쪽.

---

## 0. 확정 결정 (이번 세션 락)

| # | 항목 | 결정 |
|---|---|---|
| 1 | 이미지 태그 | **양태그 동시 발행** — 불변 `:<git-sha>`(핀) + 채널 `:dev`/semver. 매니페스트는 `kustomize edit set image …=…:<sha>` 로 SHA 핀. 둘 다 굴려보고 최종 확정 |
| 2 | 이미지 서버 | **필수 — Harbor 설치**(사내 on-prem 리허설). `docker push` 로 수동 적재 → `imagePullSecrets` 로 pull. (Jenkins 와 독립; Jenkins 는 자동화 단계에서) |
| 3 | 영속 | **기본 StorageClass + 동적 프로비저너**(kind=local-path / Docker Desktop=hostpath) 로 **PVC**. postgres=영속 StatefulSet, redis=설계상 휘발(옵션으로 AOF+PVC). **S3 제외(나중)** |
| 4 | overlay | **dev 먼저 → prod-rehearsal**. dev≠prod(아래 §1). prod 토폴로지를 로컬 대역으로 리허설하는 `overlays/prod-rehearsal` 신설 |
| 5 | CI/CD | Jenkins 는 **맨 마지막**(수동으로 build→tag→push→apply 검증 후 자동화). `deploy/cicd/Jenkinsfile` 재사용 |

## 1. dev vs prod 실제 차이 (overlay 근거 — "똑같지 않다")

| | dev | prod |
|---|---|---|
| 레플리카 | 1 | base 2 + **HPA**(metrics-server 선행) |
| 시크릿 | 약한 값 동봉 | **미포함 — ESO/SealedSecrets 외부 주입** |
| issuer | 인-클러스터 기본 | **공개 URL `https://auth.example.com`** |
| Ingress/TLS | 없음(port-forward) | **실도메인 + TLS** |
| DB/Redis | 외부 `*-postgres.internal` | 외부 매니지드 |

→ prod 전용 위험 조각(HPA·TLS ingress·외부 시크릿·공개 issuer)은 dev 그린으로 보장 안 됨 → 별도 검증. 다행히 **애드온 작업이 prod 추가분 검증과 겹침**.

## 2. 외부 의존물 → 로컬 대역 매핑 (리허설 핵심)

| 운영 전제 | 로컬 대역(prod-rehearsal) |
|---|---|
| 실 레지스트리 `registry.example.com` | **로컬 Harbor**(`core.harbor.local` 등) + `imagePullSecrets` |
| 외부 매니지드 DB `prod-postgres.internal` | **인-클러스터 영속 postgres**(`components/postgres-persistent`, StatefulSet+PVC) → DB_URL 을 `postgres:5432` 로 패치 |
| 외부 매니지드 Redis | 인-클러스터 redis(base). 토큰 생존 원하면 AOF+PVC(옵션) |
| 실도메인 + 공인 TLS | **self-signed TLS**(또는 mkcert) + `/etc/hosts` 로 `auth.local`/`si-msa.local` |
| ESO/SealedSecrets | 평문/생성 `Secret`(리허설 한정) |

## 3. 실행 순서 (런북)

> 각 단계 그린 확인 후 다음. 받는 쪽에서 `kubectl`/`docker`/`helm` 실행.

**S1. 영속 postgres (PVC)** — `components/postgres-persistent/`(StatefulSet + volumeClaimTemplate, 기본 SC). ✅ 이번 드롭 포함.
 - 검증: 파드 Ready → `kubectl get pvc -n si-msa` Bound → 파드 delete 후 재기동에도 DB 유지.

**S2. Harbor 설치 + 이미지 적재**
 - Helm: `helm install harbor harbor/harbor`(expose=ingress 또는 NodePort, self-signed TLS). `/etc/hosts` 에 `core.harbor.local`.
 - 4서비스 빌드 → **양태그** 태깅(`core.harbor.local/si-msa/<svc>:<sha>` + `:dev`) → `docker login` → `docker push`.
 - `imagePullSecrets`: `kubectl create secret docker-registry harbor-cred …` + base/overlay 의 SA 또는 deployment 에 부착.
 - ⚠️ 불변태그라 `IfNotPresent` 안전(같은 태그 다른 digest 문제 해소 — PITFALLS §9).

**S3. dev overlay 실 apply**
 - `overlays/dev` 의 `images.newName` 을 Harbor 좌표로, `newTag` 를 SHA 로(`kustomize edit set image`). DB_URL 을 인-클러스터 `postgres:5432`(S1)로 패치.
 - `kubectl apply -k overlays/dev` → 6파드 Ready → port-forward 스모크(actuator/health, AS 토큰 플로우).

**S4. 애드온 (prod 추가분 검증과 겹침)**
 - **metrics-server**(HPA 전제) → `kubectl top` → prod `hpa.yaml` 동작.
 - **kube-prometheus-stack**(Helm) → ServiceMonitor CRD → overlay 에서 SM 복원(local 은 제거 상태) → `/actuator/prometheus` 스크랩.
 - **ingress-nginx** → `base/common/ingress.yaml`(host `si-msa.example.com`→로컬은 `si-msa.local`) + self-signed TLS.

**S5. prod-rehearsal overlay 실 apply** (신설)
 - = prod overlay(HPA·ingress·hardening) + 로컬 대역(§2): `resources` 에 `../../components/postgres-persistent`, DB_URL→`postgres:5432`, issuer→`https://auth.local`, ingress host→`*.local`+self-signed TLS, secrets→평문/생성, images→Harbor:SHA.
 - `kubectl apply -k overlays/prod-rehearsal` → HPA/ingress/TLS/외부서명 issuer 까지 그린.

**S6. 상위 인증 흐름 스모크** — OIDC RP 콜백·게이트웨이 이중 발급기(우선) / SAML·webauthn(HTTPS·IdP 전제).

**S7. Jenkins(자동화, 마지막)** — `Jenkinsfile`: build → 양태그 → Harbor push → `kustomize edit set image` → `apply -k`. CredentialsId(Harbor)·kubeconfig 주입.

## 4. 이번 드롭 (S1)
- `deploy/k8s/components/postgres-persistent/{postgres.yaml,kustomization.yaml}` — 영속 postgres(StatefulSet, 기본 SC PVC, headless svc, initdb, NetworkPolicy, 하드닝 securityContext).

## 5. 다음 드롭 후보 (확인 후)
- `overlays/prod-rehearsal/` 트리(kustomization + DB/issuer/ingress/secret 패치 + Harbor images + imagePullSecrets).
- Harbor 설치 런북(`docs/ops/HARBOR_SETUP.md`) + 양태그 빌드/푸시 스크립트.
- 애드온 설치/검증 런북 보강(`K8S_ADDONS.md`).
- self-signed TLS + `/etc/hosts` 가이드.
