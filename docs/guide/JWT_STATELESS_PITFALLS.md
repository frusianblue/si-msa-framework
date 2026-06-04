# JWT · Stateless 함정과 확장 가이드

> **누가 보나**: 프레임워크 오너/유지보수자. 이 프로젝트를 개발하며 **실제로 막혔던** JWT·무상태(stateless)·인증 principal 문제들을 모아, *증상 → 원인 → 해결 → 교훈* 으로 정리하고, 같은 벽을 만났을 때 **어떻게 확장하면 되는지**를 안내한다.
> 검증 설계 전반(5가지 질문·신뢰 경계)은 [`../reference/TOKEN_VERIFICATION_GUIDE.md`](../reference/TOKEN_VERIFICATION_GUIDE.md), 무엇을 골라 조합할지는 [`AUTH_COMPOSITION_GUIDE.md`](AUTH_COMPOSITION_GUIDE.md). **인증 외 전 영역 함정은 [`PITFALLS.md`](PITFALLS.md)**(빌드/Jackson/오토컨피그/DB/테스트/환경).

---

## 0. 왜 stateless JWT 에서 자꾸 막히나

JWT 는 **토큰 자체에 정보가 들어 있어 서버가 상태를 안 들고도** 검증된다(빠름·확장 쉬움). 그 대가로:
- **서버가 "지금 이 토큰이 살아있나"를 모른다** → 로그아웃·강제만료·세션 종료가 토큰만으로 안 된다.
- **세션을 전제한 표준 기능**(서버 세션, SLO 등)이 무상태 흐름과 충돌한다.
- **인증 시점·인증 수단 같은 메타데이터**가 토큰 발급기에 명시적으로 전달돼야 한다(세션에 기대면 안 됨).

아래 함정들은 거의 다 이 세 가지의 변주다.

---

## 1. 함정 사례집 (실제 겪은 것)

### ① 로그아웃했는데 토큰이 계속 유효함 (폐기 불가)
- **증상**: 로그아웃/탈퇴/강제차단 후에도 기존 JWT 로 API 가 통과.
- **원인**: stateless JWT 는 `exp` 만료 전까지 무조건 유효. "아직 살아있나?"(폐기 여부)는 토큰만으로 답이 안 나온다 — 검증 5질문 중 ⑤번의 본질적 한계([`TOKEN_VERIFICATION_GUIDE`](../reference/TOKEN_VERIFICATION_GUIDE.md) §1).
- **해결**: access 토큰에 `jti` 부여 → 로그아웃 시 **jti 블랙리스트**(TTL=토큰 잔여수명), refresh 는 1회용 회전. 게이트웨이에서 블랙리스트를 엣지 401 로 차단(`GATEWAY_BLACKLIST_CHECK_ENABLED`). 중앙 로그아웃은 [`../modules/SSO_CENTRAL_LOGOUT.md`](../modules/SSO_CENTRAL_LOGOUT.md).
- **교훈**: 무상태의 "빠름"과 즉시 폐기는 트레이드오프. 폐기가 필요하면 **상태를 조금 도로 들인다**(짧은 블랙리스트). 전면 stateful 회귀가 아니라 최소 상태만.

### ② OIDC id_token 발급 실패 — `authenticationTime cannot be null` (principal 에 인증 팩터 없음) ★
- **증상**: `openid` scope 코드 교환 시 `Assert … "authenticationTime cannot be null"` 로 id_token 발급 전체 실패.
- **처음 오진**: "MockMvc 라 `SessionInformation` 이 null 이라서"로 진단했었다 → **틀림**.
- **진짜 원인**: SS7(7.0) 의 `JwtGenerator` 는 `auth_time` 을 **principal 의 `GrantedAuthority` 중 `FactorGrantedAuthority` 의 `issuedAt`** 에서 산출한다(SAS 1.x 의 `SessionInformation.getLastRequest()` 방식에서 **바뀐 것**). 커스텀 `FrameworkAuthenticationProvider` 가 `UsernamePasswordAuthenticationToken` 에 `ROLE_*`(SimpleGrantedAuthority)만 싣고 **`FactorGrantedAuthority` 를 안 붙여서** 산출 불가 → Assert. (표준 `AbstractUserDetailsAuthenticationProvider` 는 `FACTOR_PASSWORD` 를 자동 부착하는데, 커스텀이라 누락.)
- **해결**: ① provider 가 반환 토큰 authorities 에 `FactorGrantedAuthority.fromAuthority(PASSWORD_AUTHORITY)` 부착(`issuedAt` 기본 = `Instant.now()` = auth_time). ② `RoleClaimTokenCustomizer` 가 `roles` 클레임 생성 시 `FactorGrantedAuthority` 를 **필터 제외**(인증 수단 메타데이터가 앱 역할/권한으로 누수 방지). principal(`User`)에는 역할만, **토큰 authorities 에만 팩터** — 표준 form-login 과 동일.
- **교훈**: **SS7 에서 커스텀 `AuthenticationProvider` 를 쓰면 표준 provider 가 자동으로 하던 것(인증 팩터 부착)을 직접 해야 한다.** 그리고 *스택트레이스가 난 위치*(`getAuthenticationTime` 은 `if(sessionInformation!=null)` 가드 안)를 읽으면 오진을 피할 수 있었다 — 추측 대신 **정본 소스 대조**.

