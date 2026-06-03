# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**인증 로드맵 3) SSO — B-SAML) SAML 2.0 SP 완료 + 받는 쪽 컴파일/BUILD 정상 확인 + deprecation 경고 처리 + 다음 세션 착수 문서 정비 완료.** 다음 세션 = `docs/NEXT_SSO.md` **§6** 택1(권장 6.1→6.2): 6.1 SAML redis AuthnRequest 저장소 · 6.2 SAML SLO · 6.3 C) Authorization Server(후순위) · 6.4 Passwordless.

외부 SAML IdP(공공 통합인증·Keycloak/Azure AD SAML 모드)에 **SP 로 연동** → IdP 메타데이터 기반 RelyingParty 등록(`RelyingPartyRegistrations.fromMetadataLocation`) → 전용 `SecurityFilterChain`(`/saml2/**`,`/login/saml2/**`) → ACS 성공 시 **서버 세션 없이** NameID/속성 → `SamlAttributeMapper` 정규화 → `SamlUserResolver`(앱 구현) 매핑 → `SamlTokenIssuer`(security `JwtProvider`/`TokenStore` 재사용)로 **자체 JWT 즉시 발급**(수기 JSON, Jackson 비의존). framework-security 무수정(`@AutoConfiguration(after=SecurityAutoConfiguration)`+securityMatcher+높은 우선순위). OAuth/OIDC 와 같은 결(외부 신원확인→자체 JWT, stateless 유지).

**⚠️ 시작 시 발견: 이전 컨테이너 세션이 남긴 미추적 `framework-saml-sp` 스캐폴딩**(git 미추적·settings 미등록·`${openSamlVersion}` 핀이 루트 ext 미정의라 설정 단계 실패할 상태). 코어 코드(properties/mapper/resolver/token/success handler/autoconfig)는 양질이라 **검토 후 채택**하되, **빌드 토대는 본 세션에서 새로 정비**: ① 루트 Shibboleth 저장소(그룹 한정) ② 모듈 등록(settings/archtest) ③ 깨진 opensaml 핀 제거(전이 의존).

**조사로 확정한 사실 3건**: ⓐ **OpenSAML 4+ 는 Maven Central 에 없음**(라이선스/면책) → Shibboleth 저장소는 fallback 아니라 **필수**. ⓑ **Spring Security 가 OpenSAML 버전을 전이 관리** → opensaml **명시 핀 금지**(SS 관리 버전 수용). ⓒ `saml2Metadata` 는 SS7 정식 DSL(`saml2Login`/`saml2Logout`/`saml2Metadata`) — 초안 VERIFY 표시 해소.

## 최종 갱신
- 일자: 2026-06-04 · 갱신자: SSO(B-SAML SAML 2.0 SP) 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
### 빌드 토대 (필수 — 없으면 saml-sp include 시 설정/해소 실패)
- **루트 `build.gradle`**: `allprojects.repositories` 에 Shibboleth(`https://build.shibboleth.net/maven/releases/`)를 **`org.opensaml`/`net.shibboleth` 그룹 한정 + releasesOnly** 로 추가(그 외 의존성은 계속 Central, saml-sp 미사용 빌드 영향 0). OpenSAML 4+ 가 Central 밖이므로 필수.
- **`settings.gradle`**: `include 'framework:framework-saml-sp'`.
- **`framework-archtest/build.gradle`**: `testImplementation project(':framework:framework-saml-sp')`(ArchUnit 검사 대상).
### framework-saml-sp (코어=이전 스캐폴딩 채택, 정비/추가는 본 세션)
- **`build.gradle`(정비)**: `api framework-security`+`api framework-core`+`implementation spring-security-saml2-service-provider`(OpenSAML=전이, **핀 제거**)+web `compileOnly`(+test 재선언). 깨진 `${openSamlVersion}` opensaml 명시 핀·미사용 redis 의존 제거.
- **`SamlSpAutoConfiguration`(정비)**: `request-repository=redis` 면 **시작 시 fail-fast**(미구현 — 조용한 no-op 방지). `saml2Metadata` VERIFY 주석 → 확인됨으로 갱신.
- **`SamlSpProperties`(정비)**: `request-repository` redis 옵션 javadoc 을 "예정/미구현(현재 session+스티키 세션)"으로 명확화.
- **`SamlSpAutoConfigurationTest`(신규)**: 토글/백오프 가드(disabled by default·enabled+resolver 없으면 off·enabled 미설정이면 off) — 웹 체인(HttpSecurity/메타데이터 fetch)을 인스턴스화하지 않는 케이스만(양성 풀와이어링은 받는 쪽 검증).
- (채택·무변경) core `SamlUserInfo`/`SamlUserResolver`/`SamlAttributeMapper`·token `SamlTokenIssuer`·web `SamlRelyingPartyRegistrations`/`SamlAuthenticationSuccessHandler`·`SamlAttributeMapperTest`·`.imports`.
### 문서
- `docs/modules/SAML_SP.md`(신규 사용 가이드) · `docs/NEXT_SSO.md`(§5 완료/§5.3 Central 오해 수정/§5.6 결정 기록/§5.7 체크리스트 완료/§5.9 후속) · `docs/FRAMEWORK_MODULES.md`(변경이력+카탈로그 행) · `STACK.md`(§3.2 SS SAML2+OpenSAML 행·§5 SAML/Shibboleth 주의·갱신일) · `HANDOFF.md`(§1 트리·§3 보안골격·§6 함정 4건·§7) · `README.md`(모듈목록+사용법 서브섹션).

