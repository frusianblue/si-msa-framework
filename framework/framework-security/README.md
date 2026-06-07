# framework-security

인증/인가 **[코어]**. JWT 무상태 인증·DB 기반 RBAC(동적 인가)·메뉴 관리·비밀번호 정책·로그인 잠금·동시세션 제어를 제공하고, MFA·소셜·SAML 의 토대(자체 JWT 발급)가 된다.

## 켜는 법
`framework-core` 위 [코어] 모듈(인증만 강제 — `framework-mybatis`/DataSource 불필요). 인증이 필요한 서비스가 의존하면 적용된다.
> **RBAC(동적 인가/메뉴)는 별도 어댑터로 분리됐다** — DB 동적 인가/메뉴를 쓰려면 `framework-security-rbac-mybatis` 의존 한 줄 추가
> (보안-영속 결합 분리: 코어는 더 이상 MyBatis/DataSource 를 강제하지 않는다). 자세히는 [`../framework-security-rbac-mybatis/README.md`](../framework-security-rbac-mybatis/README.md).
```yaml
framework:
  security:
    enabled: true
    dynamic-authorization: true   # DB 기반 동적 인가(DynamicAuthorizationManager) — framework-security-rbac-mybatis 어댑터 필요(없으면 부팅 fail-fast)
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
> ℹ️ **jdbc 백엔드**(token-store/password.history/concurrent-session `type=jdbc`)는 host 앱이 `spring-boot-starter-jdbc`(+DataSource)를 제공할 때만 활성화된다 — 보안-영속 결합 분리로 코어는 `spring-jdbc` 를 `compileOnly` 로만 둔다(`@ConditionalOnClass(JdbcTemplate)` 가드). `framework-mybatis`/`framework-datasource` 를 쓰는 서비스는 이미 충족.

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

## 실전 사용 예 (코드)

개발자가 실제로 작성하는 코드는 **`Authenticator` 구현 하나**가 거의 전부다. 나머지(로그인 엔드포인트·토큰 발급·RBAC·잠금)는 프레임워크가 제공한다.

### 1) 프로젝트가 구현하는 단 하나의 인증 계약 — `Authenticator`
DB/LDAP/SSO/GPKI 등 방식이 달라도 이 인터페이스만 구현해 빈으로 등록하면 공통 로그인 흐름(JWT·세션 양쪽)이 동작한다(`AuthAutoConfiguration` 이 `@ConditionalOnBean(Authenticator.class)`).
```java
@Component
public class DbAuthenticator implements Authenticator {

    private final UserMapper userMapper;           // MyBatis 매퍼(예시)
    private final PasswordEncoder passwordEncoder; // framework-security 가 빈 제공(BCrypt 등)

    public DbAuthenticator(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthenticatedUser authenticate(LoginCommand command) {
        UserRow row = userMapper.findByLoginId(command.loginId());
        if (row == null || !passwordEncoder.matches(command.password(), row.getPasswordHash())) {
            // 실패는 예외로 — 프레임워크가 잠금 카운트 증가/감사 이벤트 발행을 처리한다.
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        // roles 는 접두어 없이 넣어도 됨 — 프레임워크가 ROLE_ 을 보장한다(예: "ADMIN" → ROLE_ADMIN).
        return new AuthenticatedUser(row.getUserId(), row.getName(), row.getRoles());
        // 추가 클레임이 필요하면: new AuthenticatedUser(userId, name, roles, Map.of("deptCode", row.getDept()))
    }
}
```

### 2) 로그인 호출 (JWT 무상태 — 기본)
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"loginId":"alice","password":"secret"}'
# 응답: { "data": { "accessToken": "...", "refreshToken": "..." }, ... }

curl http://localhost:8080/api/v1/users/me -H 'Authorization: Bearer <accessToken>'
```

### 3) 컨트롤러에서 현재 사용자 꺼내기
인증 주체는 표준 `@AuthenticationPrincipal` 로 주입된다(principal 의 username = userId, authorities = `ROLE_*`).
```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @GetMapping("/mine")
    public ApiResponse<List<OrderDto>> myOrders(@AuthenticationPrincipal User principal) {
        String userId = principal.getUsername();   // 인증 주체 = userId
        return ApiResponse.ok(orderService.findByUser(userId));
    }
}
```
> 감사필드(created_by/updated_by)는 별도 코드 없이 `CurrentUserProvider` 가 자동으로 채운다(framework-mybatis 연동). 서비스 계층에서 userId 가 필요하면 `CurrentUserProvider#getCurrentUser()` 주입.

### 4) 메서드 단위 인가 — `@PreAuthorize`
URL↔권한 동적 매핑(RBAC)과 별개로 메서드 보안도 그대로 사용(`@EnableMethodSecurity` 활성).
```java
@PreAuthorize("hasRole('ADMIN')")                 // ROLE_ADMIN 보유자만
public void deleteUser(String userId) { ... }

@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public Report monthlyReport() { ... }
```

### 5) 세션 모드 로그인 (브라우저/콘솔 — `session.mode=session` 일 때)
```bash
# 세션 수립(토큰 없이 Set-Cookie: SESSION=...)
curl -i -X POST http://localhost:8081/api/v1/auth/session/login \
  -H 'Content-Type: application/json' -c cookies.txt \
  -d '{"loginId":"admin","password":"secret"}'

# 이후 쿠키로 호출. CSRF 켜져 있으면 XSRF-TOKEN 쿠키값을 X-XSRF-TOKEN 헤더로 동봉.
curl http://localhost:8081/api/v1/admin/menus -b cookies.txt
```


## 끄는 법
`framework.security.enabled: false` 또는 의존성 미포함. 개발 우회는 프로파일 `local,local-noauth`(`dev-auth`) — **운영 금지**(`DevAuthSafetyGuard` 가 prod 차단).

**RBAC(동적 인가/메뉴)만 끄기** — `dynamic-authorization: false`(+ `menu: false`). 이러면 코어는 RBAC 빈을 만들지 않고
보안 체인은 `authenticated()` 로만 동작한다 → `framework-security-rbac-mybatis`/DataSource/MyBatis 없이 인증만으로 부팅.
(인증만 쓰는 데모/위임 서비스의 기본 형태.)

## 덮어쓰기(프로젝트 커스텀)
`Authenticator`·`PasswordHistoryStore`·`ConcurrentSessionService`·`MfaGate` 등 SPI 빈을 등록하면 기본 구현이 양보한다(`@ConditionalOnMissingBean`).

## 버전 관리
jjwt 0.12.6(api+impl+jackson). Spring Security 7(SS7) 의미가 이전과 다르므로 업그레이드 시 `../../docs/reference/CHANGES_AND_DEPRECATIONS.md` 확인.
