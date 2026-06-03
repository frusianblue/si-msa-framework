# NEXT_SESSION_KICKOFF.md — 다음 세션 즉시 착수 시트

> **용도**: 다음 세션을 열자마자 그대로 쓰는 "킥오프 + 인계 요약" 한 장.
> 직전 세션(2026-06-03, **SSO — A 중앙 로그아웃/logout-all + B-OIDC RP 강화**)까지 반영. **다음 작업 = 인증 로드맵 3) SSO 의 B-SAML(SAML 2.0 SP, `framework-saml-sp` 신설)** 가정.
> 다른 작업을 고르면 "이번 세션 목표" 절만 바꾸면 된다. 전체 맥락은 `HANDOFF_SUMMARY.md`/`HANDOFF.md`, SSO 설계는 `docs/NEXT_SSO.md`.

---

## 0. 세션 시작 시 첫 3가지 (복붙용)
1. repo 최신화: `git pull` 후 `./gradlew :framework:framework-oauth-client:test :framework:framework-archtest:test spotlessApply` 로 직전(OIDC 강화) 통과 재확인.
2. 직전 상태 읽기: `HANDOFF_SUMMARY.md`(세션 한 장) → SSO 설계 `docs/NEXT_SSO.md` **§5(SAML SP)** → 막히면 `HANDOFF.md` 6절(함정)·`STACK.md` 5절(Boot4 주의).
3. **B-SAML 착수 전 `docs/NEXT_SSO.md` §5.3(의존성/저장소 함정) + §5.6(결정 필요) 먼저 확정.**

## 1. 지금까지 (Done — 2026-06-03 기준)
- **완료**: 코어/기본 + 토대4 + 보안완성(ISMS-P) + 데이터/연계(금융: datasource·messaging·saga) + 업무생산성(excel/batch/notification/pdf) + 규제특화(mfa) + 관측(observability) + 락/캐시/마스킹/컨텍스트/이미지 + 파일하드닝/아카이브/파일일괄/SFTP + **인증 로드맵 1)소셜로그인 2)게이트웨이 엣지인증** + **3)SSO 의 A 중앙로그아웃 + B-OIDC 강화**.
- **직전 세션 = SSO A + B-OIDC**:
  - **(A) 중앙 로그아웃/logout-all**: 게이트웨이 `RedisGatewayTokenBlacklist`(`bl:{jti}` reactive)로 로그아웃 토큰 엣지 401 차단(`gateway.auth.blacklist-check.enabled`, redis 전용·fail-fast) + `LoginService.logoutAll(access,ip)`(현재 토큰 항상 블랙리스트 + 동시세션 전 세션 무효, `POST /api/v1/auth/logout-all`). 문서 `docs/modules/SSO_CENTRAL_LOGOUT.md`.
  - **(B-OIDC) framework-oauth-client OIDC RP 강화**: per-provider `oidc.enabled`(기본 off). id_token 검증(JWKS RSA/EC 또는 HS=client-secret HMAC · iss·aud⊇client-id·exp/nbf±skew·nonce·sub) + discovery 자동적용 + nonce 바인딩. 신규 `oidc/` 4종 + 기존 8파일 수정, jjwt 재사용. 문서 `docs/modules/OIDC_HARDENING.md`.
- ✅ **사용자 환경 컴파일 + `:framework:framework-oauth-client:test` 26개 통과 확인.** ⚙️ 세션 중 수정 3건: A 컴파일 에러 2건(str_replace 경계줄 누락)·JWKS 회전 가드 버그(lazy/forced 분리+쿨다운)·`*/` 주석 조기종료(`RS*/ES*/PS*`→`RS/ES/PS 계열`).

## 2. 이번 세션 목표 (다음 작업 — B-SAML, 골라서 이 절만 교체)
**주 목표 — B-SAML) SAML 2.0 Service Provider (`framework/framework-saml-sp` 신설)**
- 외부/사내 SAML IdP(공공 통합인증 등)에 **SP 로 연동** → NameID/속성 → `SamlUserResolver`(앱 구현, `OAuthUserResolver` 의 SAML 판) → **자체 JWT 발급**(OAuth `OAuthTokenIssuer` 재사용 권장, stateless 유지).
- 구현 = **Spring Security SAML2 SP**(`spring-security-saml2-service-provider`). 전용 `SecurityFilterChain`(framework-security 체인과 분리, `/saml2/**`+ACS 만), 성공 핸들러에서 서버 세션 없이 JWT 발급+`continue` 리다이렉트.
- ⚠️ **착수 전 결정/함정**(`docs/NEXT_SSO.md` §5):
  - OpenSAML 전이 의존 = **이 프레임워크 최초 "외부 의존성 0" 예외** → 버전 핀(필요 시)·**리포지터리 확인**(Central 가능? 불가면 Shibboleth repo 추가). 작성환경 Maven 차단이라 SAML 본체는 받는 쪽 gradle 컴파일.
  - 멀티 파드 in-flight = **redis 기반 `Saml2AuthenticationRequestRepository`**(OIDC state→redis 와 같은 결).
  - 신규 모듈 등록(§5.7): settings include + archtest testImplementation + `.imports`+등록가드 + STACK.md(OpenSAML 명시) + `docs/modules/SAML_SP.md`.