## 현재 상태 (적용/검증)
- ✅ **순수 매핑 로직 `SamlAttributeMapper` JDK 단독 14케이스 실행 통과**(외부 의존 0 — friendly/OID 후보·설정키 우선·다중값 첫원소·null/blank 스킵·trim). 기존 `SamlAttributeMapperTest`(4) + 가드 테스트(3) 동봉.
- ✅ **받는 쪽 환경 BUILD SUCCESSFUL(2026-06-04)** — SAML 본체 컴파일·체인 와이어링·OpenSAML(Shibboleth 5.1.6) 해소·archtest·spotless 통과. deprecation 경고 1건은 메서드 한정 억제(아래 Next 0).
- ⚙️ **스코프 결정**: redis `Saml2AuthenticationRequestRepository` 는 **이번 드롭 미포함**(SS7 인터페이스는 확인했으나 `AbstractSaml2AuthenticationRequest` 직렬화가 검증 불가·오류 취약 → 후속). 멀티 파드는 현재 게이트웨이 스티키 세션.

## 켜는 법
```yaml
framework:
  saml-sp:
    enabled: true
    # request-repository: session   # 기본. redis 는 후속(현재 설정 시 시작 실패)
    registrations:
      corp:                                  # registrationId (= IdP 식별)
        metadata-uri: "https://idp.example.com/realms/corp/protocol/saml/descriptor"
        email-attribute: "email"             # 선택(미지정 시 email/mail/urn:oid 후보 자동)
        name-attribute: "displayName"        # 선택
```
+ 앱: `SamlUserResolver` 빈 1개(외부 `SamlUserInfo` → `AuthenticatedUser`). 엔드포인트(SS 기본): 메타데이터 `/saml2/service-provider-metadata/{id}`·로그인 `/saml2/authenticate/{id}`·ACS `/login/saml2/sso/{id}`.
```bash
# 검증(받는 쪽)
./gradlew :framework:framework-saml-sp:test :framework:framework-archtest:test spotlessApply
./gradlew :framework:framework-saml-sp:dependencies --configuration runtimeClasspath   # OpenSAML 해소 출처/버전 확인
```

