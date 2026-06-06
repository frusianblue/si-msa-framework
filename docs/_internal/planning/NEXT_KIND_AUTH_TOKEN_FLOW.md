# NEXT_KIND_AUTH_TOKEN_FLOW.md — kind 위 OAuth2 클라이언트 등록 → 토큰 플로우(DbAuthenticator 운영 경로 실증)

> # ✅✅ 완료 / ARCHIVED (2026-06-06) — 자동 테스트 + kind 실배포 모두 검증 끝
> **이 스펙은 종료됐다.** A안(prod-안전 smoke 시더) 구현·검증 완료:
> - `SmokeClientDbAuthFlowTest`(`@ActiveProfiles("smoketest")`) 통과 — `tester`(authdb `app_user`) 폼 로그인(DbAuthenticator) → authorization_code+PKCE → access/id_token, sub=`tester`.
> - **kind 실배포 확인**: `SmokeClientSeeder`(`framework.auth.seed-smoke-client=true`, overlays/local)가 prod 프로파일 위에서 `demo-web`(authorization_code+refresh_token)·`demo-service`(client_credentials)를 `oauth2_registered_client` 에 등록(2 rows) — 즉 DbAuthenticator 운영 인증 경로의 마지막 한 칸이 닫혔다.
> - 배포 트리아지로 드러난 **빌드/배포 함정 2건**(둘 다 코드 아님, 이미지 전달 문제) → `PITFALLS §1`(`.dockerignore` 부재 → stale jar)·`§9`(같은 `:local` 태그 + `IfNotPresent` → 노드 containerd 옛 digest 재사용). `.dockerignore` 신규 추가로 ①은 영구 해소.
> **다음 섹션 = confidential `demo-rp` 전체 콜백 흐름** → `NEXT_RP_IDTOKEN_LINK.md` §B (`OAuthClient.exchangeCodeForTokens`, `client_secret_post`).
> _이 파일은 `docs/_internal/archive/` 로 옮겨도 무방(housekeeping). 아래는 착수~완료 시점의 원본 기록(이력 보존)._

> 상태: **✅ 완료(2026-06-06).** 전달물: `SmokeClientSeeder`(옵트인 시더) + `application-smoketest.yml` + `SmokeClientDbAuthFlowTest`(e2e) + overlays/local 플래그 + `.dockerignore` + 문서. 자동 테스트 통과 + kind 실배포 등록 2 rows 확인.
> 선행: **kind 첫 배포 ✅ 완료**(2026-06-06, si-msa ns 6파드 `1/1 Running`). 이 문서는 그 위에서 **인증·토큰 플로우**를 닫는다.
> 전체 맥락 `../HANDOFF.md` §6, 함정 `../../guide/PITFALLS.md` §9, kind 절차/트러블슈팅 `../../ops/LOCAL_K8S_TEST.md`,
> 직전 완료 스펙 `NEXT_LOCAL_COMPOSE_AND_KIND.md`(ARCHIVED), RP 연계(완료) `NEXT_RP_IDTOKEN_LINK.md`.

---

## 0. 왜 이 단계가 필요한가 (현재 사실)

kind 배포는 그린이지만 **마지막 한 칸 = authorization_code+PKCE 토큰 발급**이 미실증이다. 이유는 버그가 아니라 **설계**:

- `services/auth-server/.../config/LocalDemo.java` 가 `@Profile("local")` 이라 데모 클라이언트(`demo-web` public+PKCE, `demo-service` client_credentials)를 **local 에서만** 시드한다.
- kind 는 `SPRING_PROFILES_ACTIVE=prod` → LocalDemo 비활성 → `oauth2_registered_client` **0 rows**.
- `RegisteredClientRepository` = `JdbcRegisteredClientRepository`(`AuthorizationServerConfig`). DB 가 비면 그대로 0건. 프레임워크는 **prod 클라이언트 등록을 프로젝트 책임**으로 둔다.

