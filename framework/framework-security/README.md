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

## 끄는 법
`framework.security.enabled: false` 또는 의존성 미포함. 개발 우회는 프로파일 `local,local-noauth`(`dev-auth`) — **운영 금지**(`DevAuthSafetyGuard` 가 prod 차단).

## 덮어쓰기(프로젝트 커스텀)
`Authenticator`·`PasswordHistoryStore`·`ConcurrentSessionService`·`MfaGate` 등 SPI 빈을 등록하면 기본 구현이 양보한다(`@ConditionalOnMissingBean`).

## 버전 관리
jjwt 0.12.6(api+impl+jackson). Spring Security 7(SS7) 의미가 이전과 다르므로 업그레이드 시 `../../docs/reference/CHANGES_AND_DEPRECATIONS.md` 확인.
