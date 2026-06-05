# framework-webauthn

패스키/WebAuthn(FIDO2) — 비밀번호 없는 강인증. **Spring Security 7 네이티브 패스키 지원**(`http.webAuthn()` DSL, 내부 WebAuthn4J)을 래핑하고, 프레임워크 정체성(자체 JWT/RBAC) 접합 + 영속 백엔드 선택 + 3단 토글만 더한다. `framework-security` 전제.

> WebAuthn 영속 SPI/구현은 코어 `spring-security-web` 이 아니라 별도 아티팩트 **`spring-security-webauthn`** 에 있다(공식 이슈 #18377). WebAuthn4J 가 전이된다.

## 켜는 법
```gradle
dependencies {
    implementation project(':framework:framework-webauthn')        // framework-security 전제
    implementation 'org.springframework.security:spring-security-webauthn'  // ⚠️ 필수(JDBC 영속 클래스 포함)
    implementation 'org.springframework.boot:spring-boot-starter-web'
    // store.type=jdbc 일 때:
    implementation 'org.springframework.boot:spring-boot-starter-jdbc'
}
```
```yaml
framework:
  webauthn:
    enabled: true                 # 기본 false (opt-in)
    rp-id: example.com            # Relying Party ID = 등록 가능 도메인(서브도메인 공유). 로컬은 localhost
    rp-name: SI-MSA               # 인증기 UI 표시명(미설정 시 rp-id)
    allowed-origins:              # ceremony 허용 origin(게이트웨이 Ingress 호스트/TLS). 로컬은 http://localhost:8080
      - https://app.example.com
    token-path: /api/v1/auth/webauthn/token   # 패스키 인증(세션) → 프레임워크 JWT 교환
    store: { type: memory }       # memory(개발) | jdbc(영속 — 재기동 후 자격증명 유지)
```
- **전제(앱 제공)**: `http.webAuthn()` 은 assertion 검증 시 권한 적재에 `UserDetailsService` 빈을 **필수**로 요구한다(없으면 부팅 실패).
- **HTTPS 필수**: WebAuthn 은 SecureContext 에서만 동작(localhost 예외). dev/prod 는 Ingress TLS 전제(`deploy/k8s/overlays/prod/ingress-prod.yaml`).

## 쓰는 법
- 무상태(JWT) 주류 체인은 세션/CSRF 가 없어 ceremony 와 상충한다. 이 모듈은 `/webauthn/**`·`/login/webauthn`·`token-path` 에만 적용되는 **세션+CSRF 전용 SecurityFilterChain** 을 더 높은 우선순위로 자동 등록한다(메인 catch-all 체인과 공존).
- 패스키 인증이 세션에 인증을 수립하면, SPA 가 `token-path` 를 호출해 **프레임워크 표준 JWT** 로 교환한다. 이후는 무상태 주류로 동작(외부 IdP 성공 후 자체 JWT 발급하는 oauth-client 패턴과 동일).

표준 엔드포인트(SS 자동 노출):

| 단계 | 메서드/경로 | 인증 |
|---|---|---|
| 등록 옵션 | `POST /webauthn/register/options` | 세션 인증(현재 사용자) |
| 등록 | `POST /webauthn/register` | 세션 인증 |
| 인증 옵션 | `POST /webauthn/authenticate/options` | permitAll |
| 인증(assertion) | `POST /login/webauthn` | permitAll → 성공 시 세션 인증 수립 |
| **JWT 교환** | `POST {token-path}` | 세션 인증 → `TokenResponse` 발급 |

## 실전 사용 예 (코드)

앱은 `UserDetailsService` 만 제공하면 된다(나머지는 자동). 패스키 등록은 인증된 세션에서 1회 수행(예: 비밀번호 1차 로그인 후), 이후 패스키로 비밀번호 없이 로그인한다.

```java
// 1) 앱이 제공해야 하는 유일한 빈 — 사용자/권한 적재(SS WebAuthnAuthenticationProvider 가 사용)
@Bean
UserDetailsService userDetailsService() {
    return username -> User.withUsername(username)
            .password("{noop}")          // 패스키 로그인은 비밀번호 미사용
            .roles("USER")
            .build();
}
```
```java
// 2) 패스키 인증 성공 → 프레임워크 JWT 교환은 내장 WebAuthnTokenController 가 처리.
//    Authentication → AuthenticatedUser 매핑을 커스터마이즈하려면 resolver 를 교체(@ConditionalOnMissingBean):
//    com.company.framework.webauthn.web.WebAuthnAuthenticatedUserResolver
@Bean
WebAuthnAuthenticatedUserResolver webAuthnUserResolver() {
    return auth -> new AuthenticatedUser(
            auth.getName(),                 // user handle ↔ 내부 userId 매핑 지점
            auth.getName(),
            auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                    .toList());
}
```
```bash
# 인증 ceremony(브라우저 navigator.credentials.get 가 수행) 후, 세션 쿠키로 JWT 교환:
curl -X POST https://app.example.com/api/v1/auth/webauthn/token \
  -H "X-XSRF-TOKEN: <쿠키의 XSRF-TOKEN>" -b cookies.txt
# → { "data": { "accessToken": "...", "refreshToken": "...", "tokenType": "Bearer", ... } }
```

### JDBC 영속 저장소
`store.type=jdbc` 면 SS 의 `JdbcPublicKeyCredentialUserEntityRepository`/`JdbcUserCredentialRepository` 로 자격증명을 영속한다. DDL: `src/main/resources/db/webauthn-postgres.sql`(PG, BYTEA) / `webauthn-h2.sql`(H2, BLOB). Flyway 권장. (SS 원본 스키마는 `classpath:org/springframework/security/user-{entities,credentials}-schema.sql`)

## 끄는 법
`framework.webauthn.enabled: false`(기본) 또는 의존성 미포함. 끄면 전용 체인/엔드포인트가 미등록되어 무상태 주류 체인에 **아무 영향 없다**.

## 덮어쓰기(프로젝트 커스텀)
다음 SPI 빈 등록 시 자동 교체(`@ConditionalOnMissingBean`):
- `WebAuthnRelyingPartyOperations` — RP 연산 전체.
- `PublicKeyCredentialUserEntityRepository` / `UserCredentialRepository` — 저장소.
- `WebAuthnTokenIssuer` — 토큰 발급(기본 `DirectWebAuthnTokenIssuer`=JwtProvider/TokenStore 직접). 동시로그인 제어·감사까지 묶으려면 LoginService 위임 구현으로 교체.
- `WebAuthnAuthenticatedUserResolver` — `Authentication`→`AuthenticatedUser` 매핑.

## 버전 관리
**신규 외부 의존성 0**(런타임은 앱이 `spring-security-webauthn` 제공). web/jdbc/spring-security-webauthn 은 `compileOnly` — 테스트는 재선언 필요(`@ConditionalOnClass` 무관하게 ApplicationContextRunner 가 로드) [PITFALLS §4]. 메인 체인 억제 회피를 위해 `@AutoConfiguration(after = SecurityAutoConfiguration.class)` + 전용 체인 무가드 `@Order` [PITFALLS: 다중 SecurityFilterChain 함정].