→ authorization_code 진입(`/oauth2/authorize`)이 `invalid_client` 로 막혀 **로그인 폼까지 못 간다**. 사용자 `tester`(authdb `app_user`, `{bcrypt}` seed)는 준비됐고, `DbAuthenticator`(`@Profile("!local")`+`@ConditionalOnMissingBean`)도 떠 있다 — **클라이언트만 등록되면** 실 인증 경로가 끝까지 돈다.

### ⚠️ 절대 하지 말 것
**`oauth2_registered_client` 에 SQL INSERT 수동 등록 금지.** `client_settings`/`token_settings` 컬럼은 SAS 전용 Jackson 모듈이 `@class` 타입 메타데이터를 박아 직렬화한 JSON 이라, 손으로 만들면 `JdbcRegisteredClientRepository` 의 row mapper 가 역직렬화에서 깨진다. **반드시 `RegisteredClientRepository.save()`**(앱 코드)로 등록해 올바른 JSON 을 생성한다. (PITFALLS §9 참조)

---

## 1. 방안

### A안 (권장) — prod-안전 smoke/demo 클라이언트 시더  ✅ **전달 완료(2026-06-06)**
`@Profile("local")` 이 아닌 **별도 플래그**로 가드해 prod 에서도 옵트인 등록. **DbAuthenticator·인증 백엔드는 그대로** 둔다(프로파일을 건드리지 않으므로).

- 신규(또는 LocalDemo 분리): `config/SmokeClientSeeder`
  - 가드: `@ConditionalOnProperty(name = "framework.auth.seed-smoke-client", havingValue = "true")` (기본 false — 삼단 토글 정신).
  - `ApplicationRunner` 로 `repo.findByClientId("demo-web") == null` 일 때만 `repo.save(...)`:
    - `demo-web`: `ClientAuthenticationMethod.NONE`(public) + PKCE, grant `AUTHORIZATION_CODE`+`REFRESH_TOKEN`, scope `openid`/`profile`, redirect_uri = **kind 검증용 값**(아래 §2 redirect 주의).
    - (선택) `demo-service`: `CLIENT_SECRET_BASIC` + `CLIENT_CREDENTIALS` — 폼 로그인 없이 토큰 1줄 확인용(단 이건 클라이언트 인증이지 DbAuthenticator 경로 아님).
  - 인코더: 위임 인코더(`{bcrypt}`) 그대로. client_secret 은 `encoder.encode(...)`.
- 배선: overlays/local 의 `auth-server-config` ConfigMap 에 `FRAMEWORK_AUTH_SEED_SMOKE_CLIENT: "true"` 추가(또는 secret 무관 평문 env). 기본 false 라 dev/prod 무영향.
- ⚠️ **이미지 재빌드 1회 필요**(auth-server). Docker Desktop kind 면 빌드 후 적재 불필요(registry-mirror 자동노출) — `docker build -t si-msa/auth-server:local …` 후 `kubectl -n si-msa rollout restart deploy/auth-server`.
- **로드맵 `demo-rp`(confidential, `client_secret_post`) 등록과 겹침** → 이 시더를 출발점으로 RP 풀플로우(B안, NEXT_RP_IDTOKEN_LINK 백로그)로 확장 가능.

### B안 (빠른 확인) — 클라이언트 없이 DbAuthenticator 만
authorization_code 없이 폼 로그인만 태워 **tester 인증이 authdb 로 도는지** 확인(토큰 X, 인증 O).
```bash
kubectl -n si-msa port-forward svc/auth-server 9000:9000 &
# 로그인 페이지에서 세션쿠키+CSRF
curl -c cj.txt -s localhost:9000/login | grep -o 'name="_csrf" value="[^"]*"'
# 폼 로그인 (성공=302 "/" , 실패=302 /login?error)
curl -b cj.txt -s -o /dev/null -w "%{http_code} -> %{redirect_url}\n" \
  --data-urlencode "username=tester" --data-urlencode "password=Test1234!" \
  --data-urlencode "_csrf=<위 값>" localhost:9000/login
```
> 주의(메모리 §): MockMvc 폼 로그인은 세션 이벤트 미발생으로 `SessionRegistry` 가 비어 토큰 단계에서 `authenticationTime cannot be null` 이 나는 함정이 있으나, **실 서블릿(curl/브라우저) 요청은 세션 이벤트가 발생**하므로 폼 로그인 자체엔 무관.