### ③ SAML SP-initiated 로그아웃(SLO)이 무상태와 충돌
- **증상**: 우리 앱에서 IdP 로 로그아웃을 보내는 SP-initiated SLO 가 무상태 Bearer 배포에서 동작 안 함.
- **원인**: SS7 `saml2Logout` 의 SP-initiated 는 로그아웃 시점 SecurityContext 의 `Saml2Authentication`(**세션**)에 의존. 우리 SAML 로그인은 ACS 성공 즉시 자체 JWT 로 교환하는 **무상태** 구조라 세션이 없다. 게다가 SS SLO 필터는 LogoutRequest XML 의 NameID 를 현재 `Authentication` 에서 가져와 **무상태로는 NameID 추출 불가**.
- **해결**: **IdP-initiated 수신을 우선** 채택(`{registrationId}` URL 경로로 registration 해소 → 무상태 가능). 우리 토큰 무효화는 `LoginService.logoutAllByUserId(...)` 로 userId 기반 전 세션 끊기. SP-initiated 완전 무상태는 OpenSAML 디코더 확장이 필요(확장점, [`../modules/SAML_SP.md`](../modules/SAML_SP.md)).
- **교훈**: 세션을 전제한 표준 기능을 무상태에 얹을 땐 **무상태로 되는 경로를 먼저 고르고**, 불가피한 부분만 세션 저장소(redis) 또는 확장으로 메운다.

### ④ "등록은 됐는데 동작 안 함" — 빈은 떴지만 배선 누락
- **증상**: 로그인 잠금(`LoginAttemptService`)이 컴파일·기동 다 정상인데 5회 실패해도 잠기지 않음.
- **원인**: 빈은 등록됐지만 `LoginService` 가 그걸 **호출(assertNotLocked/recordFailure/reset)하도록 배선되지 않음**. 컴파일·부팅은 멀쩡해서 안 드러남.
- **해결**: `LoginService` 를 4-인자 생성자로 바꿔 잠금 서비스 주입 + 실패/성공 지점에 호출 연결.
- **교훈**: **"빈 등록 ≠ 기능 동작."** 보안 기능은 "켜져 있나"가 아니라 "실제로 막나"를 회귀 테스트로 확인(예: 5회 실패 → 429).

### ⑤ 게이트웨이 `lb://user-service` 가 로컬에서 안 풀림
- **증상**: 로컬에서 게이트웨이(:8000)→user-service 라우팅 실패.
- **원인**: 라우트가 `lb://`(디스커버리/로드밸런서 전제)인데 게이트웨이에 discovery client 의존성이 없음 = K8s/외부 디스커버리 환경 전제.
- **해결**: 로컬은 서비스에 직접 호출(8080), 또는 라우트 uri 를 `http://localhost:8080` 으로 override. K8s 에선 서비스 DNS `http://user-service:8080`. 상세 [`../modules/GATEWAY_EDGE_AUTH.md`](../modules/GATEWAY_EDGE_AUTH.md).
- **교훈**: 엣지 인증(stateless)과 별개로 **라우팅 전제(디스커버리)**도 환경 가정이다 — 로컬/운영 가정을 README 에 명시.

### ⑥ 멀티 인스턴스인데 `memory` 저장소 → 상태가 안 공유됨
- **증상**: replicas≥2 에서 로그인 잠금 우회, 로그아웃이 한 파드에만 적용, 멱등/락이 새는 현상.
- **원인**: `type=memory` 는 인스턴스별 맵 → 파드 간 공유 안 됨. 토큰스토어·로그인시도·멱등·락·MFA 챌린지 모두 해당.
- **해결**: 운영은 `framework.security.token-store.type=redis`, `login-attempt.type=redis`, idempotency/lock/mfa-challenge 도 `redis`. 상태 관리 표 [`AUTH_COMPOSITION_GUIDE`](AUTH_COMPOSITION_GUIDE.md) §2.
- **교훈**: stateless 토큰을 써도 **"조금 들인 상태"(블랙리스트/잠금/멱등)는 공유 저장소**(Redis)가 있어야 멀티 파드에서 의미를 가진다.

### ⑦ 로그인 잠금 IP 키의 `X-Forwarded-For` 위조
- **증상**: `key-strategy=login-id-and-ip` 에서 공격자가 XFF 를 바꿔 잠금 회피.
- **원인**: `X-Forwarded-For` 는 클라이언트가 위조 가능. 신뢰 프록시가 없으면 무의미.
- **해결**: XFF 신뢰는 **신뢰 프록시 환경 한정**. 그 외엔 `login-id` 키. (`ClientIpResolver` 가 XFF→`getRemoteAddr()` 폴백.)
- **교훈**: 헤더 기반 신원/IP 는 **누가 그 헤더를 보장하나**를 먼저 따진다(게이트웨이 신뢰 경계와 동일 원리).

