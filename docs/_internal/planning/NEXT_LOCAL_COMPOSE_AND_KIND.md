# NEXT — compose 그린(✅ 완료) → k8s overlays/local 정합 → kind 배포

> **현재 위치(2026-06-05): compose 풀 그린 ✅ 검증 완료(유일하게 검증된 환경). k8s overlays/local 정합 패치 + auth-server DbAuthenticator 는 _작성만_ 됨(아직 kind 미배포·미검증). kind 클러스터는 Active(3 노드)이나 pod 0(빈 상태). → 이 문서 §4(kind 첫 배포)부터.**
> 전체 맥락 `../HANDOFF.md` §6, 함정 `../../guide/PITFALLS.md` §9, compose 사용법 `../../../deploy/compose/README.md`,
> kind 절차 `../../ops/LOCAL_K8S_TEST.md`, 애드온 `../../ops/K8S_ADDONS.md`.

---

## 0. 지금까지 (Done)

- 신규 `deploy/docker/Dockerfile.build`(A안: 소스부터 컨테이너 안 Gradle 빌드, 단일 공유 builder → 1회 빌드, 팻JAR `java -jar`).
- 신규 `deploy/compose/docker-compose.yml`(4서비스 + PostgreSQL + Redis; env 는 `overlays/local` 과 동일값), `initdb/init.sql`(authdb/sidb/**admindb**), `README.md`.
- **✅ compose 풀 그린 검증 완료** — 받는 쪽 `up -d --build` 후 `docker compose ps` 6컨테이너 전부 `(healthy)`. user/admin 부팅 전 구간(logging→file storage→Flyway(sidb/admindb)→security TokenStore) 통과.

## 1. 적용된 결정/결함 (되돌리지 말 것 — PITFALLS §9)

### 1-A. compose 신설 시 우회한 결함 (3건)

| # | 결함 | compose 우회 | 운영/k8s 영향 |
|---|---|---|---|
| ① | user/admin 가 같은 `sidb` → Flyway 테이블·체크섬 충돌 | admin → `admindb` 분리 | **overlays/local 도 동일 결함** |
| ② | auth-server `prod` 에 `Authenticator` 빈 없음(LocalDemo=@Profile local) | `local,local-postgres` 데모(demo/demo) | **overlays/local 도 동일**(auth 를 prod 로 배포) |
| ③ | `application-local-postgres.yml` 의 `localhost:5432` 하드코딩 | `SPRING_DATASOURCE_URL` env 로 덮음 | 컨테이너/파드 공통 |
| — | 4서비스 병렬 빌드 캐시 경합 | 단일 공유 builder 스테이지(1회 빌드) | — |
| — | Boot4 레이어추출+JarLauncher 미평탄화 | 로컬은 팻JAR `java -jar` | 운영 Dockerfile 은 별개 유지 |

### 1-B. compose 그린화 트리아지로 발견·수정한 결함 (4건, 2026-06-05)

| # | 증상 | 원인 | 수정(적용 형태) | kind 전파 |
|---|---|---|---|---|
| 1 | auth-server 만 `unhealthy`→의존 서비스 부팅 실패 | AS 자체 보안체인이 `/actuator/**` permitAll 누락 → health 302/401 | `AuthorizationServerConfig` 1줄 permitAll (**앱 코드**) | ✅ 자동(같은 이미지) |
| 2 | user/admin `FileNotFoundException: ./logs/*.log` | logback-common 파일 appender 가 비루트/읽기전용 컨테이너 `./logs` 못 씀 | `LOG_DIR=/tmp` (**env**: compose + k8s hardening) | ✅ 자동(hardening 공통 패치) |
| 3 | user/admin `AccessDeniedException: /application/uploads` | local FileStorage 가 `./uploads` 생성 시도(동근원) | `FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads` (**env**) | ⚠️ compose 만 — k8s 는 overlays 에서 admin 별도 |
| 4 | user/admin `TokenStore` 빈 없음 | prod `token-store.type=redis` 인데 `framework-redis` 미의존(삼단 토글 위반) | `build.gradle` 에 `framework-redis` 추가 (**모듈**) | ✅ 자동(같은 이미지) |

> 핵심: 1·2·4 는 **같은 이미지/공통 패치**라 kind 에 자동 전파, 3 만 overlays 에서 admin 에 별도 반영. ①②(1-A)는 overlays/local 에 그대로 남아 있어 아래 §3 에서 처리.

## 2. compose 그린화 — ✅ 완료

`up` 을 순차 트리아지하며 1-B 의 4건을 잡아 6컨테이너 `(healthy)` 달성. 부팅 관문 순서(참고): **logging(LOG_DIR) → file storage(업로드경로) → datasource/Flyway(sidb/admindb) → security(redis TokenStore)**. 우려했던 postgres init 레이스·admindb 볼륨 stale·H2↔PG Flyway 이식성은 실제로 안 터졌다(볼륨 정상). 재현/검증:

```bash
docker compose -f deploy/compose/docker-compose.yml up -d --build
docker compose -f deploy/compose/docker-compose.yml ps          # 6/6 (healthy)
curl -i http://localhost:9000/actuator/health                   # 200 + status UP
```

## 3. k8s `overlays/local` 정합 패치 — 🟡 작성 완료(정적검사만, kind 미배포·미검증)

compose 와 kind 는 같은 이미지 → 1-B 의 1·2·4 자동 전파. **아래는 매니페스트/코드 _작성_ 완료 항목**(YAML/XML/brace/해시 정적검증만 — 실제 apply·기동 검증은 §4):

- [x] **ⓐ admin-service DB 분리** — `overlays/local/postgres.yaml` initdb 에 `admindb`(OWNER siuser) + `kustomization.yaml` 패치로 `admin-service-config.DB_URL=…/admindb`. (운영 overlay 는 admin 전용 DB 별도 provisioning 필요.)
- [x] **ⓑ auth-server 운영 인증기** — base configmap 이 이미 `prod`+`DB_URL=authdb` 라 프로파일 변경 없이, **`DbAuthenticator`**(authdb `app_user` 비번 검증, `@Profile("!local")`+`@ConditionalOnMissingBean(Authenticator.class)`)로 prod 부팅 가능. Flyway **V7** 가 `app_user` + seed **`tester`/`Test1234!`**({bcrypt}) 생성. `@MapperScan` 에 user 패키지 추가. **local 은 LocalDemo(demo/demo) 그대로** — 상호 배타. 실 프로젝트는 자체 Authenticator(LDAP/AD/GPKI) 주입 시 자동 우선.
- [x] **ⓒ admin-service 업로드 경로** — `kustomization.yaml` 패치로 `admin-service-config.FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads`(admin 은 local 파일저장+s3 모듈 없음+readOnlyRootFilesystem). 실운영은 PVC/NAS 또는 file 모듈 비활성.
- [x] **ⓓ user-service 파일저장** — `kustomization.yaml` 패치로 `user-service-config` 를 `FILE_STORAGE_TYPE=local`+`/tmp/uploads`(로컬 테스트). base 는 `s3` 유지(실운영=framework-file-s3 주석 해제 + S3 자격증명).
- [x] actuator permit(앱)·LOG_DIR(hardening)·redis TokenStore(build.gradle) — 이미 반영(매니페스트 추가 작업 없음).

> 변경 파일: 코드 = `services/auth-server` V7·`user/{AppUser,AppUserMapper,DbAuthenticator,ProdAuthenticatorConfig}`·`mapper/AppUserMapper.xml`·`AuthServerApplication`(@MapperScan)·`DbAuthenticatorTest` / 매니페스트 = `overlays/local/{postgres,kustomization}.yaml`.
> 운영(dev/prod) 잔여: admindb 외부 provisioning · admin 업로드 PVC(또는 file 모듈 off) · user-service s3 모듈/자격증명. (테스트 목적 overlays/local 엔 불요.)

## 4. ▶ 지금부터 — kind 첫 배포·검증

> 현재: kind 클러스터 Active(3 노드, v1.34.3), deployment/pod 0. compose 는 정지(회귀용). **여기부터가 미검증 구간** — apply 후 파드 로그로 트리아지.

**0) 받는 쪽 빌드/테스트(먼저)** — 마지막 zip 적용 → `./gradlew :services:auth-server:test :services:auth-server:spotlessApply`(DbAuthenticatorTest 5건) → 4서비스 이미지 빌드(auth-server 는 V7/DbAuthenticator 로 재빌드 필수).


```bash
# 1) 이미지 빌드(로컬 compose 빌드 산출 재사용 가능) 후 노드에 적재
for s in gateway auth-server user-service admin-service; do
  kind load docker-image si-msa/$s:local --name <클러스터명>
done
# 2) 적용 + 관찰
kubectl apply -k deploy/k8s/overlays/local
kubectl -n si-msa get pods -w
# 3) 스모크: 프로브 Ready, port-forward 헬스
kubectl -n si-msa port-forward svc/auth-server 9000:9000 &
curl -i http://localhost:9000/actuator/health     # 200 UP (actuator permit 확인)
```

- overlays/local 은 이미지 `:local`(IfNotPresent) 전제 → 태그를 `si-msa/<svc>:local` 로 맞추거나 overlays images 의 repo 에 맞춰 재태깅.
- Docker Desktop 내장 kind 는 `kind load` 가능. 스모크/트러블슈팅은 `docs/ops/LOCAL_K8S_TEST.md`, 애드온(metrics-server/ServiceMonitor/ingress)은 `docs/ops/K8S_ADDONS.md`.
- 예상 첫 확인 포인트: 모든 파드 Ready(프로브=actuator permit 확인), admin 파드가 admindb 로 Flyway 통과(ⓐ), auth 파드 기동(ⓑ 전략대로), user/admin 업로드 경로 에러 없음(ⓒⓓ).

## 5. 검증 한계

작성환경 Maven Central·릴리스 CDN 차단 → 빌드/`kind`/`kubectl` 직접 실행 불가. 매니페스트는 정적 검증(역할/DB/시크릿/패치 타깃 정합)만 가능하고, 실제 `apply`/파드 로그 트리아지는 받는 쪽에서. 다음 세션은 **kind `get pods`/파드 로그 스샷으로 시작**.
