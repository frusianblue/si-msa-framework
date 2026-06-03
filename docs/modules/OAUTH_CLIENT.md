# framework-oauth-client — 소셜 로그인(OAuth 2.0 / OIDC) 사용 가이드

> 외부 IdP(구글·카카오·네이버 등)로 **신원만 위임**받고, 우리 시스템은 그 결과로 **자체 JWT 를 발급**한다.
> 발급 이후 흐름(액세스/리프레시 토큰, 검증 필터, 로그아웃)은 자체 비밀번호 로그인과 완전히 동일하다.
>
> 한 줄 요약: **소셜 로그인 = "외부에서 본인확인 → 우리 토큰 발급"**. 비밀번호를 우리가 보관하지 않는다.

---

## 1. 활성화 3단계 (다른 모듈과 동일한 토글 규약)

1. **모듈 의존성 추가** — 이 모듈이 클래스패스에 있어야 한다.
2. **`framework.oauth-client.enabled=true`** — 기능 on.
3. **`OAuthUserResolver` 빈 등록** — 외부 사용자를 우리 사용자로 매핑하는 구현(아래 3절). 이게 없으면 조용히 비활성.

세 가지가 모두 갖춰질 때만 컨트롤러/서비스가 등록된다. (자체 로그인이 `Authenticator` 빈으로 켜지는 것과 같은 방식.)

### build.gradle

```gradle
dependencies {
    implementation project(':framework:framework-oauth-client')
    // 자체 로그인도 함께 쓰면 framework-security 는 이미 들어와 있다(oauth-client 가 전이).
    // 다중 파드에서 state 공유가 필요하면 redis 도:
    // implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}
```

---

## 2. application.yml 설정

`google`/`kakao`/`naver` 는 표준 엔드포인트·속성 키가 프리셋으로 내장되어 있어 **client-id/secret 만** 채우면 된다.

```yaml
framework:
  oauth-client:
    enabled: true
    base-redirect-uri: "https://api.example.com"   # 콜백 베이스(끝 슬래시 유무 무관)
    state:
      ttl: PT5M
      store:
        type: redis          # memory(기본) | redis  ← 다중 파드면 redis 필수
        key-prefix: "oauth:state:"
    providers:
      google:
        client-id: "${GOOGLE_CLIENT_ID}"
        client-secret: "${GOOGLE_CLIENT_SECRET}"
        # authorization-uri/token-uri/user-info-uri/scope/*-attribute 는 프리셋 자동 적용
      kakao:
        client-id: "${KAKAO_REST_API_KEY}"
        client-secret: "${KAKAO_CLIENT_SECRET}"   # 카카오 콘솔에서 secret 사용 설정 시
        scope: [account_email, profile_nickname]   # 동의항목(콘솔과 일치)
```

> **redirect_uri 주의** — IdP 콘솔(구글/카카오 개발자센터)에 등록한 redirect URI 와 정확히 같아야 한다.
> 미지정 시 자동값은 `{base-redirect-uri}/api/v1/auth/oauth/{provider}/callback` 이다. 콘솔에 이 값을 등록하라.

프리셋이 없는 임의 IdP 는 `authorization-uri`/`token-uri`/`user-info-uri`/`user-name-attribute`/`email-attribute`/`name-attribute` 를 직접 지정하면 그대로 동작한다(중첩 응답은 점 표기, 예: `response.id`).

---

## 3. OAuthUserResolver 구현 (프로젝트가 작성하는 유일한 코드)

외부 신원(`OAuthUserInfo`)을 우리 사용자(`AuthenticatedUser`)로 바꾼다. 보통 "연동계정 조회 → 없으면 가입(JIT)".

```java
@Component
public class MyOAuthUserResolver implements OAuthUserResolver {

    private final UserMapper userMapper; // 프로젝트의 사용자 저장소

    public MyOAuthUserResolver(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public AuthenticatedUser resolve(OAuthUserInfo info) {
        // 1) provider + providerId 로 기존 연동 조회
        User user = userMapper.findBySocial(info.provider(), info.providerId());

        // 2) 없으면 즉시 가입(JIT provisioning). 정책상 이메일로 기존 계정 연동도 가능.
        if (user == null) {
            user = User.ofSocial(info.provider(), info.providerId(), info.email(), info.name());
            userMapper.insert(user);
        }

        // 3) 우리 토큰에 담길 표준 모델 반환(roles 는 우리 시스템 권한)
        return new AuthenticatedUser(
                String.valueOf(user.getId()),
                user.getName(),
                user.getRoles());            // 예: List.of("USER")
    }
}
```

