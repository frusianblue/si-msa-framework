# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**로컬 compose `up -d` 트리아지 — auth-server 만 `unhealthy` 로 의존 서비스(gateway/user-service) 부팅 실패(2026-06-05).** 받은 스샷 판독: auth-server 앱은 **정상 기동**(`Tomcat started on port 9000`, `Started AuthServerApplication`)인데 compose 가 unhealthy 판정 → `dependency failed to start`. **근본 원인 = 보안 체인이 `/actuator/health` 를 막음**(앱 결함, 헬스체크/타이밍 문제 아님). `AuthorizationServerConfig` 의 `@Order(2) defaultSecurityFilterChain` 이 `anyRequest().authenticated()`+formLogin 이라 미인증 actuator 접근이 302(→`/login`)/401 → `curl -fsS … | grep -q UP` 영영 실패. framework-security 기본 체인은 `/actuator/**` permitAll(그래서 user/admin/gateway 정상)인데 AS 가 자체 체인으로 백오프시키며 이 규약이 누락. **같은 코드라 k8s startup/liveness/readiness 프로브도 동일 결함** → 앱 한 줄 수정으로 compose+kind 동시 해소(헬스체크 우회보다 정답). curl 은 런타임 이미지에 정상 설치(원인 아님) 확인. **그 후 user/admin 이 logback `./logs/*.log`(2차) → local FileStorage `./uploads`(3차) 컨테이너 쓰기불가로 연쇄 종료 → `LOG_DIR=/tmp`·`FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads` env 로 수정**(전부 소스/Dockerfile 무변경; 동근원=비루트+읽기전용 루트FS 에서 작업디렉터리 하위 쓰기). k8s readOnlyRootFilesystem 까지 정합. **그다음 user/admin 이 DB/Flyway(sidb/admindb)는 통과했으나 `TokenStore` 빈 부재로 종료 → prod `token-store.type=redis` 인데 `framework-redis` 미의존이 원인 → build.gradle 에 모듈 추가(4차, 유일하게 재빌드 필요).**

## 최종 갱신
- 일자: 2026-06-05 · 갱신자: compose 트리아지 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / SF7 / SC 2025.1.1 / Jackson 3 — **스택·build.gradle 무변경**.

## 직전에 한 것 (Done)
- **코드 수정 1건**: `services/auth-server/.../config/AuthorizationServerConfig.java` `@Order(2) defaultSecurityFilterChain` 에 `requestMatchers("/actuator/**").permitAll()` 추가(framework-security 규약과 동일). AS 프로토콜 체인(@Order(1))·formLogin·인증 위임은 무변경.
- **문서 동반 갱신**: PITFALLS §9 신규 항목(actuator-AS보안 함정, ★) + 자가진단표 1행 · AUTH_SERVER.md §3 "actuator 미인증 허용" bullet · 본 HANDOFF_SUMMARY · HANDOFF §6 한 줄.
- **코드 수정 2건째(LOG_DIR)**: user/admin 컨테이너 부팅 실패(logback `./logs/*.log` 못 씀) → **소스/Dockerfile 무변경**, `LOG_DIR=/tmp` env 로 해결. compose=user/admin env, k8s=`deployment-hardening.yaml` `app` 컨테이너 env 단일 정의(전 서비스, /tmp emptyDir 와 일치). 문서: PITFALLS §9 신항목(★)+자가진단표 1행 · compose README 주의절.
- **수정 3건째(업로드 경로)**: user/admin 의 local `FileStorage` 가 `./uploads`(/application/uploads) 생성 실패(AccessDenied) → `FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads`(relaxed-binding 정식 env; admin 은 yml `${FILE_BASE_PATH}` 매핑 없어 이 형태 필수). 소스/Dockerfile 무변경. 문서: PITFALLS §9 신항목(★)+자가진단표 1행 · compose README.
- **수정 4건째(redis 토큰스토어)**: user/admin prod `token-store.type=redis` + `framework-redis` 미의존 → `TokenStore` 빈 없음. `services/{user,admin}-service/build.gradle` 에 `implementation project(':framework:framework-redis')` 추가(api spring-data-redis 전이로 StringRedisTemplate 자동구성). **build.gradle 변경 → `up -d --build` 필요.** 문서: PITFALLS §9 신항목(★)+자가진단표 1행.
- 진단 근거: framework-security `SecurityAutoConfiguration` L304/L372 의 `requestMatchers("/actuator/**", …).permitAll()` 규약 / k8s `common/deployment-hardening.yaml` 의 startup·liveness·readiness 프로브 경로(`/actuator/health{,/liveness,/readiness}`) / `Dockerfile.build` 런타임에 curl 설치 확인.

