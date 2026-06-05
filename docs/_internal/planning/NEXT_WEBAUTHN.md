# NEXT_WEBAUTHN.md — A1 패스키/WebAuthn (다음 섹션 착수)

> **목표**: 비밀번호 없는 강인증(FIDO2/패스키)을 프레임워크에 끼운다. 신설 모듈 `framework-webauthn` — 기존 코드 수정 없이 3단 토글(클래스패스→`enabled`→구현선택)로 켜고 끈다.
>
> **핵심 결정(선조사 완료)**: 직접 구현(WebAuthn4J raw)·서드파티 확장(`webauthn4j-spring-security`) 대신, **Spring Security 7 네이티브 패스키 지원**(`http.webAuthn()` DSL)을 래핑한다. SS6.4+ 도입·SS7.0 정식, 내부적으로 WebAuthn4J 사용, Spring Security 프로젝트가 유지보수. 우리는 "프레임워크 정체성(JWT/RBAC)과의 접합 + 영속 백엔드 + 토글"만 더한다.
>
> **그라운딩 철칙**: 착수 시 SS7 공식 문서/실 API 를 다시 대조(아래 §1 은 2026-06 조사값). 추측 금지.

---

## 0. 사실 — SS7 네이티브 WebAuthn (2026-06 조사, 공식 문서 기준)

- **의존성**: `org.springframework.security:spring-security-webauthn` (버전은 Boot 4 BOM 관리). WebAuthn4J(`com.webauthn4j:webauthn4j-core`)가 전이. → JDBC 영속 클래스도 이 모듈에 포함.
- **활성화 DSL**: `http.webAuthn(w -> w.rpName("...").rpId("example.com").allowedOrigins("https://example.com"))`.
- **RP 빈**: `WebAuthnRelyingPartyOperations`(구현 `Webauthn4JRelyingPartyOperations`) — `PublicKeyCredentialUserEntityRepository`, `UserCredentialRepository`, rpId/rpName/allowedOrigins 로 구성.
- **영속 SPI** (`org.springframework.security.web.webauthn.management`):
  - `PublicKeyCredentialUserEntityRepository` — username ↔ user handle(PublicKeyCredentialUserEntity).
  - `UserCredentialRepository` — 사용자별 `CredentialRecord` 관리(findByCredentialId / findByUserId / save / delete).
  - 기본 **In-Memory**. JDBC: `JdbcPublicKeyCredentialUserEntityRepository(JdbcOperations)` + `JdbcUserCredentialRepository(JdbcOperations)` (스키마 필요 — SS 가 DDL 제공/문서화).
- **엔드포인트(기본)**: `POST /webauthn/register/options`, `POST /webauthn/register`(등록), `GET /webauthn/register`(기본 등록 페이지), `POST /webauthn/authenticate/options`, `POST /login/webauthn`(인증), `GET /login/webauthn`.
- **제약**: WebAuthn 은 **SecureContext(HTTPS)** 에서만 동작(localhost 예외). 등록/인증 ceremony 는 **세션+CSRF** 기반(챌린지 보관).

## 1. 레포 통합 지점 (현재 사실 — 코드 대조)

- **신설 모듈명/연결점은 이미 문서에 예약됨** — `AUTH_COMPOSITION_GUIDE.md §7`: "Passkey/WebAuthn → `framework-webauthn` (security `MfaGate`/인증기 SPI 에 연결)".
- **보안 체인은 `framework-security` 가 만든다** — `SecurityAutoConfiguration`:
  - `StatelessChainConfig`(`session.mode=stateless`, 기본): `SessionCreationPolicy.STATELESS` + CSRF disable. ← **WebAuthn ceremony(세션/CSRF 필요)와 상충 — 핵심 결정 ②**.
  - `SessionChainConfig`(`session.mode=session`): `IF_REQUIRED` 세션 + (옵션)CSRF. ← WebAuthn ceremony 와 자연 정합.
