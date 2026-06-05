# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 진입점은 `../00_INDEX.md`, 전체 맥락은 `HANDOFF.md`(같은 폴더), 사용법은 `../../README.md`, 버전은 `../reference/STACK.md`, 모듈 설계는 `../FRAMEWORK_MODULES.md`. (문서는 역할별 재구조화됨 — 작업기록은 `_internal/`.)

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**A1 WebAuthn/패스키 1차 슬라이스 구현(2026-06-05).** 킥오프 `NEXT_WEBAUTHN.md` 대로 신설 모듈 `framework-webauthn` 작성 — **SS7 네이티브 `http.webAuthn()` DSL 래핑**. 착수 철칙(SS7 실 API 재대조·추측 금지)에 따라 spring-security 를 blobless 클론해 DSL/SPI/생성자/스키마를 **소스로 전수 확인**한 뒤 코딩. 무상태 주류 ↔ ceremony 충돌은 **세션+CSRF 전용 SecurityFilterChain**(경로 한정·고우선순위)로 국소화하고, 패스키 인증(세션)→**토큰교환 엔드포인트로 자체 JWT 발급**(oauth/saml `DirectTokenIssuer` 패턴 대칭). 저장소 memory|jdbc(SS `Map*`/`Jdbc*Repository`, 실 DDL 동봉). **framework-security 무수정**(`@AutoConfiguration(after=SecurityAutoConfiguration)`+무가드 `@Order`로 메인 체인 공존). 배선 5종·문서 6종·토글 테스트 완비.

## 최종 갱신
- 일자: 2026-06-05 · 갱신자: A1 WebAuthn 1차 슬라이스 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / Jackson 3(tools.jackson.*) — **스택 무변경**(런타임 `spring-security-webauthn` 은 앱 제공=compileOnly, 새 외부 의존성 0)

## 직전에 한 것 (Done — 정적 검증, 받는 쪽 컴파일/테스트 확인)
- **신설 모듈 `framework-webauthn`** — 3단 토글(`@ConditionalOnClass(WebAuthnRelyingPartyOperations)`→`framework.webauthn.enabled`→`store.type=memory|jdbc`). 파일: `build.gradle`·`WebAuthnProperties`·`WebAuthnAutoConfiguration`(repos memory/jdbc + `WebAuthnRelyingPartyOperations`(`Webauthn4JRelyingPartyOperations`) + 전용 체인 + 토큰교환)·`WebAuthnTokenIssuer`/`DirectWebAuthnTokenIssuer`·`WebAuthnAuthenticatedUserResolver`/`Default*`·`WebAuthnTokenController`·`.imports`·`db/webauthn-{postgres,h2}.sql`·`README.md`·토글 테스트.
- **SS7 API 그라운딩(소스 확인)** — DSL `http.webAuthn(w->w.rpId().rpName().allowedOrigins())`(successHandler 미노출), `webAuthn()` 은 `UserDetailsService` 빈 **필수**, RP/저장소 빈 자동 픽업. 관리 패키지 `...web.webauthn.management`, API `...web.webauthn.api`. 아티팩트 `spring-security-webauthn`(코어 web 아님, #18377). 실 DDL `user_entities`/`user_credentials`(BLOB→PG BYTEA).
- **배선 5종** — settings.gradle · 루트 build.gradle jacocoAggregation · framework-archtest testImplementation · framework/README 🔐 행 · FRAMEWORK_MODULES 카탈로그.
- **문서** — 모듈 README(켜기/쓰기/실전코드/끄기/덮어쓰기) · AUTH_COMPOSITION_GUIDE §0표·§7(🟡→🟢/✅) · PITFALLS §4(다중 SecurityFilterChain↔@ConditionalOnMissingBean 순서 함정) +§5(WebAuthn 5종: 아티팩트·UserDetailsService 필수·ceremony↔무상태·HTTPS/rpId·BLOB→BYTEA 스키마).

## 현재 상태 (적용/검증)
- **✅ 받는 쪽 통과 확인(2026-06-05)**: `:framework:framework-webauthn:test`(토글 3/3 — enabled→RP+memory·disabled→백오프·store=jdbc→Jdbc* 선택) + `spotlessApply` + `:framework:framework-archtest:test` 모두 그린. (테스트 수정 1건 반영: jdbc 토글 테스트의 H2 임베디드 DataSource → mock DataSource. webauthn 모듈 test 클래스패스에 H2 부재 → 컨텍스트 기동 실패였음. JDBC 리포지토리 생성자는 DB 미접근이라 mock 으로 충분.)
- 작성환경 Maven Central 차단으로 본 세션 코드는 SS 소스 대조 기반 정적 작성 → 컴파일/테스트는 위와 같이 받는 쪽 확인. **브라우저 ceremony 실서명(등록/인증 라운드트립)은 web 앱(`UserDetailsService`+`spring-security-webauthn`+HTTPS) 에서 검증 잔여.**

## 바로 다음 할 일 (Next)
- 받는 쪽에서 `:framework:framework-webauthn:test` + `spotlessApply` + (web 앱) ceremony 라운드트립 확인.
- **A1 후속**: ① 2차 MFA factor 연계(mfa `MfaMethod` 에 `WEBAUTHN` 추가 검토) ② passkey 관리 UX(목록/삭제 `DELETE /webauthn/{id}`) ③ rpId/origin 멀티서비스 일원화 정책 문서.
- 이후 A2(SP-initiated SLO)·A3(KMS/Vault) 각 독립 세션.

## 이번 세션에서 새로 박힌 함정 (되돌리지 말 것)
- **두 번째 `SecurityFilterChain` = 메인 체인 억제** — 메인은 `@ConditionalOnMissingBean(SecurityFilterChain.class)` 가드. 회피: 추가 체인 모듈은 `@AutoConfiguration(after=SecurityAutoConfiguration)` + 무가드 `@Order(HIGHEST_PRECEDENCE+50)` + `securityMatcher` 경로 한정(saml-sp·webauthn 동일) [PITFALLS §4].
- **`http.webAuthn()` 은 `UserDetailsService` 빈 필수**(없으면 부팅 실패) + **HTTPS(SecureContext) 전제** + rpId↔origin 정합 [PITFALLS §5].
- **WebAuthn JDBC 클래스/스키마는 `spring-security-webauthn`** — 스키마는 SS 번들 BLOB(`classpath:org/springframework/security/user-{entities,credentials}-schema.sql`), PG 는 BYTEA 치환 [PITFALLS §5].
- **Boot4 패키지 이동 회피** — 체인 우선순위는 `SecurityProperties.BASIC_AUTH_ORDER`(패키지 이동 위험) 대신 `Ordered.HIGHEST_PRECEDENCE+50` 사용(saml-sp 선례) [PITFALLS §3].
<!-- 갱신 끝 -->