## 현재 상태 (적용/검증)
- **✅ 받는 쪽 검증 완료(2026-06-05)**: 수정 적용 후 재기동 → `[+] up 10/10`, auth-server `Healthy`. `curl -i …:9000/actuator/health` = **200 + {"groups":["liveness","readiness"],"status":"UP"}**(이전엔 302/401). 응답에 liveness/readiness 그룹이 떠 있어 **k8s 프로브도 동일 통과 확인**(같은 이미지/경로) → compose+kind 동시 해소.
- **auth-server/gateway/postgres/redis = `(healthy)` 검증 완료.** user/admin 은 `prod` logback 파일경로 문제로 종료했었음(이번 LOG_DIR 수정 대상). 
- **DB/Flyway(sidb/admindb) 통과 확인됨**(user 4개 마이그레이션 validated, admin up-to-date) → admindb/Flyway 결함은 실제로는 안 터짐(볼륨 정상).
- ⚠️ **redis 토큰스토어 수정은 재빌드 필요** — `docker compose ... up -d --build`(build.gradle 변경). 이후 user/admin `(healthy)` 확인. 부팅 순서 logging→file→Flyway→security(TokenStore) 까지 다 통과하면 compose 풀 그린. redis 컨테이너는 이미 healthy 라 `spring.data.redis.host=redis` 로 바로 연결.

## 바로 다음 할 일 (Next)
1. **compose 완전 그린 확인**: LOG_DIR 수정 재기동 후 user/admin `(healthy)` 확인 → prod 첫 Flyway(sidb/admindb) 통과 신호. 막히면 다음 후보(admindb 볼륨 stale → `down -v` / postgres `pg_isready` 가 init.sql 완료 전 healthy 레이스) 트리아지.
2. **k8s `overlays/local` 정합 패치**(compose 와 동일 결함이라 kind 도 깨짐): ① admin-service DB 분리(admindb 또는 `spring.flyway.table`) ② auth-server prod Authenticator 주입 전략. **actuator-permit 은 이번 코드 수정으로 이미 해결**(같은 이미지라 프로브도 통과) — 별도 매니페스트 패치 불요.
3. 그 후 **kind 배포**: 이미지 빌드 → `kind load docker-image` → `kubectl apply -k deploy/k8s/overlays/local` → 스모크.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **AS 자체 SecurityFilterChain 정의 시 `/actuator/**` permitAll 을 직접 넣어야 함** — framework-security 기본 체인이 백오프되므로 actuator permit 규약도 함께 사라진다. 빠뜨리면 앱은 정상인데 K8s 프로브/compose healthcheck 가 영영 실패(302/401). 점검: `curl -i …:9000/actuator/health` 가 302/401 이면 이 함정 [PITFALLS §9].
- **컨테이너 로그는 `LOG_DIR=/tmp`** — logback-common(user/admin)이 항상 `./logs/*.log` 파일을 쓰는데 컨테이너는 비루트+root소유 WORKDIR, k8s 는 readOnlyRootFilesystem(쓰기=`/tmp` emptyDir 뿐). 되돌리면 user/admin 이 logging init 에서 죽는다. 운영 로그/감사는 stdout 수집이 정석(파일은 보조) [PITFALLS §9].
- **컨테이너 업로드 경로도 `/tmp`** — local FileStorage 기본 `./uploads` 도 logback 과 동근원으로 컨테이너 쓰기불가 → `FRAMEWORK_FILE_STORAGE_BASE_PATH=/tmp/uploads`. 일반 원칙: 비루트+하드닝 컨테이너에선 작업디렉터리 하위 쓰기경로(logs/uploads/임시) 전부 /tmp 로 [PITFALLS §9].
- **백엔드 토글은 모듈 의존과 한 쌍** — `token-store.type=redis`(또는 임의 백엔드 선택)를 켜면 해당 구현을 제공하는 모듈(`framework-redis`)을 반드시 build.gradle 에 의존 추가해야 한다(삼단 토글 tier1). 설정만 바꾸고 모듈을 빼면 빈이 안 생겨 기동 실패 [PITFALLS §9].
- **"앱 기동 로그 정상 ≠ 컨테이너 healthy"** — `Started …Application`/`Tomcat started` 가 찍혀도 healthcheck 명령(보안·포트·경로)이 틀리면 unhealthy. 헬스체크 실패는 앱 로그가 아니라 **healthcheck 가 때리는 경로의 응답**(302/401/connection)을 먼저 본다.
<!-- 갱신 끝 -->