- **정체성 접합 SPI**:
  - `Authenticator`(`AuthenticatedUser authenticate(LoginCommand)`) — 1차 인증.
  - `MfaGate`(`boolean isRequired(user)`, `MfaTicket issueChallenge(user, ip)`) — 2차 인증 게이트(mfa 모듈이 `DefaultMfaGate` 로 구현).
  - JWT 발급은 `JwtProvider`(security) — oauth-client 가 외부 IdP 성공 후 자체 JWT 발급하는 패턴과 동일하게, WebAuthn 성공 핸들러에서 `AuthenticatedUser`→JWT.
- **영속/저장 백엔드 패턴(미러 대상)**: mfa 의 `store/`(InMemory·Jdbc·Redis) + `@ConditionalOnMissingBean` 선택, redis 백엔드는 `framework-redis` 에 두는 컨벤션(`RedisConcurrentSessionService` 최근 추가가 최신 예시).
- **DDL 컨벤션**: 각 모듈 `security-extras-*.sql`(PG)/H2 — WebAuthn 자격증명 테이블도 동일 위치/방식.
- **build.gradle 컨벤션**(mfa 참고): `api project(':framework:framework-security')`; 선택 의존(`spring-security-webauthn`, web/jdbc/redis)은 **앱이 제공** → 라이브러리는 `compileOnly` + 테스트에 `testImplementation` 재선언(ApplicationContextRunner introspection 함정 [PITFALLS §4]).
- **배선 5종**: `settings.gradle` include · 루트 `build.gradle` jacocoAggregation · `framework-archtest/build.gradle` `testImplementation project(...)` · 모듈 README · `framework/README.md` 그룹표(🔐 인증·보안) + `AUTH_COMPOSITION_GUIDE §7`/`FRAMEWORK_MODULES`.

## 2. 착수 전 결정사항 (구현 시작점에서 확정)

1. **모드 — 패스키 1차(passwordless) vs 2차(MFA factor) vs 둘 다.**
   - 1차: WebAuthn 성공 → 곧바로 자체 JWT 발급(아이디 없는 로그인 or username 힌트).
   - 2차: 비번 1차 후 `MfaGate.isRequired` → WebAuthn assertion 으로 챌린지 충족(mfa 의 `MfaMethod` 에 `WEBAUTHN` 추가 검토).
   - **권장 시작**: 1차(passwordless) 단독으로 자기완결 슬라이스 → 이후 2차 연계. (작게 끝낼 수 있는 경계)
2. **보안 체인 통합 — 가장 까다로움.** 무상태(JWT) 체인은 세션/CSRF 가 없어 ceremony 와 충돌. 선택지:
   - (a) `session.mode=session` 전제로만 WebAuthn 활성(가장 단순, 문서로 제약 명시).
   - (b) `/webauthn/**`·`/login/webauthn` 전용 **별도 SecurityFilterChain**(세션/CSRF 국소 허용) + 성공 시 JWT 발급해 그 후는 무상태. **권장** — 무상태 주류를 깨지 않음. `@Order` 로 경로 한정 체인 우선.
   - (c) security 에 ceremony 훅(customizer) 노출. 침습적 — 후순위.
3. **영속 백엔드 범위** — In-Memory(개발) + **JDBC**(SS 제공 `Jdbc*` 래핑 + DDL) 우선. Redis 는 선택(챌린지는 휘발, 자격증명은 영속이라 JDBC 가 1순위).
4. **rpId/origin 과 게이트웨이** — rpId = 등록가능 도메인(예 `example.com`), origin = 공개 URL(게이트웨이 Ingress 호스트/TLS). D 에서 추가한 `ingress-prod.yaml`(TLS, `api.example.com`)과 정합. 멀티서비스에서 rpId 일원화 정책 필요.
5. **Jackson 3** — SS WebAuthn 직렬화가 자체 Jackson 모듈을 쓰는지 확인(프레임워크는 `com.fasterxml.*` 금지 = `tools.jackson.*`만). 충돌 시 격리 [PITFALLS §2].

