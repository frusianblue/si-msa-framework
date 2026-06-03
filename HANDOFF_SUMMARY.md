# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**SSO 6.1) SAML redis `Saml2AuthenticationRequestRepository` 구현 완료** — 멀티 파드에서 스티키 세션 없이 SP-initiated 흐름(AuthnRequest↔Response 상관)을 묶도록 redis 공유 저장소를 추가하고, `framework-saml-sp` 의 `request-repository: redis` fail-fast 가드를 **redis 빈 등록**으로 교체. 다음 세션 = `docs/NEXT_SSO.md` **§6.2 SAML SLO**(`saml2Logout` + 우리 JWT 블랙리스트, 6.1 선행 충족) · 그 다음 6.3 Authorization Server(후순위)/6.4 Passwordless.

핵심 설계: ① 직렬화 = `AbstractSaml2AuthenticationRequest` 네이티브 직렬화/Jackson 대신 **순수 JDK 고정형 수기 코덱**(`Saml2AuthnRequestCodec` — serialVersionUID/클래스 진화 취약성 회피, "robust over fragile"). ② save↔load 상관 = **서버 발급 UUID 쿠키**(세션 없는 멀티 파드). ③ ⚠️ **쿠키 `SameSite=None; Secure` 필수** — POST 바인딩 ACS 콜백은 IdP→SP 크로스사이트 top-level POST 라 Lax/Strict 쿠키가 미전송. ④ 하위타입 복원에 `RelyingPartyRegistrationRepository` 주입(public 팩토리가 `withRelyingPartyRegistration` 뿐). ⑤ **빈 등록만으로 SS7 이 자동 주입**(`Saml2LoginConfigurer#getBeanOrNull` → save/load/remove 측 모두) — 체인 DSL 수정 불필요.

## 최종 갱신
- 일자: 2026-06-04 · 갱신자: SSO 6.1(SAML redis AuthnRequest 저장소) 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
### framework-saml-sp 신규 `store/` (멀티 파드 redis AuthnRequest 저장소)
- **`store/Saml2AuthnRequestCodec`(신규)**: 순수 JDK(SS/redis 무의존). nested `record Data(binding, samlRequest, relayState, authenticationRequestUri, relyingPartyRegistrationId, id, sigAlg, signature)`. `encode(Data)`/`decode(String)`. 포맷 v1 = 매직 `siv1` + 9라인 개행 구분, 각 값 presence 플래그('1'/'0') + Base64(UTF-8)(Base64 에 개행 없음→구분자 충돌 불가). POST 는 sigAlg/signature 를 명시 null. 손상/매직 불일치/필드수 불일치/필수 누락 → decode 가 **null 반환**(예외 아님). encode 는 필수값 blank 면 `IllegalArgumentException`.
- **`store/RedisSaml2AuthenticationRequestRepository`(신규)**: `implements Saml2AuthenticationRequestRepository<AbstractSaml2AuthenticationRequest>`. 생성자 `(StringRedisTemplate, RelyingPartyRegistrationRepository, SamlSpProperties.Redis)`. save=UUID 키 mint→redis set(keyPrefix+key, encode, ttl)→`ResponseCookie`(httpOnly/secure/sameSite/path/maxAge=ttl) 헤더. load=쿠키→redis get→rebuild. remove=쿠키→`getAndDelete`(원자 1회 소비)→쿠키 만료(maxAge=0)→rebuild. rebuild=decode→registrationId 로 `findByRegistrationId`(없으면 null)→`withRelyingPartyRegistration` 빌더 재구성(id null 이면 `.id()` 생략). 사용자 제어값(쿠키/samlRequest/relayState) 미로깅, registrationId 만 debug.
- **`config/SamlSpProperties`(수정)**: `requestRepository` javadoc 갱신(redis=구현됨, 쿠키 함정·starter 부재 fail-fast). nested `Redis`(keyPrefix=`saml:authnreq:`/ttl=`Duration` 5m/cookieName=`SAML_AUTHN_KEY`/cookieSameSite=`None`/cookieSecure=`true`/cookiePath=`/`) + getter/setter.
- **`config/SamlSpAutoConfiguration`(수정)**: `samlRelyingPartyRegistrationRepository` 에서 redis fail-fast throw 제거(이제 항상 RP 등록만 빌드). **신규 빈** `redisSaml2AuthenticationRequestRepository`(`@ConditionalOnClass(StringRedisTemplate)`+`@ConditionalOnProperty(request-repository=redis)`+`@ConditionalOnMissingBean(Saml2AuthenticationRequestRepository)`): SameSite=None&&!secure 면 `BusinessException` fail-fast, 아니면 redis 저장소 반환. **guard 빈** `samlSpRedisRepositoryRequiredGuard`(redis 빈 **뒤** 선언, `@ConditionalOnProperty(redis)`+`@ConditionalOnMissingBean`): redis 요청인데 저장소 빈 없으면(starter 부재) `BusinessException`(redis 타입 미참조→안전).
- **`build.gradle`(수정)**: `compileOnly`+`testImplementation 'org.springframework.boot:spring-boot-starter-data-redis'`.
- **`store/Saml2AuthnRequestCodecTest`(신규, JUnit/assertj 8)**: round-trip post/redirect·null vs "" 구분·구분자/유니코드·POST sig 드롭·tamper→null·blank 필수→throw.
### 문서
- `docs/NEXT_SSO.md`(§6.1 완료 마킹+설계 대비 차이 2건 기록·§6 배너·§5 체크리스트/§5.9 갱신, 다음=6.2) · `docs/modules/SAML_SP.md`(§2 멀티 파드/§3 redis YAML/§7 완료) · `docs/FRAMEWORK_MODULES.md`(변경이력+카탈로그) · `HANDOFF.md`(§6 함정 4건 교체·트리·저널) · `README.md`(redis YAML+쿠키 주의) · `STACK.md`(data-redis 행·SAML 주의·갱신일).

