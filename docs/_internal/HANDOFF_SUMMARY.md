# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**로컬 통합 실행용 Docker Compose 스택 신설(2026-06-05, `deploy/compose/` + `deploy/docker/Dockerfile.build`) — A안(소스부터 컨테이너 안 Gradle 빌드).** 4개 서비스(gateway/auth-server/user-service/admin-service)+PostgreSQL+Redis 를 한 번에 띄우는 compose 작성. **빌드는 성공(4이미지 Built), postgres/redis healthy 확인**. 가져오는 과정에서 **멀티서비스 배포 정합 결함 3건을 발견·우회**(① user/admin 가 같은 sidb 공유 시 Flyway V1/V2 테이블·체크섬 충돌 → admin 을 `admindb` 분리 ② auth-server 의 `prod` 는 Authenticator 빈을 의도적으로 안 넣음(LocalDemo=@Profile("local")) → 로컬은 `local,local-postgres` 데모 경로 ③ `local-postgres.yml` 의 `localhost:5432` 하드코딩 → `SPRING_DATASOURCE_URL` env 로 덮음). **①②는 k8s `overlays/local` 에도 동일 결함**(kind 올리면 같은 자리에서 깨짐). 새 외부 의존성 0, framework/service 소스 무변경(전부 deploy 자산 + 문서).

## 최종 갱신
- 일자: 2026-06-05 · 갱신자: 로컬 compose 스택 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / SF7 / SC 2025.1.1 / Jackson 3 — **스택·build.gradle 무변경**.

## 직전에 한 것 (Done)
- **신규 `deploy/docker/Dockerfile.build`**: 로컬 compose 전용 멀티스테이지. builder 스테이지가 `SERVICE` 인자를 안 써서 4서비스가 **같은 builder 를 공유 → BuildKit 이 1회만 빌드**(병렬 캐시경합·중복컴파일 제거). 4개 bootJar 를 한 번에 빌드 후 런타임은 해당 JAR 만 복사해 `java -jar`. (운영용 `deploy/docker/Dockerfile`=미리빌드 JAR 런타임 전용은 무변경.)
- **신규 `deploy/compose/docker-compose.yml`**: env 는 `overlays/local` ConfigMap/Secret 과 동일값. auth-server 만 `local,local-postgres`(+`SPRING_DATASOURCE_URL=…/authdb`), 나머지 `prod`. admin-service 는 `admindb`. user-service `FILE_STORAGE_TYPE=local`(s3 덮음). 앱 healthcheck=`/actuator/health`(curl, 이미지에 설치).
- **신규 `deploy/compose/initdb/init.sql`**: authdb/sidb + **admindb** 역할/DB 생성.
- **신규 `deploy/compose/README.md`**: 켜는법/쓰는법/끄는법 + 발견 3건 주의.
- **문서 동반 갱신**: PITFALLS §9 신설(compose/배포정합 5건) + 자가진단표 2행 · 본 HANDOFF_SUMMARY · HANDOFF §6 한 줄 · 착수문서 `_internal/planning/NEXT_LOCAL_COMPOSE_AND_KIND.md` 신설 · 00_INDEX/LOCAL_SETUP compose 포인터.

## 현재 상태 (적용/검증)
- **빌드 ✅**(4 이미지 Built, 받는 쪽 실행). **postgres/redis healthy ✅**. JarLauncher 오류(v1 레이어추출 잔재) → `java -jar` 로 교체해 해소. auth-server `Authenticator` 누락 → `local` 프로파일로 해소(수정 적용 직후).
- ⚠️ **auth-server 이후 부팅·user/admin prod 부팅·Flyway 는 미검증** — 받는 쪽에서 그다음 에러가 났고(스샷 다음 세션 제공 예정) 트리아지 대기. 작성환경 Maven Central 차단으로 빌드/기동 직접 실행 불가(정적 작성 + 받는 쪽 확인).

## 바로 다음 할 일 (Next) — 상세 `_internal/planning/NEXT_LOCAL_COMPOSE_AND_KIND.md`
1. **compose 그린 만들기**: 다음 세션 스샷의 `APPLICATION FAILED TO START`/`Caused by:` 트리아지. 유력 후보 = (a) postgres `pg_isready` 가 init.sql 완료 전에 healthy → role/DB 부재 레이스 (b) auth-server Flyway(authdb V1~V6) (c) user/admin prod 첫 부팅(DB/Flyway).
2. **k8s `overlays/local` 정합 패치**(compose 와 동일 결함이라 kind 도 깨짐): admin-service DB 분리(admindb 또는 `spring.flyway.table` 분리) + auth-server 의 prod Authenticator 주입 전략(프로젝트 구현 빈 or 로컬 데모 경로 결정).
3. 그 후 **kind 배포**: 이미지 빌드 → `kind load docker-image` → `kubectl apply -k deploy/k8s/overlays/local` → 스모크.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **user-service ↔ admin-service 는 같은 DB 공유 불가** — 두 서비스 Flyway `V1__init` 이 동일 테이블(users/roles/…) 생성 + 같은 `flyway_schema_history` → 둘째 서비스 부팅 실패. 서비스별 DB 분리(admin=admindb) [PITFALLS §9, §6].
- **auth-server 는 prod 단독 부팅 불가** — `Authenticator` 빈은 `LocalDemo`(@Profile("local"))만 제공. 실배포는 프로젝트가 DB/LDAP 구현 주입, 로컬은 `local,local-postgres` 데모(demo/demo) [PITFALLS §9, §5].
- **`local-postgres.yml` 은 `localhost:5432` 하드코딩**(user-service 와 달리 `${DB_URL}` 없음) → 컨테이너에선 `SPRING_DATASOURCE_URL` env 로 덮어야 함 [PITFALLS §9].
- **compose 다중 서비스 빌드는 단일 공유 builder 스테이지로** — 서비스별 `RUN --mount=type=cache,target=/root/.gradle` 동시쓰기는 Gradle 캐시 경합으로 깨짐. builder 를 `SERVICE` 무관하게 만들어 BuildKit 이 1회만 실행 [PITFALLS §9].
- **로컬 이미지는 `java -jar` 팻JAR** — Boot 4 레이어추출(`jarmode=tools extract`)+`JarLauncher` 레이아웃은 추출본을 평탄화해 클래스패스에 합쳐야 함(안 하면 `ClassNotFoundException: JarLauncher`). 로컬은 팻JAR 직실행이 단순·견고 [PITFALLS §9].
<!-- 갱신 끝 -->