가입을 막고 싶으면(사전 등록 사용자만 허용) 2)에서 `throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "등록되지 않은 사용자")` 하면 로그인이 거부된다.

---

## 4. 엔드포인트와 프론트 연동

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/v1/auth/oauth/{provider}/authorize` | IdP 인가 페이지로 302 redirect (state 자동 발급) |
| GET | `/api/v1/auth/oauth/{provider}/callback` | IdP 가 호출 → 자체 토큰(JSON) 반환 |

콜백 응답은 자체 로그인(`/api/v1/auth/login`)과 동일한 `TokenResponse` 다.

```json
{
  "success": true,
  "message": "소셜 로그인 성공",
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "9f3c...",
    "tokenType": "Bearer",
    "expiresInSeconds": 1800,
    "roles": ["USER"]
  }
}
```

전형적 프론트 흐름:

1. 프론트의 "구글로 로그인" 버튼 → `GET /api/v1/auth/oauth/google/authorize` 로 이동(브라우저 redirect).
2. 사용자가 구글에서 로그인/동의 → 구글이 `.../callback?code=...&state=...` 호출.
3. 콜백이 `TokenResponse` 를 반환 → 이후 모든 API 는 `Authorization: Bearer {accessToken}` 로 호출.

> 콜백을 SPA 가 직접 받게 하려면, 콜백을 프론트 라우트로 두고 거기서 이 API 를 호출하거나, 팝업 방식으로
> 토큰을 부모 창에 전달하는 패턴을 쓴다. (1단계에서는 안전하게 JSON 반환만 제공. 토큰을 쿼리스트링으로
> redirect 시키는 방식은 노출 위험이 있어 기본 제공하지 않는다.)

---

## 5. 보안 메모

- **state(CSRF)**: authorize 에서 발급한 1회용 토큰을 callback 에서 검증·소비한다. 재사용/위조/만료는 거부.
- **다중 파드**: authorize 와 callback 이 서로 다른 파드로 갈 수 있으므로 운영(K8s)에서는 `state.store.type=redis` 가 사실상 필수. memory 는 단일 인스턴스/로컬 전용.
- **권한 경로**: `/api/*/auth/**` 는 `SecurityAutoConfiguration` 화이트리스트에 포함되어 있어 별도 설정 없이 열린다.
- **시크릿**: client-secret 은 환경변수/시크릿 매니저로 주입(평문 커밋 금지).

---

## 6. 고급 — 동시 로그인 제어/감사까지 통합하기

기본 발급기(`DirectOAuthTokenIssuer`)는 JWT+Refresh 만 발급한다. 자체 로그인처럼 **동시 로그인 제어·접속 감사**까지
소셜 로그인에도 적용하려면, `LoginService` 에 위임하는 `OAuthTokenIssuer` 를 프로젝트가 등록해 교체한다(자동 override).

```java
@Bean
OAuthTokenIssuer loginServiceTokenIssuer(LoginService loginService) {
    return user -> loginService.completeMfa(user.userId(), user.roles(), null);
    // completeMfa 는 "신원확인된 사용자에게 표준 발급"을 수행(동시세션 등록/감사 포함).
}
```

(주: 위 방식은 감사 이벤트가 MFA_SUCCESS 로 적재되는 한계가 있다. OAuth 전용 발급 이벤트는 다음 단계에서
`framework-security` 에 범용 발급 메서드를 추가하며 정리할 예정.)

---

## 7. 빠른 점검 체크리스트

- [ ] 의존성 추가 + `framework.oauth-client.enabled=true`
- [ ] `OAuthUserResolver` 빈 등록
- [ ] IdP 콘솔에 redirect URI 등록(`{base}/api/v1/auth/oauth/{provider}/callback`)
- [ ] client-id/secret 환경변수 주입
- [ ] 다중 파드면 `state.store.type=redis` + redis 의존성
