# framework-security

인증/인가 **[코어]**. JWT 무상태 인증·DB 기반 RBAC(동적 인가)·메뉴 관리·비밀번호 정책·로그인 잠금·동시세션 제어를 제공하고, MFA·소셜·SAML 의 토대(자체 JWT 발급)가 된다.

## 켜는 법
`framework-core`/`framework-mybatis` 위 [코어] 모듈. 인증이 필요한 서비스가 의존하면 적용된다.
```yaml
framework:
  security:
    enabled: true
    dynamic-authorization: true   # DB 기반 동적 인가(DynamicAuthorizationManager)
    menu: true                    # 메뉴-권한 관리 API
    jwt:
      # secret/만료 등 — JwtSecretSafetyGuard 가 약한 키 기동 차단
    token-store:
      type: memory                # memory(기본) | jdbc | redis(framework-redis 필요)
    login-attempt:
      type: memory                # memory | jdbc | redis — 5회 실패 시 잠금
    password:
      policy-enabled: true        # 강도: min-length 9, 문자 3종 이상
      expiry:  { enabled: false, max-age: 90d, warn-before: 14d }
      history: { enabled: false, count: 3 }     # 직전 N개 재사용 금지
    concurrent-session:
      enabled: false              # 중복 로그인 제어
```
> 멀티 인스턴스(replicas≥2)는 `token-store.type` / `login-attempt.type` 을 `redis`(또는 `jdbc`)로 — `memory` 는 인스턴스별이라 잠금 우회 가능.

## 쓰는 법

**로그인** — `POST /api/v1/auth/login {loginId,password}` → JWT. 이후 `Authorization: Bearer <JWT>`.
**현재 사용자** — `AuthenticatedUser` 주입 또는 `CurrentUserProvider`.
**인가** — DB 기반 RBAC 가 URL↔권한을 동적 매핑(`DynamicAuthorizationManager`). 메서드 보안은 `@PreAuthorize`.
**비밀번호 변경** — `PATCH /api/v1/users/me/password`(현재 비번 필요) / 관리자 강제 초기화 `PATCH /api/v1/users/{id}/password/reset`(ADMIN).
**MFA 연동** — `MfaGate` SPI 가 있으면(=framework-mfa) 로그인이 2단계로 분기, 없으면 단일 단계.

토큰 검증 상세는 [`../../docs/reference/TOKEN_VERIFICATION_GUIDE.md`](../../docs/reference/TOKEN_VERIFICATION_GUIDE.md).

## 인증 모드: 무상태(JWT) vs 서버 세션

기본은 **무상태 JWT**(위). 브라우저가 곧 클라이언트인 경우(관리자 콘솔·SSR·레거시 호환)는 **서버 세션 모드**로 전환할 수 있다 — 코어 토글 하나, 추가 모듈 0(단일 인스턴스 기준).

```yaml
framework:
  security:
    session:
      mode: session     # stateless(기본) | session
      csrf: true        # 세션 모드 CSRF 보호(쿠키 더블서브밋). 기본 on
      cookie-name: SESSION
```

- **엔드포인트**: `POST /api/v1/auth/session/login {loginId,password}` → 세션 수립(토큰 미발급, `Set-Cookie`) · `POST /api/v1/auth/session/logout` → 세션 무효화.
- **인가·로그인 잠금 동일**: 세션 모드도 동일한 `ROLE_*` 권한 형태로 컨텍스트를 세우므로 RBAC·`@PreAuthorize`·로그인 잠금이 JWT 경로와 똑같이 동작한다.
- **세션 고정 방어**: 로그인 성공 시 세션ID를 회전(`changeSessionId`)한다.
- **CSRF**: SPA가 쿠키의 원시 토큰을 동봉하는 형태(평문 핸들러). 로그인/로그아웃 경로는 CSRF 면제. 순수 토큰 API만 쓰면 `csrf: false`.
- **멀티 인스턴스(replicas≥2)**: 세션을 공유해야 하므로 [`framework-session`](../framework-session/README.md)(Spring Session Redis) 추가. `spring.session.data.redis.*`(Boot4 키)로 설정.
- **dev-auth 우선순위**: `dev-auth` 프로파일이 켜지면 두 모드 모두 무시하고 개발 체인이 우선한다.

> 모드 선택 가이드: 순수 API(모바일/외부 연계)는 JWT, 브라우저 중심(콘솔/SSR)은 세션. 결정·레시피는 [`../../docs/guide/AUTH_COMPOSITION_GUIDE.md`](../../docs/guide/AUTH_COMPOSITION_GUIDE.md) R7.


## 끄는 법
`framework.security.enabled: false` 또는 의존성 미포함. 개발 우회는 프로파일 `local,local-noauth`(`dev-auth`) — **운영 금지**(`DevAuthSafetyGuard` 가 prod 차단).

## 덮어쓰기(프로젝트 커스텀)
`Authenticator`·`PasswordHistoryStore`·`ConcurrentSessionService`·`MfaGate` 등 SPI 빈을 등록하면 기본 구현이 양보한다(`@ConditionalOnMissingBean`).

## 버전 관리
jjwt 0.12.6(api+impl+jackson). Spring Security 7(SS7) 의미가 이전과 다르므로 업그레이드 시 `../../docs/reference/CHANGES_AND_DEPRECATIONS.md` 확인.
