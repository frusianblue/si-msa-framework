# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**✅ 로컬 Docker Compose 풀 그린 달성(2026-06-05) → 현재 진행 트랙 = K8s(kind) 배포 테스트.** compose `up` 을 순차 트리아지하며 멀티서비스 배포정합 결함 4건을 발견·수정해 6컨테이너 전부 `(healthy)` 확인. **검증 완료 환경은 compose 까지.** 그 뒤 kind 배포를 위해 overlays/local 정합 패치(ⓐadmindb·ⓒⓓ파일경로) + auth-server 운영 인증기(`DbAuthenticator`) 를 **작성**했으나, **아직 빌드/배포/검증 0** — kind 클러스터는 떠 있지만(3 노드) 비어 있다. **다음 섹션 = 이 자산을 kind 에 처음 올려보는 단계.** ⊕ 이번 세션 추가: kind 첫 배포 전 정적 점검에서 **이미지명 정합 결함 1건 선포착·수정** — local overlay `images:` 가 tag만 바꿔 렌더명이 `registry.example.com/...:local` 로 남아 노드 적재명 `si-msa/...:local`(compose 빌드/HANDOFF 적재명)과 불일치 → 첫 `apply` 시 4파드 `ImagePullBackOff` 확정. `images.newName` 으로 short name 통일(검증: 변환기 재현으로 4서비스 전부 노드 적재명 일치 확인). 동반 문서 갱신: `LOCAL_K8S_TEST.md`·`LOCAL_K8S_ENV_SETUP.md`·`PITFALLS §9`(+자가진단 1행). 트리아지 상세는 `HANDOFF.md` §6(누적 정본)·`PITFALLS.md` §9, 착수 순서는 `_internal/planning/NEXT_LOCAL_COMPOSE_AND_KIND.md` §3~§4.

## 최종 갱신
- 일자: 2026-06-05 · 갱신자: compose 그린화 → K8s 배포 트랙 전환 세션 (⊕ 후속: kind 배포 전 이미지명 정합 정적 점검·수정)
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / SF7 / SC 2025.1.1 / Jackson 3 — 스택 무변경.

## 직전에 한 것 (Done)
**compose 트리아지로 배포정합 결함 4건 발견·수정 → compose 풀 그린(받는 쪽 검증 ✅).** 표(상세=PITFALLS §9):
| # | 증상 | 원인 | 수정(적용 형태) | kind 전파 |
|---|---|---|---|---|
| 1 | auth-server 만 `unhealthy`→의존 서비스 부팅 실패 | AS 자체 보안체인이 `/actuator/**` permitAll 누락 → health 302/401 | `AuthorizationServerConfig` 1줄 permitAll (**앱 코드**) | ✅ 자동(같은 이미지) |
| 2 | user/admin `FileNotFoundException: ./logs/*.log` | logback-common 파일 appender 가 비루트/읽기전용 컨테이너 `./logs` 못 씀 | `LOG_DIR=/tmp` (**env**: compose + k8s hardening) | ✅ 자동(hardening 공통 패치) |
| 3 | user/admin `AccessDeniedException: /application/uploads` | local FileStorage 가 `./uploads` 생성 시도(동근원) | `FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads` (**env**) | ⚠️ compose 만 — k8s 는 overlays 에서 admin 별도 |
| 4 | user/admin `TokenStore` 빈 없음 | prod `token-store.type=redis` 인데 `framework-redis` 미의존(삼단 토글 위반) | `build.gradle` 에 `framework-redis` 추가 (**모듈**) | ✅ 자동(같은 이미지) |

- 1·2·4 는 같은 이미지/공통 패치라 kind 자동 적용, 3 만 k8s overlays 에서 admin 에 별도 반영 필요.
- 문서 동반 갱신: PITFALLS §9(5항목+자가진단표 5행)·compose README·AUTH_SERVER.md §3·HANDOFF §6.
- **추가(2026-06-05, 작성/전달만 — 빌드·배포·테스트 미실행) — auth-server 운영 인증기**: prod 엔 Authenticator 빈이 없던 결함(②)을 `DbAuthenticator`(authdb `app_user`, `@Profile("!local")`+`@ConditionalOnMissingBean`)로 해소 → kind 를 **운영(prod)처럼** 띄워 실 인증 경로 검증 가능. V7 seed `tester/Test1234!`({bcrypt}). local=demo/demo(LocalDemo) 그대로. overlays/local 에 ⓐadmindb·ⓒadmin업로드·ⓓuser로컬저장 패치 동반. 신규/변경: V7·AppUser·AppUserMapper(+xml)·DbAuthenticator·ProdAuthenticatorConfig·@MapperScan·DbAuthenticatorTest·overlays(postgres/kustomization).