## 바로 다음 할 일 (Next)
0. ✅ **받는 쪽 빌드 검증 완료(2026-06-04)**: `:framework-saml-sp:test :framework-archtest:test spotlessApply` → **BUILD SUCCESSFUL**(15 executed). `:dependencies` 로 OpenSAML 해소 확인 — `spring-security-saml2-service-provider:7.0.5` → `opensaml-saml-api/core/messaging:5.1.6` + `net.shibboleth:shib-support:9.1.6`(Shibboleth 저장소에서 정상 해소, SS 가 버전 관리=핀 안 한 결정 검증). **남은 deprecation 경고 1건 처리**: `Saml2AuthenticatedPrincipal`(SS7 deprecated, 후속=`Saml2AssertionAuthentication`+`Saml2ResponseAssertionAccessor`) → `SamlAuthenticationSuccessHandler.onAuthenticationSuccess` 에 메서드 한정 `@SuppressWarnings("deprecation")`+마이그레이션 TODO(7.0.x 완전 동작·제거는 빨라야 SS8, 새 접근자 메서드는 IDE 컴파일 확인 후 교체).
1. **SSO 후속 — 착수 설계 `docs/NEXT_SSO.md` §6**(택1, 권장 순서 6.1→6.2): **6.1** SAML redis `Saml2AuthenticationRequestRepository`(멀티 파드/스티키 세션 제거 — SS7 인터페이스·직렬화 난점·`SamlSpAutoConfiguration` 배선·OAuth `RedisOAuthStateStore` 패턴 정리됨; 현 `request-repository=redis` fail-fast 가드를 redis 빈 등록으로 교체) · **6.2** SAML SLO(`saml2Logout` + 우리 JWT 블랙리스트 연계, 6.1 선행 권장) · **6.3** C) Authorization Server(별도 `services/auth-server`, 명시 요구 시·최대 작업) · **6.4** Passwordless(WebAuthn). 각 항목 결정/인터페이스/함정/테스트는 §6 에 정리.
2. (병행 가능, devops) CI 게이트(`:framework-archtest:test`+전 모듈 `:test` PR 차단)+멀티모듈 jacoco 집계 · 게이트웨이 런타임 점검(CORS preflight·rate-limit 429)·k8s 멀티서비스.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **OpenSAML = 첫 비-Central 저장소**: OpenSAML 4+ 는 Maven Central 에 **없다**(Shibboleth 전용). `spring-security-saml2-service-provider` 가 전이로 끌어오고 **버전은 SS 가 관리** → opensaml **명시 선언/핀 금지**(SS 관리 버전과 어긋남 + 루트 ext 미정의 시 설정 단계 실패. 초안 `${openSamlVersion}` 가 바로 이 함정). 루트 `allprojects.repositories` 에 Shibboleth 를 **org.opensaml/net.shibboleth 그룹 한정**으로 추가(그 외는 Central 유지). poi/openpdf/sshd 의 "BOM 밖→버전 핀" 패턴과 다름(SAML 은 저장소만 추가).
- **SAML 체인은 메인 체인 "뒤"**: 메인 `securityFilterChain` 이 `@ConditionalOnMissingBean(SecurityFilterChain)` → SAML 체인을 먼저 등록하면 메인이 사라진다. `@AutoConfiguration(after=SecurityAutoConfiguration)` + `securityMatcher("/saml2/**","/login/saml2/**")` + 높은 우선순위(`@Order(HIGHEST_PRECEDENCE+50)`)로 메인 뒤에 추가·매처 경로만 가로챔. 신원=`Saml2AuthenticatedPrincipal#getRelyingPartyRegistrationId()/getName()(NameID)/getAttributes()(Map<String,List<Object>>)`.
- **SS7 SAML2 DSL = `saml2Login`/`saml2Logout`/`saml2Metadata`**(셋 다 유효, HttpSecurity DSL).
- **redis AuthnRequest 저장소는 미구현 fail-fast**: `AbstractSaml2AuthenticationRequest` 직렬화 난점(검증 불가)으로 후속. `request-repository: redis` 설정 시 오토컨피그가 시작 시 실패(조용한 no-op 금지). 멀티 파드는 그때까지 게이트웨이/인그레스 스티키 세션(핸드셰이크 수초).
- **미추적 스캐폴딩 주의(일반화)**: 컨테이너에 이전 세션의 미추적 산출물이 남아 있을 수 있다(`git status --short`/`git ls-files` 로 추적 여부 먼저 확인). 원격 master 가 사용자 환경의 기준 — 미추적 파일은 그대로 동작 가정 금지(등록/빌드토대 점검 필수).
- (지난·유효) 블록 주석 내 `*/` 금지 / Jackson3(tools.jackson, annotation만 예외) / compileOnly 타입 test 재선언(introspection) / 새 오토컨피그 `.imports`+등록가드 / EPP 는 spring.factories(Boot4 패키지) / spotless Palantir·`lineEndings=UNIX`·설정캐시 / 필터·EPP·SAML 핸들러는 GlobalExceptionHandler 밖(수기 JSON) / prod 가드(JWT·DevAuth·Password·AES마스터키) / Boot4 패키지 리네임 추측 금지 / 새 모듈=settings+archtest+imports 동시 등록 / OIDC nonce state 동반 1회용.

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. 순수 로직은 Spring/라이브러리 무의존 코어로 분리해 JDK 단독 검증(이번 `SamlAttributeMapper` 14케이스).
2. 기존 인터페이스는 capability/오버로드로 확장. 생성자 변경 시 빈 배선(autoconfig)도 같이.
3. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`(+test 재선언), BOM 밖=`implementation`(+카탈로그/ext 핀). **단 SAML 처럼 SS/Boot 가 버전 관리하는 전이 의존은 핀하지 말 것**(저장소만 필요하면 루트 repositories 에 그룹 한정 추가).
4. 새 오토컨피그면 `.imports`+등록가드. 신규 모듈은 settings include + archtest testImplementation.
5. 오토컨피그 토글/`@ConditionalOnProperty` + `@ConditionalOnBean`(앱 리졸버) + `@ConditionalOnMissingBean` + 라이브러리 `@ConditionalOnClass` 백오프. 보안 체인 기여 모듈은 `@AutoConfiguration(after=SecurityAutoConfiguration)` + securityMatcher 로 메인 체인 무수정.
6. 테스트: 순수 알고리즘(JDK) + 오토컨피그 토글/백오프(웹 체인은 HttpSecurity/네트워크 필요 → 양성 풀와이어링은 받는 쪽). 미컴파일 본체는 받는 쪽 gradle.
7. 드롭인: 신규+변경 파일 한 zip, 루트 `unzip -o`. 문서 동기화(README/STACK/FRAMEWORK_MODULES/HANDOFF/HANDOFF_SUMMARY/NEXT_SSO). 받는 쪽 `./gradlew :...:test :framework-archtest:test spotlessApply`.
<!-- 갱신 끝 -->
