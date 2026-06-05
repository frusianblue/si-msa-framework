# NEXT — 로컬 통합 실행(compose) 그린화 → k8s overlays/local 정합 → kind 배포

> 착수 문서. 직전 세션(2026-06-05)에서 로컬 Docker Compose 스택을 신설하고 빌드까지 통과시켰다.
> 이 문서는 **다음 세션이 바로 이어서** compose 를 완전히 그린으로 만들고, 같은 결함을 k8s 로 전파하지
> 않도록 정합을 맞춘 뒤 kind 에 올리는 순서를 담는다. 전체 맥락은 `../HANDOFF.md` §7, 함정은
> `../../guide/PITFALLS.md` §9, 사용법은 `../../../deploy/compose/README.md`.

---

## 0. 지금까지 (Done)

- 신규 `deploy/docker/Dockerfile.build`(A안: 소스부터 컨테이너 안 Gradle 빌드, 단일 공유 builder → 1회 빌드, 팻JAR `java -jar`).
- 신규 `deploy/compose/docker-compose.yml`(4서비스 + PostgreSQL + Redis; env 는 `overlays/local` 과 동일값).
- 신규 `deploy/compose/initdb/init.sql`(authdb/sidb/**admindb**), `deploy/compose/README.md`.
- **빌드 ✅(4 이미지 Built)·postgres/redis healthy ✅**. JarLauncher 오류 해소(→`java -jar`). auth-server Authenticator 누락 해소(→`local,local-postgres`).

## 1. 적용된 결정/우회 (되돌리지 말 것 — PITFALLS §9)

| # | 결함 | compose 우회 | 운영/k8s 영향 |
|---|---|---|---|
| ① | user/admin 가 같은 `sidb` → Flyway 테이블·체크섬 충돌 | admin → `admindb` 분리 | **overlays/local 도 동일 결함** |
| ② | auth-server `prod` 에 `Authenticator` 빈 없음(LocalDemo=@Profile local) | `local,local-postgres` 데모(demo/demo) | **overlays/local 도 동일**(auth 를 prod 로 배포) |
| ③ | `application-local-postgres.yml` 의 `localhost:5432` 하드코딩 | `SPRING_DATASOURCE_URL` env 로 덮음 | 컨테이너/파드 공통 |
| — | 4서비스 병렬 빌드 캐시 경합 | 단일 공유 builder 스테이지(1회 빌드) | — |
| — | Boot4 레이어추출+JarLauncher 미평탄화 | 로컬은 팻JAR `java -jar` | 운영 Dockerfile 은 별개 유지 |

## 2. 바로 다음 (Next) — compose 그린화

받는 쪽에서 `up` 후 멈춘 컨테이너 로그를 트리아지한다:

```bash
docker compose -f deploy/compose/docker-compose.yml ps
docker compose -f deploy/compose/docker-compose.yml logs --no-color auth-server  | tail -n 100
docker compose -f deploy/compose/docker-compose.yml logs --no-color user-service | tail -n 100
docker compose -f deploy/compose/docker-compose.yml logs --no-color admin-service| tail -n 100
```

`APPLICATION FAILED TO START` / `Caused by:` 윗부분을 보고 분기. **유력 후보**:

1. **Postgres init 레이스** — 증상: `database "authdb" does not exist` / `role "authuser" does not exist`. 원인: `pg_isready` 가 `init.sql`(역할/DB 생성) 완료 **전**에 healthy. 해결안: postgres healthcheck 를 `pg_isready -d authdb -U authuser` 처럼 **앱 DB 기준**으로 바꾸거나, init 완료 마커(예: `psql -c '\dt'` on admindb) 확인. (init.sql 은 빈 데이터 디렉터리에서 1회만 실행됨 — `down -v` 후 재기동 시 재실행.)
2. **auth-server Flyway** — `FlywayException`/`Migration … failed`(authdb V1~V6). local 프로파일도 base 에서 flyway enabled → 실 PG 마이그레이션. H2 전용 SQL 이 PG 에서 깨지는지 확인(PITFALLS §6 H2↔PG 이식성).
3. **user/admin prod 첫 부팅** — 이번이 prod 프로파일 첫 실기동. DB 연결/Flyway/시크릿 가드(`AesMasterKeySafetyGuard`·`JwtSecretSafetyGuard`) 확인. secrets-local 값은 가드 통과하도록 설계됨(AES 32바이트·강한 JWT).

> 각 수정은 가능한 한 **compose env/healthcheck 선에서** 해결하고, 소스/프로파일 변경이 필요하면 그게 곧 framework 결함이므로 PITFALLS + 해당 서비스 문서에 반영.

## 3. 그 다음 — k8s `overlays/local` 정합 패치

compose 에서 우회한 ①②가 **kind 에도 그대로** 있으므로, kind 올리기 전에 overlays 를 맞춘다:

- **① admin DB 분리**: `deploy/k8s/overlays/local/postgres.yaml` initdb 에 `admindb` 추가 + admin-service `DB_URL` 을 `…/admindb` 로 패치(base 는 sidb). 또는 base/admin-service 에 `spring.flyway.table` 분리 env. (운영 overlay 도 동일 원칙 — admin 전용 DB 권장.)
- **② auth-server Authenticator**: 운영 배포에서 auth 를 `prod` 로 띄우려면 **프로젝트 Authenticator 구현 빈**이 필요. 로컬 검증 목적이면 overlays/local 의 auth-server 를 `local,local-postgres` 로 띄우는 패치(+`SPRING_DATASOURCE_URL`). 운영 경로(프로젝트 구현 주입)와 로컬 데모 경로 중 무엇을 표준으로 둘지 결정 후 문서화(`docs/modules/AUTH_SERVER.md`).

## 4. 그 다음 — kind 배포

```bash
# 이미지 빌드(로컬 compose 빌드 산출 재사용 가능) 후 노드에 적재
for s in gateway auth-server user-service admin-service; do
  kind load docker-image si-msa/$s:local --name <클러스터명>   # 컨텍스트 docker-desktop
done
kubectl apply -k deploy/k8s/overlays/local
kubectl -n si-msa get pods -w
```

- overlays/local 은 이미지 `:local`(IfNotPresent) 전제 → 태그를 `si-msa/<svc>:local` 로 맞추거나 overlays images 의 repo(`registry.example.com/si-msa/<svc>`)에 맞춰 재태깅.
- Docker Desktop 내장 kind 는 `kind load` 가능. 스모크/트러블슈팅은 `docs/ops/LOCAL_K8S_TEST.md`.

## 5. 검증 한계

작성환경 Maven Central 차단 → 빌드/기동 직접 실행 불가. compose YAML·env·DB 라우팅은 정적 검증 완료, 실제 `up`/`apply` 와 부팅 로그 트리아지는 받는 쪽에서. 다음 세션은 **그 로그 스샷으로 시작**.
