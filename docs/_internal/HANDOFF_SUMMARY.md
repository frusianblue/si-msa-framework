# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->

## 이번 세션 한 줄 요약
**▶ kind "OAuth2 클라이언트 등록 → 토큰 플로우"(=DbAuthenticator 운영 인증 경로) A안 코드/테스트 전달(2026-06-06).** prod 클라이언트 0건(설계: LocalDemo `@Profile("local")`)을 깨지 않고, **프로파일 비의존 옵트인 시더** `SmokeClientSeeder`(`@ConditionalOnProperty framework.auth.seed-smoke-client`, 기본 false)를 추가 — 켜면 `demo-web`(public+PKCE)·`demo-service`(client_credentials)를 `repo.save()` 로 멱등 등록. **DbAuthenticator·프로파일 무변경.** overlays/local `auth-server-config` 가 플래그를 on(kind 검증용, dev/prod 미적용). 자동 검증 등가물 = `SmokeClientDbAuthFlowTest`(`@ActiveProfiles("smoketest")` = `!local` → DbAuthenticator 활성 + prod 하드닝 회피; `tester`/`Test1234!`(authdb `app_user`) 폼 로그인 → authorization_code+PKCE → access/id_token, **sub=`tester`** 단언). 작성환경 Central 차단 → **받는 쪽 검증 대기**(`:services:auth-server:test --tests '*SmokeClientDbAuthFlowTest'` + 이미지 재빌드→kind 절차). 스펙 `_internal/planning/NEXT_KIND_AUTH_TOKEN_FLOW.md`(A안 전달 완료), 함정 `PITFALLS §9`.

## 최종 갱신
- 일자: 2026-06-06 · 갱신자: smoke 시더 + DbAuthenticator 토큰 플로우 A안 전달 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / SF7 / SC 2025.1.1 / Jackson 3 — 스택 무변경.

## 직전에 한 것 (Done)
**A안(prod-안전 smoke 시더) 전달.** 변경 파일:
| 파일 | 내용 |
|---|---|
| `services/auth-server/.../config/SmokeClientSeeder.java` (신규) | 옵트인 시더. `@ConditionalOnProperty(framework.auth.seed-smoke-client, havingValue=true)` 가드(기본 off, 삼단 토글 정신) + `ApplicationRunner` 가 `findByClientId==null` 일 때만 `repo.save()`(멱등). demo-web=public+PKCE+AC+RT+openid/profile, demo-service=client_secret_basic+client_credentials+api.read. LocalDemo 와 동일 식별자·설정. |
| `services/auth-server/.../resources/application.yml` | `framework.auth.seed-smoke-client: ${FRAMEWORK_AUTH_SEED_SMOKE_CLIENT:false}` 노출(문서·바인딩). |
| `services/auth-server/src/test/resources/application-smoketest.yml` (신규) | `!local` + 플래그 on + H2(`authdb_smoke`) — prod 하드닝 회피하며 DbAuthenticator 활성. |
| `services/auth-server/src/test/.../e2e/SmokeClientDbAuthFlowTest.java` (신규) | 시더 등록 단언 + `tester` DbAuthenticator authorization_code+PKCE → access/id_token(sub=tester) + demo-service client_credentials. |
| `deploy/k8s/overlays/local/kustomization.yaml` | `auth-server-config` ConfigMap 패치로 `FRAMEWORK_AUTH_SEED_SMOKE_CLIENT: "true"`(kind 검증용). |
| 문서 | `AUTH_SERVER.md` §6.5(prod 등록/시더 절차) + §8 갱신 · `PITFALLS §9`(시더 = 옵트인 해소 경로) · 스펙 상태(A안 전달). |

- 정적 교차검증: 중괄호/괄호 균형 OK, `com.fasterxml` 0(Jackson 3), `ResourceServerJwtVerifier` 7-arg 생성자/`Verified(userId,jti,roles)` 일치, 시더 import 는 LocalDemo 와 동일(컴파일 검증된 패턴). 테스트는 확정 통과 중인 `OidcIdTokenIssuanceTest` 를 템플릿으로(MockMvc 폼로그인+openid auth_time 은 `FrameworkAuthenticationProvider` 의 FACTOR_PASSWORD 로 해소됨 — 동일 근거).

## 현재 상태 (적용/검증)
- **✅ kind 첫 배포 = 그린**(이전 세션): si-msa ns 6파드 `1/1 Running`.
- **▶ smoke 시더 + DbAuthenticator 토큰 플로우 = 코드/테스트 전달, 받는 쪽 검증 대기**: 작성환경 Central 차단으로 Gradle/kubectl 직접 실행 불가.
- 작성환경 Maven Central·릴리스 CDN 차단 → 빌드/kubectl 직접 실행 불가(정적 작성 + 받는 쪽 실행).

## 바로 다음 할 일 (Next)
1. **받는 쪽 검증**: `./gradlew :services:auth-server:test --tests '*SmokeClientDbAuthFlowTest'`(스모크 시더+DbAuthenticator 자동 증명). 통과 시 토큰 플로우 Done.
2. **kind 실배포 마감(선택)**: `docker compose -f deploy/compose/docker-compose.yml build auth-server` → `kubectl -n si-msa rollout restart deploy/auth-server` → §6.5 절차로 authorization_code+PKCE 수동 1회 확인.
3. **commit/push**(게이트웨이 런타임 점검 + 이번 변경 누적).
- 백로그: confidential `demo-rp` 전체 콜백 흐름(`NEXT_RP_IDTOKEN_LINK §B`), 모듈 README 샘플 코드, CI 게이트 + Jacoco aggregate, K8s addons.

## 이번 세션에서 새로 박힌 함정/원칙 (되돌리지 말 것)
- **prod 클라이언트 0건은 설계 — 깨지 말고 옵트인 시더로 우회**: `SmokeClientSeeder`(`framework.auth.seed-smoke-client`, 기본 false)는 프로파일/DbAuthenticator 를 건드리지 않는 별도 플래그 가드. local 의 `LocalDemo`(@Profile("local")) 분리 유지.
- **`oauth2_registered_client` SQL INSERT 금지**: `client_settings`/`token_settings` 가 SAS 전용 Jackson(`@class`) JSON → 반드시 `repo.save()`.
- **DbAuthenticator 테스트는 `!local` 프로파일 필요**(local 은 LocalDemo demo/demo). prod 는 하드닝(DB env 필수)이라 깨짐 → 전용 `smoketest`(H2, 플래그 on, 하드닝 회피).
- **플래그를 켜도 시더 클래스 포함 이미지 재빌드 1회 필요**(env 수정과 달리 코드 변경).
- (이전 세션 유지) Docker Desktop kind = NetworkPolicy 집행 + registry-mirror 자동노출 / initdb 1회성 / local overlay `images.newName` / AS `/actuator/**` permitAll.

<!-- 갱신 끝 -->
