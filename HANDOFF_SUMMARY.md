# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**SSO 6.2-A) SAML IdP-initiated SLO 수신 구현 완료** — 외부 IdP 가 중앙에서 로그아웃할 때 우리 앱의 자체 JWT 도 함께 무효화("중앙 로그아웃 준수"). 방향 택1에서 **A) IdP-initiated 수신**(무상태/멀티 파드 친화·규제 직답) 채택, B) SP-initiated(우리앱→IdP)는 후속. **SAML 본체(서명검증·XML·LogoutResponse 생성)는 SS `saml2Logout` 기본구현에 위임 → 우리 기여물은 OpenSAML 무의존**(6.1 코덱과 동일 분리 철학). 다음 세션 = `docs/NEXT_SSO.md` **§6.2-B**(SP-initiated SLO) 또는 **§6.3** Authorization Server(후순위).

핵심 설계: ① 우리 SAML 로그인은 **무상태**(ACS 성공 즉시 자체 JWT, 서버 SAML 세션 없음) → SS `saml2Logout` 의 SP-initiated 는 세션의 `Saml2Authentication` 의존이라 충돌. IdP-initiated 만 `{registrationId}` URL 경로로 무상태 수신(SS 이슈 #10820). ② NameID→우리 userId **역매핑 SPI `SamlLogoutUserResolver`**(로그인 `SamlUserResolver` 대칭, 미매칭=null graceful). ③ userId 로 전 세션 무효화 = `LoginService.logoutAllByUserId` 신설(access token 불요). ④ SAML 모듈이 security `LoginService` 에 하드결합 안 되도록 `SamlSessionTerminator` 로 분리(SAML 전용 앱 확장점). ⑤ 토글 `framework.saml-sp.slo.enabled=false` 기본 — 켜야 체인에 `/logout/saml2/**`+`saml2Logout`+핸들러 추가, 끄면 기존 SAML SP 무변경.

## 최종 갱신
- 일자: 2026-06-04 · 갱신자: SSO 6.2-A(SAML IdP-initiated SLO 수신) 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 무엇을 했나 (Done)
### framework-security (userId 기반 전 세션 무효화)
- **`auth/LoginService.logoutAllByUserId(userId, clientIp, reason)`(신규)**: access token 없이 userId 로 직접 전 세션 무효화 — 동시세션 레지스트리 열거 → refresh 제거 + accessJti 블랙리스트 + unregister, 감사 이벤트 발행. userId blank 면 `BusinessException(INVALID_INPUT)`. 레지스트리 미사용 시 0 반환(무상태 JWT 한계). SAML SLO 외 **관리자 강제 로그아웃·계정도용 대응**에도 재사용.

### framework-saml-sp (IdP-initiated SLO 수신)
- **`core/SamlLogoutInfo`(신규)**: `record(registrationId, nameId, List<String> sessionIndexes)`. null sessionIndexes→`List.of()` 방어복사, 단축 생성자(registrationId, nameId).
- **`core/SamlLogoutUserResolver`(신규 SPI)**: `String resolveUserId(SamlLogoutInfo)`. NameID→우리 userId 역매핑(로그인 `SamlUserResolver` 대칭). **이 빈을 등록해야 SLO 수신 활성**. 미매칭이면 null(예외 금지 — IdP LogoutRequest 처리는 관용적이어야).
- **`slo/SamlSessionTerminator`(신규)**: `@FunctionalInterface int terminateAll(userId, reason)`. SLO 오케스트레이션을 security `LoginService` 에서 분리(테스트 fake/SAML 전용 앱 확장점).
- **`slo/SamlSloService`(신규)**: 무상태 오케스트레이션(slf4j 만 의존). `int onLogoutRequest(SamlLogoutInfo)` — null/blank nameId→0, resolver→userId(null→0 graceful), terminator 호출. 사용자 제어값(nameId) 미로깅(registrationId 만 debug).
- **`web/SamlSloLogoutHandler`(신규)**: `implements LogoutHandler`. SS `Saml2LogoutRequestFilter` 가 LogoutRequest **검증 후** 호출 → registrationId(경로) + nameId(`Authentication#getName()`) → `SamlSloService`. auth==null 이면 no-op. 순수 `static registrationIdFromUri(String)` 분리(servlet 무의존, JDK 검증용).
- **`config/SamlSpProperties.Slo`(신규 nested)**: `enabled=false`.
- **`config/SamlSpAutoConfiguration`(수정)**: SLO 빈 3종 전부 `@ConditionalOnProperty(slo.enabled=true)` — `samlSessionTerminator`(`@ConditionalOnBean(LoginService)`, 람다 `(u,r)->loginService.logoutAllByUserId(u,null,r)`)·`samlSloService`(`@ConditionalOnBean({SamlLogoutUserResolver,SamlSessionTerminator})`)·`samlSloLogoutHandler`(`@ConditionalOnBean(SamlSloService)`). 체인 빈에 `SamlSpProperties`+`ObjectProvider<SamlSloLogoutHandler>` 주입, slo.enabled 면 matcher 에 `/logout/saml2/**` 추가 + `http.saml2Logout(withDefaults())` + (핸들러 있으면) `http.logout(l->l.addLogoutHandler(handler))`.
- **테스트(신규)**: `slo/SamlSloServiceTest`(매핑/무매핑 no-op/blank·null nameId no-op·resolver 미호출/SessionIndex 방어복사) · `web/SamlSloLogoutHandlerTest`(`registrationIdFromUri` 경로/끝슬래시/slo무id→null/null·empty·root·슬래시없음→null).

## 검증 (이 환경)
- **SLO 순수 결정로직 JDK 단독 15/15 통과**(`registrationIdFromUri` 10 + `onLogoutRequest` 분기 5). `com.fasterxml` 누수 0 확인.
- SAML 본체(서명검증·XML·LogoutResponse 라운드트립)는 작성 환경 OpenSAML 컴파일 불가 → 받는 쪽 gradle.

## 새로 밟은/확정한 함정 (HANDOFF §6 등록)
1. **SS `saml2Logout` 은 세션결합** — SP-initiated 는 SecurityContext 의 `Saml2Authentication`(보통 HttpSession) 의존. 무상태 Bearer 와 충돌 → **IdP-initiated 만 `{registrationId}` URL 경로로 무상태 수신**(#10820: principal 이 `Saml2AuthenticatedPrincipal` 아니면 registrationId 를 URL 에서 해소).
2. **SS 가 파싱된 NameID 를 LogoutHandler 에 안 넘김** — 현재 `Authentication` 의존. 서버 세션 없는 순수 Bearer 배포에서 LogoutRequest XML 의 NameID 를 **완전 무상태로** 뽑으려면 OpenSAML 디코더 확장 필요(확장점으로 남김).
3. **`LoginService` 는 `@ConditionalOnBean(Authenticator)`** — 비번 로그인 없는 **SAML 전용 앱엔 없을 수 있음** → `SamlSessionTerminator` 로 분리, 기본 종료기 `@ConditionalOnBean(LoginService)`. 없으면 앱이 `SamlSessionTerminator` 빈 직접 등록.
4. **토큰 무효화 완전 커버 전제** = `framework.security.concurrent-session.enabled=true` + 공유 TokenStore(redis). 사용자별 세션을 레지스트리로 열거해야 일괄 블랙리스트 가능(없으면 `logoutAllByUserId`=0).

## 켜는 법 (요약)
```yaml
framework:
  saml-sp:
    enabled: true
    slo:
      enabled: true            # IdP-initiated SLO 수신 ON (기본 false)
  security:
    concurrent-session:
      enabled: true            # ★ 토큰 무효화 완전 커버 전제. redis TokenStore 권장.
```
+ 앱이 `SamlLogoutUserResolver` 빈 등록(NameID→우리 userId; 미매칭 null=graceful). (SAML 전용 앱이면 `SamlSessionTerminator` 빈도 직접 등록.)

## 다음 (Next)
- **§6.2-B SP-initiated SLO**(우리앱→IdP): 로그인 시 SAML 로그아웃 주체(`{registrationId,nameId,sessionIndex}`)를 redis 영속(6.1 코덱 패턴) → 별도 브라우저 엔드포인트 `GET /saml2/logout` 에서 무상태 복원 후 LogoutRequest. 명시 요구 시.
- 또는 **§6.3** Authorization Server(별도 `services/auth-server`, 명시 요구 시·후순위) · **§6.4** Passwordless.

## 받는 쪽 검증
```bash
./gradlew :framework:framework-saml-sp:test :framework:framework-security:test :framework:framework-archtest:test spotlessApply
```
<!-- 갱신 끝 -->