## 3. 1차 구현 계획 (권장 슬라이스 — passwordless 자기완결)

1. `framework/framework-webauthn/` + `build.gradle`(`api security`; `compileOnly` spring-security-webauthn·web·jdbc) + `settings.gradle`/jacoco/archtest 배선.
2. `WebAuthnProperties`(`framework.webauthn.*`: enabled·rp-id·rp-name·allowed-origins·store.type=memory|jdbc).
3. `WebAuthnAutoConfiguration` — 3단 토글. `WebAuthnRelyingPartyOperations` 빈 + 영속 SPI 빈(memory 기본, `store.type=jdbc` 면 SS `Jdbc*` 래핑) + 전용 SecurityFilterChain(결정 ②-b) + WebAuthn 성공 핸들러(→`JwtProvider` 로 자체 JWT).
4. `security-extras` WebAuthn DDL(PG/H2).
5. 테스트: 오토컨피그 토글/등록 가드(`.imports`) + (가능하면) MockMvc 로 `/webauthn/register/options` 200 스모크. ceremony 실서명은 받는 쪽 브라우저 검증.
6. 문서 동반: 모듈 README(켜기/쓰기/실전/끄기) · `framework/README.md` 🔐 그룹 행 · `AUTH_COMPOSITION_GUIDE §7`(⬜→✅/🟡) · `FRAMEWORK_MODULES` · PITFALLS(착수 중 발견분).

## 4. 수용 기준 (Acceptance)

- [ ] `framework.webauthn.enabled=true` + 의존 추가 시 등록/인증 엔드포인트 노출, 미설정 시 미등록(무상태 주류 무영향).
- [ ] passwordless 인증 성공 → 프레임워크 표준 JWT 발급(기존 RBAC/`@PreAuthorize` 와 동일 권한 형태).
- [ ] `store.type=jdbc` 면 자격증명 영속(재기동 후 인증 유지) + DDL 제공.
- [ ] 오토컨피그 토글/`.imports` 등록 가드 테스트 그린.
- [ ] 문서 6종 동반 갱신, archtest 통과.

## 5. 관련 코드/문서
- 통합점: `framework-security/.../config/SecurityAutoConfiguration.java`(체인), `.../auth/{Authenticator,MfaGate,AuthenticatedUser}.java`, `.../jwt/JwtProvider.java`
- 패턴 미러: `framework-mfa/`(store/web/properties/autoconfig), `framework-redis/RedisConcurrentSessionService`(최신 백엔드 추가 예), `framework-oauth-client`(외부인증→자체JWT 성공핸들러)
- 문서: `docs/guide/AUTH_COMPOSITION_GUIDE.md §7`, `docs/guide/MODULE_COMPOSITION.md §0`(신설 모듈 절차), `docs/guide/PITFALLS.md §2·§4`
- 외부(착수 시 재대조): Spring Security Reference — Passkeys(servlet) · `spring-security-webauthn` 아티팩트 · WebAuthn4J

## 6. 함정 메모(착수 전 예측)
- **무상태↔ceremony 충돌**이 최대 난관 — 전용 체인(②-b)로 국소화. 무상태 체인에 WebAuthn 을 그대로 얹으면 CSRF/세션 부재로 ceremony 실패.
- **HTTPS 필수** — 로컬은 localhost 예외지만 dev/prod 는 Ingress TLS 전제(D의 `ingress-prod.yaml`).
- **rpId 불일치** = ceremony 거부 — origin/도메인 정합을 멀티서비스에서 일원화.
- **spring-security-webauthn 아티팩트 누락** — JDBC 영속 클래스는 web 코어가 아니라 이 모듈에 있음(공식 문서 이슈 #18377). 의존 빠뜨리면 `Jdbc*Repository` 컴파일 불가.
- **compileOnly→test 갭** [PITFALLS §4] — 선택 의존 전부 `testImplementation` 재선언.