## 현재 상태 (적용/검증)
- ✅ **코덱 라운드트립 JDK 단독 20케이스 실행 통과**(round-trip post/redirect·null vs ""·POST sig 드롭·유니코드/구분자(`\n`/`:`/`,`/`|`/`\t`)·20KB samlRequest·tamper/매직 불일치/필드수→null·blank→throw). 외부 의존 0.
- ⚙️ **작성 환경 SAML 본체 컴파일 불가**(Shibboleth repo 차단=allowlist 밖, sshd/MINA 와 동일 패턴) → SAML 본체·redis 풀와이어링·체인 자동 주입은 **받는 쪽 gradle 로 검증**. JUnit 코덱 테스트는 동일 로직(받는 쪽 실행).

## 켜는 법
```yaml
framework:
  saml-sp:
    enabled: true
    request-repository: redis          # 멀티 파드. 생략/session = 기본(단일/스티키)
    redis:
      key-prefix: "saml:authnreq:"     # 기본
      ttl: 5m                          # AuthnRequest 수명
      cookie-name: "SAML_AUTHN_KEY"
      cookie-same-site: "None"         # POST 바인딩 ACS=크로스사이트 → None 필수
      cookie-secure: true              # None 은 Secure(HTTPS) 필수. None+!secure → 시작 실패
    registrations:
      corp:
        metadata-uri: "https://idp.example.com/realms/corp/protocol/saml/descriptor"
```
- **운영 HTTPS 필수**(Secure 쿠키). 로컬 평문 HTTP 는 `request-repository: session`.
- `spring-boot-starter-data-redis` 부재 시 redis 요청은 **시작 실패**(조용한 session 폴백 금지).
```bash
# 검증(받는 쪽)
./gradlew :framework:framework-saml-sp:test :framework:framework-archtest:test spotlessApply
```

