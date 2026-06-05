# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**로컬 compose `up -d` 트리아지 — auth-server 만 `unhealthy` 로 의존 서비스(gateway/user-service) 부팅 실패(2026-06-05).** 받은 스샷 판독: auth-server 앱은 **정상 기동**(`Tomcat started on port 9000`, `Started AuthServerApplication`)인데 compose 가 unhealthy 판정 → `dependency failed to start`. **근본 원인 = 보안 체인이 `/actuator/health` 를 막음**(앱 결함, 헬스체크/타이밍 문제 아님). `AuthorizationServerConfig` 의 `@Order(2) defaultSecurityFilterChain` 이 `anyRequest().authenticated()`+formLogin 이라 미인증 actuator 접근이 302(→`/login`)/401 → `curl -fsS … | grep -q UP` 영영 실패. framework-security 기본 체인은 `/actuator/**` permitAll(그래서 user/admin/gateway 정상)인데 AS 가 자체 체인으로 백오프시키며 이 규약이 누락. **같은 코드라 k8s startup/liveness/readiness 프로브도 동일 결함** → 앱 한 줄 수정으로 compose+kind 동시 해소(헬스체크 우회보다 정답). curl 은 런타임 이미지에 정상 설치(원인 아님) 확인.

## 최종 갱신
- 일자: 2026-06-05 · 갱신자: compose 트리아지 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / SF7 / SC 2025.1.1 / Jackson 3 — **스택·build.gradle 무변경**.

## 직전에 한 것 (Done)
- **코드 수정 1건**: `services/auth-server/.../config/AuthorizationServerConfig.java` `@Order(2) defaultSecurityFilterChain` 에 `requestMatchers("/actuator/**").permitAll()` 추가(framework-security 규약과 동일). AS 프로토콜 체인(@Order(1))·formLogin·인증 위임은 무변경.
- **문서 동반 갱신**: PITFALLS §9 신규 항목(actuator-AS보안 함정, ★) + 자가진단표 1행 · AUTH_SERVER.md §3 "actuator 미인증 허용" bullet · 본 HANDOFF_SUMMARY · HANDOFF §6 한 줄.
- 진단 근거: framework-security `SecurityAutoConfiguration` L304/L372 의 `requestMatchers("/actuator/**", …).permitAll()` 규약 / k8s `common/deployment-hardening.yaml` 의 startup·liveness·readiness 프로브 경로(`/actuator/health{,/liveness,/readiness}`) / `Dockerfile.build` 런타임에 curl 설치 확인.

## 현재 상태 (적용/검증)
- **✅ 받는 쪽 검증 완료(2026-06-05)**: 수정 적용 후 재기동 → `[+] up 10/10`, auth-server `Healthy`. `curl -i …:9000/actuator/health` = **200 + {"groups":["liveness","readiness"],"status":"UP"}**(이전엔 302/401). 응답에 liveness/readiness 그룹이 떠 있어 **k8s 프로브도 동일 통과 확인**(같은 이미지/경로) → compose+kind 동시 해소.
- ⚠️ 잔여 확인: admin/user/gateway 가 스샷 시점엔 `Started`(start_period 90s 창). `docker compose ps` 로 `(healthy)` 전환 확인 권장 — 이게 **이전 미검증 구간(user/admin prod 첫 Flyway on sidb/admindb)** 의 그린 신호. unhealthy 로 머물면 해당 서비스 로그 트리아지.

## 바로 다음 할 일 (Next)
1. **compose 완전 그린 확인**: auth-server Healthy 후 gateway/user-service/admin-service prod 부팅·Flyway 통과 확인. 막히면 스샷 트리아지(유력 후보: postgres `pg_isready` 가 init.sql 완료 전 healthy → role/DB 레이스 / user·admin prod 첫 Flyway).
2. **k8s `overlays/local` 정합 패치**(compose 와 동일 결함이라 kind 도 깨짐): ① admin-service DB 분리(admindb 또는 `spring.flyway.table`) ② auth-server prod Authenticator 주입 전략. **actuator-permit 은 이번 코드 수정으로 이미 해결**(같은 이미지라 프로브도 통과) — 별도 매니페스트 패치 불요.
3. 그 후 **kind 배포**: 이미지 빌드 → `kind load docker-image` → `kubectl apply -k deploy/k8s/overlays/local` → 스모크.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **AS 자체 SecurityFilterChain 정의 시 `/actuator/**` permitAll 을 직접 넣어야 함** — framework-security 기본 체인이 백오프되므로 actuator permit 규약도 함께 사라진다. 빠뜨리면 앱은 정상인데 K8s 프로브/compose healthcheck 가 영영 실패(302/401). 점검: `curl -i …:9000/actuator/health` 가 302/401 이면 이 함정 [PITFALLS §9].
- **"앱 기동 로그 정상 ≠ 컨테이너 healthy"** — `Started …Application`/`Tomcat started` 가 찍혀도 healthcheck 명령(보안·포트·경로)이 틀리면 unhealthy. 헬스체크 실패는 앱 로그가 아니라 **healthcheck 가 때리는 경로의 응답**(302/401/connection)을 먼저 본다.
<!-- 갱신 끝 -->