**대안 후보** — (devops) CI 게이트+jacoco 집계 · 그릇 정비(게이트웨이 런타임/ k8s 멀티서비스) · C) Authorization Server · 4) Passwordless.
→ 택1 후 모듈/책임/확정 결정을 여기에 적고 3절로.

## 3. 착수 전 확인할 것 (공통)
- **추측 금지**: Boot4/Spring7/Jackson3 + 외부 API(특히 **OpenSAML/Spring Security SAML2**)는 **공식 소스(GitHub raw·공식 API 문서)로 확정**. SAML 은 XML 보안 → Spring Security 추상화만 사용(직접 XML 서명 파싱 금지).
- 동적/개수 가변 빈은 **`ImportBeanDefinitionRegistrar`**(BDRPP 아님). 정적 빈은 `@AutoConfiguration`+`@ConditionalOnMissingBean`.
- 새 라이브러리는 BOM 밖이면 `libs.versions.toml`+루트 `ext` 핀, `implementation`. SAML 은 BOM 관리(spring-security-saml2)지만 **OpenSAML 은 BOM 밖일 수 있음**.
- **블록 주석에 `*/` 금지**(이번 세션 함정) — `grep -nE '\*/[^ ]'` 점검을 brace/paren 점검에 추가.
- 런타임 비용 큰 기능은 기본 off, 토글로만.

## 4. 모듈 추가/확장 레시피 (요약)
1. 신규 `framework/framework-saml-sp/`(config Properties+AutoConfiguration · 도메인 · imports FQCN). 전용 `SecurityFilterChain` 빈.
2. `build.gradle`: `api framework-security`+`api framework-core` · `implementation spring-security-saml2-service-provider`(+OpenSAML 핀 필요시) · web/servlet `compileOnly`(+test 재선언). 테스트 넣으면 `testImplementation spring-boot-starter-test` 도.
3. `settings.gradle` include · `imports`(새 autoconfig) · archtest testImplementation — 누락 주의.
4. 오토컨피그 3단 토글 `framework.saml-sp.enabled` + `@ConditionalOnClass`(SAML2) + 빈 `@ConditionalOnMissingBean`. 멀티 IdP=`registrations.<id>` 맵→`RelyingPartyRegistrationRepository`.
5. 검증: 순수(속성→registration 빌더·속성→`AuthenticatedUser` 매핑) JDK 단독 + SAML 본체는 받는 쪽 gradle(OpenSAML init 필요). 오토컨피그 토글/`FilteredClassLoader` 백오프.
6. 드롭인 zip(변경 파일 전부 + settings/imports/문서) → 루트 `unzip -o`.

## 5. 세션 종료 시 할 일 (인계)
- `HANDOFF_SUMMARY.md` 갱신구간 교체(양식 `HANDOFF_SUMMARY_TEMPLATE.md`). 새 모듈이라 `HANDOFF.md`(1·6·7절) + `docs/FRAMEWORK_MODULES.md`(카탈로그/로드맵) + `STACK.md`(OpenSAML 신규 의존성·주의) + `docs/NEXT_SSO.md`(§5 완료 표시) 갱신. 사용법은 `docs/modules/SAML_SP.md`.
- 다음 세션용으로 **이 파일** "이번 세션 목표"를 그다음 작업(C Authorization Server 또는 devops)으로 갱신.

---
*직전 세션 산출물: sso-A-central-logout-dropin.zip(게이트웨이 블랙리스트+logout-all, 컴파일 에러 수정 반영) · oidc-hardening-dropin.zip(framework-oauth-client OIDC 강화 — oidc/ 4 + 수정 8 + 테스트 3 + 문서 2). 루트에서 `unzip -o`. 사용자 환경 컴파일 + 26 테스트 통과 확인됨.*