### ⑧ RBAC 가 deny-by-default 가 아니다
- **증상**: 권한 매핑을 안 했는데 인증된 사용자가 그냥 통과.
- **원인**: `DynamicAuthorizationManager` 는 `resources`/`role_resources` 매핑이 **없으면 인증된 사용자에게 허용**(명시적 deny 기본 아님).
- **해결**: 보호 자원은 매핑을 명시. 민감 경로는 `@PreAuthorize` 로 이중 방어.
- **교훈**: "인증됨"과 "인가됨"은 다르다. 매핑 공백의 기본 동작을 알고 설계.

### ⑨ (보조) JWT 서명 변조 음성 테스트가 안 깨짐
- **증상**: 서명 마지막 글자를 바꿔 "위조 토큰 거부" 테스트를 짰는데 통과해버림.
- **원인**: base64url 마지막 문자는 trailing-bit 특성상 바꿔도 동일 바이트로 디코드될 수 있음.
- **해결**: 서명 세그먼트의 **중간 문자**를 변경.
- **교훈**: 음성(negative) 보안 테스트는 "정말 깨지는 입력"인지 확인.

> **교차 함정(인증 외 공통, 참고만)**: `com.fasterxml.*` 금지(Jackson 3 = `tools.jackson.*`) · `compileOnly` 의존은 테스트에서 `testImplementation` 재선언(특히 `ApplicationContextRunner`). 상세는 [`DEVELOPER_GUIDE.md`](DEVELOPER_GUIDE.md) §10·§11. `FactorGrantedAuthority` 직렬화는 SS core 의 Jackson **3** 모듈이 지원하므로 ②의 해법이 안전하다.

---

## 2. 확장 가이드 — 막혔을 때 이렇게 늘린다

### 2.1 폐기/로그아웃을 더 강하게
1. **짧은 access TTL + refresh 회전** — 폐기 지연을 TTL 로 줄인다(가장 저렴).
2. **jti 블랙리스트(Redis)** — 즉시 무효화. 멀티 파드는 Redis 필수.
3. **introspection / 중앙 OP** — 외부·고규제면 발급기에 살아있는지 질의(`auth-server` `/oauth2/revoke`, [`../modules/AUTH_SERVER.md`](../modules/AUTH_SERVER.md)).
> 한 번에 다 갈 필요 없음. ①→②→③ 으로 요구 강도에 맞춰 단계적으로.

### 2.2 커스텀 `AuthenticationProvider` 만들 때 체크리스트 (②의 재발 방지)
- [ ] 반환 인증 토큰 authorities 에 **`FactorGrantedAuthority`**(예: `PASSWORD_AUTHORITY`) 부착 — OIDC `auth_time` 산출 전제.
- [ ] **principal 에는 앱 역할만**, 인증 팩터는 토큰 authorities 에만(누수 방지).
- [ ] 클레임 커스터마이저에서 `roles` 등 **앱 권한 클레임에 팩터가 섞이지 않게** 필터.
- [ ] 직렬화 경로 확인(SS core Jackson **3** 모듈 사용 — `com.fasterxml` 아님).
- [ ] **회귀 테스트**: `openid` 코드 교환 → id_token 의 `auth_time` non-null 단언.

### 2.3 세션 전제 기능을 무상태에 얹을 때 (③의 일반화)
- 무상태로 되는 경로(예: IdP-initiated)를 **먼저** 채택.
- 상관관계가 꼭 필요하면 **세션 대신 공유 저장소**(예: `request-repository=redis` + 상관 쿠키 `SameSite=None;Secure`).
- 표준 필터가 세션 의존이면 **디코더/핸들러 확장점**으로 무상태화(예: SAML NameID 추출).

### 2.4 새 인증 방식/모듈을 추가할 때
표준 **3단 토글 규약**(클래스패스 → `enabled` → 구현 선택)으로 `framework-<x>` 신설 → `framework-archtest/build.gradle` 에 `testImplementation project(...)` 한 줄 등록(검사 포함). 절차 요약은 [`AUTH_COMPOSITION_GUIDE`](AUTH_COMPOSITION_GUIDE.md) §7, 토글/의존 규약은 [`MODULE_COMPOSITION`](MODULE_COMPOSITION.md) §0.

### 2.5 회귀 방지 원칙 (④의 일반화)
- 보안 기능은 **"동작하나"를 테스트**(잠금=429, 폐기=401, 인가 거부=403). 빈 등록 여부가 아니라 **막는지**.
- 멀티 인스턴스 가정 기능은 **공유 저장소 전제**를 README·설정 주석에 명시.
- 추측 진단 금지 — 스택트레이스 위치와 **정본 소스(라이브러리 버전 브랜치)**를 대조하고 고친다.