## 현재 상태 (적용/검증)
- **✅ compose = 받는 쪽 검증 완료**(유일하게 실제 검증된 환경): `up -d --build` 후 6컨테이너 전부 `(healthy)`. 부팅 전 구간(logging→file→Flyway(sidb/admindb)→security TokenStore) 통과.
- **🟡 k8s 자산 = 작성/정적검사만, kind 미배포·미검증**: `deploy/k8s/base`+`overlays/local` 매니페스트는 이미 레포에 존재(이전 세션). 이번에 overlays/local 패치(ⓐadmindb·ⓒadmin업로드·ⓓuser로컬저장) + auth-server `DbAuthenticator`(V7 `app_user`+tester seed)를 **작성**. YAML/XML/brace/해시 정적검증만 했고 **빌드·`kind load`·`apply`·스모크는 전부 미실행**.
- **🟡 auth-server 변경(V7·DbAuthenticator·@MapperScan·테스트) = 받는 쪽 빌드/테스트 미실행** — 마지막 zip 전달만 됨.
- **현재 인프라**: compose 스택 정지(필요 시 재기동), **kind 클러스터 Active(3 노드, v1.34.3)·deployment/pod 0(빈 상태)**.
- 작성환경 Maven Central·릴리스 CDN 차단 → 빌드/kind/kubectl 직접 실행 불가(정적 작성 + 받는 쪽 실행).

## 바로 다음 할 일 (Next) — kind 첫 배포 (상세 `_internal/planning/NEXT_LOCAL_COMPOSE_AND_KIND.md` §3~§4)
> 정합 자산은 **작성 끝(미검증)**. 다음 섹션은 이걸 kind 에 **처음 올려서 검증**하는 단계. compose 는 이미 그린이라 회귀용으로만.
1. **받는 쪽 빌드/테스트** — 마지막 zip 적용 후 `:services:auth-server:test`(`DbAuthenticatorTest` 5건) + `spotlessApply`. 4서비스 이미지 재빌드(auth-server 는 V7/DbAuthenticator 로 필수).
2. **kind 적재·배포** — (이미지명 정합 수정 반영) `kind load docker-image si-msa/<svc>:local`(4종, compose 빌드 이미지 **재빌드 없이 그대로 재사용 가능** — local overlay 가 `newName` 으로 short name 으로 렌더) → `kubectl apply -k deploy/k8s/overlays/local` → `kubectl -n si-msa get pods -w`. (overlays/local: ServiceMonitor 제거됨, 인-클러스터 postgres(authdb/sidb/admindb)+redis, `si-msa/<svc>:local` IfNotPresent.) ⚠️ kindnet 은 NetworkPolicy 비강제라 통과하지만, 강제 CNI(Calico/Cilium) 로 바꾸면 postgres 수신 허용 규칙 부재로 DB 차단됨(추후 `allow-postgres-from-apps` 추가 필요).
3. **스모크** — 전 파드 Ready(프로브=actuator permit). AS 폼 로그인 **`tester`/`Test1234!`** → authorization_code+PKCE → 토큰(운영 인증기 DbAuthenticator 경로). admin=admindb Flyway 통과, user/admin 업로드 에러 없음. 막히면 `kubectl logs`/`describe` 스샷 트리아지.
- 참고: kind 절차/트러블슈팅 `docs/ops/LOCAL_K8S_TEST.md`, 애드온(metrics-server/Prometheus/ingress) `docs/ops/K8S_ADDONS.md`.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것 — 전부 PITFALLS §9)
- AS 가 자체 `SecurityFilterChain` 정의 시 `/actuator/**` permitAll 을 **직접** 넣어야 함(framework-security 백오프되며 규약 사라짐). 점검: `curl -i …/actuator/health` 302/401.
- 비루트+하드닝(읽기전용 루트FS) 컨테이너에선 **작업디렉터리 하위 쓰기경로(logs/uploads/임시) 전부 `/tmp`(emptyDir)로** 리다이렉트. 운영 로그/감사는 stdout 수집이 정석.
- **백엔드 토글은 모듈 의존과 한 쌍**: `token-store.type=redis`(임의 백엔드)를 켜면 그 구현 모듈(`framework-redis`)을 build.gradle 에 반드시 추가(삼단 토글 tier1). 설정만 바꾸면 빈 없어 기동 실패.
- "앱 기동 로그 정상 ≠ 컨테이너 healthy" — healthcheck 실패는 앱 로그가 아니라 **healthcheck 가 때리는 경로의 응답**(302/401/connection)을 먼저 본다.
- 핵심 교훈: 멀티서비스는 **실제 컨테이너로 한 번 띄워봐야** 드러나는 정합 결함이 있다(단위/슬라이스 테스트로는 안 잡힘). k8s 전 compose 게이트가 그 역할.
- **로컬(레지스트리 없는 kind/minikube) overlay 는 이미지 name 까지 노드 적재명과 1:1**: kustomize `images:` 는 `newTag` 만 주면 name(`registry.example.com/...`)이 그대로 남아 노드 적재 short name(`si-msa/...`)과 불일치 → `ImagePullBackOff`. `newName` 으로 가짜 접두어를 떼야 함. 점검: `kubectl -n si-msa get deploy -o jsonpath='{..image}'` vs `crictl images | grep si-msa`. (이번 세션 정적 점검 선포착.)
- **Docker Desktop 의 kind 모드는 NetworkPolicy 를 집행한다**(standalone kind=kindnet 비집행과 다름): `default-deny-ingress` 가 앱→postgres:5432 를 막아 Flyway `Connect timed out`(08001) → auth/user/admin CrashLoop, gateway 만 생존. `overlays/local/postgres.yaml` 에 `allow-postgres-from-apps`(name=postgres, from component=service, TCP 5432) 추가로 해소. 증상이 `Connect timed out`/08001 이면 인증·DNS 가 아니라 L3/4 차단=NetworkPolicy. (이번 세션 kind 첫 배포 실배포에서 겪음.)
<!-- 갱신 끝 -->