---

## 2. 검증 절차 (A안 기준)

1. **시더 추가 + ConfigMap 플래그 on** → auth-server 이미지 재빌드 → `rollout restart deploy/auth-server`.
2. 등록 확인:
   ```bash
   kubectl -n si-msa exec deploy/postgres -- psql -U authuser -d authdb \
     -c "SELECT client_id, client_authentication_methods, authorization_grant_types FROM oauth2_registered_client;"
   ```
   `demo-web` (+선택 `demo-service`) 가 보이면 등록 성공.
3. **authorization_code+PKCE**:
   - `code_verifier`/`code_challenge`(S256) 생성 → 브라우저로 `GET /oauth2/authorize?response_type=code&client_id=demo-web&redirect_uri=…&scope=openid&code_challenge=…&code_challenge_method=S256` → **로그인 폼** → `tester`/`Test1234!`(DbAuthenticator 인증) → 동의 → `code` 수령.
   - `POST /oauth2/token`(code+code_verifier) → **access/id_token** 수령.
4. (선택) client_credentials 1줄 확인:
   ```bash
   curl -s -u demo-service:demo-secret -d grant_type=client_credentials localhost:9000/oauth2/token
   ```

### ⚠️ redirect_uri / issuer 주의 (kind port-forward)
- discovery `issuer` 가 in-cluster 명 `http://auth-server:9000` → 브라우저는 그 호스트를 못 푼다. port-forward(`localhost:9000`)로 접근 시 redirect/issuer 불일치가 생길 수 있다.
- 옵션: (a) `redirect_uri` 를 `http://127.0.0.1:8081/login/oauth2/code/demo-web`(LocalDemo 와 동일 패턴)로 등록하고 RP 없이 code 만 수령해 토큰교환까지 curl 로, (b) hosts 에 `auth-server` 매핑, (c) `AUTH_SERVER_ISSUER` 를 port-forward 기준 공개 URL 로 임시 치환. **검증 목적엔 (a)+curl 토큰교환이 가장 단순**.

---

## 3. 완료 정의 (Done)
- `oauth2_registered_client` 에 최소 `demo-web` 등록(앱 `save()` 경유, JSON 정상).
- `tester`/`Test1234!` 로 authorization_code → 토큰 발급 성공 = **DbAuthenticator 운영 인증 경로 kind 실증**.
- 동반 문서: `AUTH_SERVER.md`(prod 클라이언트 등록 절차 + smoke 시더 플래그)·`PITFALLS §9`(필요시)·`HANDOFF_SUMMARY`/`HANDOFF §6` 갱신.
- 그 뒤(백로그): confidential `demo-rp` 전체 콜백 흐름(B안, `NEXT_RP_IDTOKEN_LINK` §B) — RP `OAuthClient.exchangeCodeForTokens`(`client_secret_post`)까지.

## 4. 관련 코드 좌표
- `services/auth-server/src/main/java/com/company/authserver/config/LocalDemo.java` — 시더 패턴 원본(`@Profile("local")`, `repo.save(RegisteredClient…)`).
- `…/config/AuthorizationServerConfig.java` — `JdbcRegisteredClientRepository` 빈(`registeredClientRepository`), `/actuator/**` permitAll(이미 적용).
- `…/db/migration/V1__authorization_server_schema.sql` — `oauth2_registered_client` 스키마(`client_settings`/`token_settings` JSON).
- `…/db/migration/V7…` — `app_user` + `tester` seed(`{bcrypt}`).
- `deploy/k8s/overlays/local/` — `auth-server-config` ConfigMap(플래그 추가 지점).
