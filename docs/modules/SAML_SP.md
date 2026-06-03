# SAML_SP.md — framework-saml-sp (SAML 2.0 Service Provider)

> 인증 로드맵 3) SSO — **B-SAML**. SAML 만 말하는 외부/사내 IdP(공공 통합인증, 일부 그룹 SSO, Keycloak/Azure AD 의 SAML 모드)에
> **SP(Service Provider)로 연동**한다. OAuth/OIDC 클라이언트와 같은 결: **외부 신원확인 → 앱 리졸버가 우리 사용자로 매핑 →
> 자체 JWT 발급**(stateless 유지). SAML 은 "신원확인 프로토콜"일 뿐, 이후 운영은 기존 JWT/게이트웨이/context 그대로다.

---

## 1. 한눈에

- **모듈**: `framework:framework-saml-sp` (선택형, 기본 off)
- **구현**: Spring Security SAML2 SP(`spring-security-saml2-service-provider`) — 직접 XML 서명 파싱 대신 검증된 스택 사용.
- **앱이 구현할 것**: `SamlUserResolver` 1개(외부 신원 `SamlUserInfo` → 우리 `AuthenticatedUser`). 자체 로그인 `Authenticator`,
  소셜 로그인 `OAuthUserResolver` 와 대칭.
- **발급기**: `SamlTokenIssuer`(기본 `DirectSamlTokenIssuer`) — security 의 `JwtProvider`/`TokenStore` 재사용. 발급 JWT 형태는
  자체/소셜 로그인과 동일. 동시 로그인 제어·접속 감사까지 통합하려면 LoginService 위임 구현을 `@Bean` 으로 교체(`@ConditionalOnMissingBean`).
- **무상태**: ACS 성공 시 서버 세션을 만들지 않고 즉시 자체 JWT 를 발급해 JSON 으로 반환(필터 계층이라 수기 JSON, Jackson 비의존).

## 2. ⚠️ 의존성/저장소 (착수 전 필독)

- **OpenSAML 전이 + Shibboleth 저장소(필수)**: 이 모듈은 프레임워크 최초의 "새 외부 의존성 0" 예외다. `spring-security-saml2-service-provider`
  가 OpenSAML(`org.opensaml:*`)을 **전이로** 끌어오고 **버전도 Spring Security 가 관리**한다(여기서 opensaml 을 명시 선언/핀하지 않는다).
  단 **OpenSAML 4+ 는 Maven Central 에 게시되지 않고 Shibboleth 저장소에만** 있어, 루트 `build.gradle` 에 해당 저장소를
  `org.opensaml`/`net.shibboleth` 그룹 한정으로 추가해 두었다(이미 반영, fallback 아님). 해소 확인:
  `./gradlew :framework:framework-saml-sp:dependencies`.
- **멀티 파드 in-flight 상태**: SP-initiated 흐름은 AuthnRequest↔Response 상관을 HTTP 세션에 보관한다. 게이트웨이가 authorize 와
  ACS 콜백을 다른 파드로 보내면 세션이 없어 깨진다. **현 단계 권장: SAML 핸드셰이크 구간(수초)에 한해 게이트웨이/인그레스 스티키 세션.**
  redis 공유 저장소(`Saml2AuthenticationRequestRepository` redis 구현)는 다음 단계이며, 그때까지 `request-repository: redis` 로
  설정하면 오토컨피그가 시작 시 명확히 실패시킨다(조용한 no-op 방지).

## 3. 켜는 법

```gradle
// 의존 서비스 build.gradle
dependencies {
    implementation project(':framework:framework-saml-sp')
}
```

```yaml
framework:
  saml-sp:
    enabled: true
    # request-repository: session   # 기본. redis 는 다음 단계(현재 설정 시 시작 실패)
    default-continue-url: "https://app.example.com/"   # (선택) 성공 후 앱 정책용 — 현재 기본 핸들러는 JSON 반환
    registrations:
      corp:                              # registrationId (= IdP 식별, OAuth provider id 와 대칭)
        metadata-uri: "https://idp.example.com/realms/corp/protocol/saml/descriptor"
        # entity-id: "{baseUrl}/saml2/service-provider-metadata/{registrationId}"   # (선택) SP entityId
        # assertion-consumer-service-location: "..."                                # (선택) ACS 위치 오버라이드
        email-attribute: "email"          # (선택) 미지정 시 email/mail/urn:oid:.. 후보 자동 시도
        name-attribute: "displayName"     # (선택) 미지정 시 displayName/name/cn/urn:oid:.. 후보
```