## 바로 다음 할 일 (Next)
1. **받는 쪽 빌드 검증**: `:framework-saml-sp:test :framework-archtest:test spotlessApply` → BUILD SUCCESSFUL 확인(코덱 JUnit 8 + 기존 토글/매핑 테스트). 풀와이어링(redis 빈 등록·SS7 자동 주입)·redis 라운드트립 통합은 받는 쪽에서.
2. **SSO 6.2 — SAML SLO(`saml2Logout`)**: 우리 로그아웃(JWT 블랙리스트)과 SAML SLO 트리거 순서·단일 진입점·SP 서명 키(RP 등록 signing credential)·IdP SLO 미지원 시 우리 토큰만 무효화(graceful). 6.1(redis 상태 공유) 선행 충족됨. 설계 = `docs/NEXT_SSO.md` §6.2. **이때 `Saml2AuthenticatedPrincipal` deprecation 도 함께 정식 교체 검토**(IDE 에서 `Saml2AssertionAuthentication`+`Saml2ResponseAssertionAccessor` 접근자 확정 후).
3. (병행, devops) CI 게이트(`:framework-archtest:test`+전 모듈 `:test` PR 차단)+멀티모듈 jacoco 집계 · 게이트웨이 런타임 점검(CORS preflight·rate-limit 429)·k8s 멀티서비스.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **redis `Saml2AuthenticationRequestRepository` 는 빈 등록만으로 SS7 이 자동 주입**: `Saml2LoginConfigurer#getAuthenticationRequestRepository` 가 `getBeanOrNull(Saml2AuthenticationRequestRepository.class)` 로 컨텍스트 빈을 감지해 save 측(`Saml2WebSsoAuthenticationRequestFilter`)·load/remove 측(`OpenSaml5AuthenticationTokenConverter`/`Saml2WebSsoAuthenticationFilter`) 모두에 주입(미발견 시 HttpSession 기본). **`saml2Login` DSL 수정 불필요** — `@Bean` 만 등록.
- **⚠️ 상관관계 쿠키는 `SameSite=None; Secure` 필수**: POST 바인딩 ACS 콜백은 IdP→SP **크로스사이트 top-level POST** → `SameSite=Lax/Strict` 쿠키가 전송되지 않아 save↔load 상관이 깨진다. 따라서 상관 쿠키는 `SameSite=None`(+`Secure`=HTTPS). `None`+비-Secure 조합은 시작 시 fail-fast. 로컬 평문 HTTP 는 `request-repository: session`. (상관 키로 RelayState 를 쓰지 않는 이유 = 앱 의미값이라 키 부적합 → 서버 발급 UUID 쿠키.)
- **starter 부재 시 guard 빈으로 fail-fast(조용한 폴백 금지)**: redis 저장소 빈은 `@ConditionalOnClass(StringRedisTemplate)`+`@ConditionalOnProperty(request-repository=redis)`+`@ConditionalOnMissingBean`. redis 요청인데 `spring-boot-starter-data-redis` 가 없으면 redis 타입을 참조하지 않는 별도 guard 빈(redis 빈 **뒤** 선언)이 `BusinessException` 으로 시작을 실패시킨다(조용한 session 폴백 방지).
- **`AbstractSaml2AuthenticationRequest` 직렬화 = 고정형 수기 코덱(네이티브/Jackson 금지)**: Java 네이티브 직렬화(serialVersionUID 620L·파드/SS 버전 간 취약)·Jackson 대신 매직+presence+Base64 고정 셰이프. 복원은 하위타입(Redirect/Post) 모두 public 팩토리가 `withRelyingPartyRegistration(RelyingPartyRegistration)` 뿐 → `RelyingPartyRegistrationRepository.findByRegistrationId`(코덱 보관 registrationId) 주입 필수, 없으면 null.
- (지난·유효) Jackson3(tools.jackson, annotation만 예외) / OpenSAML 핀 금지(SS 관리·Shibboleth 그룹 한정 repo) / SAML 체인은 메인 뒤(`after=SecurityAutoConfiguration`+securityMatcher+높은 우선순위) / compileOnly 타입 test 재선언(introspection) / 새 오토컨피그 `.imports`+등록 가드 / 새 모듈=settings+archtest+imports 동시 / 필터·EPP·SAML 핸들러는 GlobalExceptionHandler 밖(수기 JSON) / spotless Palantir·`lineEndings=UNIX`·설정캐시 / 작성 환경 SAML 본체 컴파일 불가→받는 쪽 검증 / 블록 주석 내 `*/` 금지 / prod 가드(JWT·DevAuth·Password·AES마스터키).

## 모듈 추가/확장 레시피 (검증된 반복 절차)
1. 신규 모듈/기존 확장. 순수 로직은 Spring/라이브러리 무의존 코어로 분리해 JDK 단독 검증(이번 `Saml2AuthnRequestCodec` 20케이스).
2. 기존 인터페이스는 capability/구현체로 확장. 생성자 변경 시 빈 배선(autoconfig)도 같이. **단 SS DSL 이 빈 자동 감지(`getBeanOrNull`)하는 확장점은 빈 등록만으로 충분**(SAML AuthnRequest 저장소).
3. `build.gradle`: 능력전이=`api`, 호스트/선택=`compileOnly`(+test 재선언), BOM 밖=`implementation`(+카탈로그/ext 핀). SS/Boot 가 버전 관리하는 전이 의존은 핀 금지(저장소만 필요하면 루트 repositories 그룹 한정).
4. 새 오토컨피그면 `.imports`+등록 가드. 신규 모듈은 settings include + archtest testImplementation.
5. 토글/`@ConditionalOnProperty` + `@ConditionalOnClass`(라이브러리) 백오프 + (조용한 no-op 위험 시) **별도 guard 빈으로 fail-fast**.
6. 테스트: 순수 알고리즘(JDK 단독) + 오토컨피그 토글/백오프. 미컴파일 본체(SAML/redis 풀와이어링)는 받는 쪽 gradle.
7. 드롭인: 신규+변경 파일 한 zip, 루트 `unzip -o`. 문서 동기화(README/STACK/FRAMEWORK_MODULES/HANDOFF/HANDOFF_SUMMARY/NEXT_SSO). 받는 쪽 `./gradlew :...:test :framework-archtest:test spotlessApply`.
<!-- 갱신 끝 -->