```java
// 앱이 구현하는 단 하나의 계약
@Component
public class MySamlUserResolver implements SamlUserResolver {
    @Override
    public AuthenticatedUser resolve(SamlUserInfo info) {
        // info.registrationId() + info.nameId() 로 연동 계정 조회 → 있으면 반환
        // 없으면 정책에 따라 JIT 가입 또는 info.email() 로 기존 계정 연동
        // 불가하면 throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, ...)
        return new AuthenticatedUser(/* userId */ info.nameId(), info.name(), List.of("USER"));
    }
}
```

활성 3단 토글: **(1)** 모듈 의존 추가 + `@ConditionalOnClass`(SAML2 클래스) → **(2)** `framework.saml-sp.enabled=true` →
**(3)** `SamlUserResolver` 빈 등록(`@ConditionalOnBean`) + `registrations` 1개 이상. 하나라도 빠지면 백오프(안전 기본값).

## 4. 엔드포인트 (Spring Security 기본 경로)

- **SP 메타데이터**: `GET /saml2/service-provider-metadata/{registrationId}` (IdP 에 등록)
- **로그인 시작(SP-initiated)**: `GET /saml2/authenticate/{registrationId}` → IdP SSO 로 302
- **ACS(assertion 수신)**: `POST /login/saml2/sso/{registrationId}`

전용 `SecurityFilterChain`(`securityMatcher("/saml2/**","/login/saml2/**")`, 높은 우선순위)이 이 경로만 처리하고 permitAll
(인증 자체가 여기서 일어난다). 매처 밖 모든 요청은 framework-security 의 메인 체인(catch-all)이 그대로 처리한다.

## 5. 체인 순서 (왜 깨지지 않는가)

framework-security 의 메인 `securityFilterChain` 은 `@ConditionalOnMissingBean(SecurityFilterChain.class)` 다. SAML 체인을
메인보다 먼저 등록하면 메인이 백오프되어 사라진다. 이를 막으려 이 오토컨피그를 `@AutoConfiguration(after = SecurityAutoConfiguration.class)`
로 두어 **메인 체인이 먼저 등록된 뒤** SAML 체인을 추가한다. SAML 체인은 `securityMatcher` + 높은 우선순위로 먼저 평가되고,
매처 밖은 메인 체인이 받는다. → framework-security 무수정.

## 6. 신원 매핑 (`SamlAttributeMapper`)

SAML 속성 키는 IdP 마다 제각각(friendly: `email`/`displayName`, 또는 URN/OID: `urn:oid:1.2.840.113549.1.9.1`=email).
`SamlAttributeMapper` 는 **설정 키 우선 → 표준 후보(friendly + OID)** 순으로 처음 발견된 비어있지 않은 값을 채택한다(다중값은 첫 원소).
OpenSAML/Spring 타입 무의존(입력은 추출된 nameId + `Map<String,List<Object>>`)이라 네트워크 없이 JDK 단독 단위검증된다.

## 7. 다음 단계 (이 모듈의 후속)

- **`Saml2AuthenticatedPrincipal` deprecation 마이그레이션(SS8 전)**: SS7 이 assertion 세부를 principal 에서 분리하며 `Saml2AuthenticatedPrincipal` 을 deprecated 했다(정식 후속 = `Saml2AssertionAuthentication.getRelyingPartyRegistrationId()` + `Saml2ResponseAssertionAccessor`). 현재 `SamlAuthenticationSuccessHandler` 는 검증된 deprecated 경로를 유지하고 경고만 메서드 한정 `@SuppressWarnings("deprecation")` 으로 억제(7.0.x 완전 동작, 제거는 빨라야 SS8). IDE 에서 새 접근자 메서드를 확정한 뒤 교체.
- **redis 기반 `Saml2AuthenticationRequestRepository`**: 멀티 파드에서 스티키 세션 없이 동작하도록 AuthnRequest 를 redis 공유.
  난점은 `AbstractSaml2AuthenticationRequest`(복합 객체) 직렬화 — 검증 후 도입.
- (선택) SLO(Single Logout, `saml2Logout`) · 메타데이터 없는 IdP(엔드포인트/인증서 수동 입력) 지원.

## 8. 검증 (받는 쪽)

```bash
./gradlew :framework:framework-saml-sp:test :framework:framework-archtest:test spotlessApply
# OpenSAML 해소 출처/버전 확인:
./gradlew :framework:framework-saml-sp:dependencies --configuration runtimeClasspath
```

> ⚠️ 작성 환경은 Maven Central/Shibboleth 차단 → SAML 본체 컴파일 불가. 순수 로직(`SamlAttributeMapper`)만 JDK 단독 검증했고,
> SAML 본체·서명검증·체인 와이어링은 받는 쪽 gradle 로 확인한다(sshd/MINA 와 동일 패턴).
