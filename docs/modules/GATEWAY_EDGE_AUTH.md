# 게이트웨이 엣지 인증 (Gateway Edge Authentication)

> 게이트웨이에서 JWT 를 **한 번 검증**하고, 통과한 요청에만 신뢰 신원 헤더(`X-User-Id`/`X-User-Roles`)를
> 박아 다운스트림으로 보낸다. 각 서비스는 토큰 재검증 부담을 덜고, `framework-context` 가 그 헤더를 그대로 읽는다.

---

## 동작 요약

```
[클라이언트] --Bearer JWT--> [게이트웨이]
                               │ 1) 클라이언트가 보낸 X-User-* 헤더 제거(스푸핑 차단)
                               │ 2) 화이트리스트(/api/*/auth/**, /actuator/**, /fallback/**)면 그냥 통과
                               │ 3) 그 외엔 JWT 검증 (실패 → 401)
                               │    iss 로 분기 — 자체 JWT=HMAC+typ=access / AS=RS256·JWKS (이중 발급기)
                               │ 4) sub→X-User-Id, roles→X-User-Roles 주입 + userId 로 레이트리밋
                               ▼
                          [user-service / admin-service ...]
                          framework-context 가 X-User-Id 를 RequestContext 로 바인딩
```

핵심 보안 포인트: **클라이언트가 직접 보낸 `X-User-Id` 등은 항상 먼저 제거**한다. 그래서 외부에서 헤더로 신분을
위장할 수 없고, 그 헤더는 오직 게이트웨이가 토큰을 검증했을 때만 채워진다. (`HeaderContextResolver` 주석의
"위조 방지는 게이트웨이가 책임진다"를 실제로 구현.)

---

## 켜는 법

`framework-security` 와 **같은 비밀키**를 게이트웨이에도 준다(게이트웨이는 WebFlux 라 security 모듈을 의존할 수
없어, 같은 키를 환경변수로 공유한다).

```yaml
# gateway/application.yml — 이미 추가됨. 운영에서 환경변수만 주입.
gateway:
  auth:
    enabled: ${GATEWAY_AUTH_ENABLED:false}   # 운영: true
    jwt-secret: ${JWT_SECRET:}                # = framework.security.jwt.secret
    token-type: access
    permit-all-patterns:
      - /api/*/auth/**
      - /actuator/**
      - /fallback/**
```

```bash
# 게이트웨이와 각 서비스에 동일한 JWT_SECRET 주입(예: K8s Secret)
export JWT_SECRET="32바이트-이상-운영-비밀키"
export GATEWAY_AUTH_ENABLED=true
```

기본 `enabled=false` 라 켜기 전에는 현재처럼 무인증 통과한다(점진 도입). `enabled=true` 인데 `jwt-secret` 이
비어 있으면 기동 시 명확히 실패한다(fail-fast).

---

## 다운스트림에서 받기

각 서비스는 `framework-context` 를 켜면 `X-User-Id` 를 자동으로 `RequestContext.userId` 로 바인딩한다.
권한(`X-User-Roles`)이 필요하면 헤더에서 직접 읽거나, 서비스가 `Authorization` 토큰을 그대로 재검증해도 된다
(게이트웨이는 Authorization 을 제거하지 않는다 — 심층 방어).

---

## 중앙 로그아웃(SSO) 연동

엣지 인증 위에 **중앙 로그아웃**을 옵트인으로 얹을 수 있다(`gateway.auth.blacklist-check.enabled=true`).
켜면 검증 통과한 토큰의 `jti` 를 공유 Redis 블랙리스트(`bl:{jti}`)와 대조해, 로그아웃된 토큰을 엣지에서 차단한다.
서명/만료만으로는 막을 수 없는 "로그아웃 후에도 살아 있는 토큰" 문제를 해결한다. 자세한 설정/설계는
[`SSO_CENTRAL_LOGOUT.md`](./SSO_CENTRAL_LOGOUT.md) 참고.

> **신뢰 경계**: "게이트웨이를 거친 요청만 신뢰"는 네트워크에서 보장하는 게 정석이다. K8s 라면 NetworkPolicy 로
> 백엔드 서비스의 인그레스를 게이트웨이 파드로만 제한하라. `AddRequestHeader=X-Gateway` 같은 상수 헤더는
> 위조 가능하므로 신뢰 근거로 쓰지 말 것.

---

## 이중 발급기 (자체 JWT + Authorization Server)

게이트웨이는 **두 종류의 발급자**를 동시에 받을 수 있다(옵트인). 설계 배경/경계는
[`AUTH_SERVER.md`](./AUTH_SERVER.md) §4 가 정본이다.

| 구분 | 자체 JWT (INTERNAL) | AS 토큰 (AUTHORIZATION_SERVER) |
|---|---|---|
| 발급자 | `framework-security` `JwtProvider` | `services/auth-server` (OP) |
| 서명 | HMAC(대칭키, `jwt-secret` 공유) | RS256(JWKS 공개키) |
| `iss` | 없음 | AS issuer(설정값) |
| 검증 | 서명+만료+`typ=access` | 서명(JWKS)+만료+`iss` 일치+`sub` 필수 |
| 폐기 | jti 블랙리스트(중앙 로그아웃) | **AS `/oauth2/revoke`** (게이트웨이 블랙리스트 미적용) |

분기 방식: 토큰 payload 의 `iss` 를 **서명 검증 전에 엿보고**(라우팅 전용), AS issuer 와 같으면 JWKS 경로,
아니면 자체 JWT 경로로 보낸다. **`iss` 엿보기는 신뢰 경계가 아니다** — 분기된 경로가 서명을 끝까지 검증하므로,
`iss` 를 위조해도 라우팅만 바뀔 뿐 위조 토큰은 통과하지 못한다(잘못 분기되면 그 경로의 서명 검증에서 탈락).

구현 노트:
- WebFlux 게이트웨이에 servlet 의존(`framework-oauth-client`)을 끌어오지 않기 위해, `JwksKeyResolver` 패턴
  (kid 캐시 TTL + 미발견 시 1회 강제 재조회 + 쿨다운 + 단일키 fallback)을 게이트웨이 안에 **자립적으로 재현**했다
  (`GatewayJwksTokenVerifier`, jjwt `keyLocator`).
- JWKS 조회는 블로킹 IO(`RestClient`)이므로 **AS 토큰일 때만** `boundedElastic` 스케줄러로 오프로드한다.
  자체 JWT 핫패스는 이벤트 루프에서 그대로(CPU only) — 도입 전 성능 특성 유지.
- **§4 경계**: AS 토큰은 자체 jti 블랙리스트를 조회하지 않는다(혼용 금지). 다운스트림(`framework-context`)은
  주입된 `X-User-Id`/`X-User-Roles` 만 읽으므로, AS 발급자 수용을 위해 **프레임워크/다운스트림 코드는 무변경**이다.

### AS 경로 켜는 법

```yaml
gateway:
  auth:
    enabled: true                                  # 엣지 인증 전체
    jwt-secret: ${JWT_SECRET:}                     # 자체 JWT(HMAC)
    authorization-server:
      enabled: ${GATEWAY_AS_ENABLED:false}         # ★ AS 경로 옵트인(기본 off)
      issuer: ${AUTH_SERVER_ISSUER:}               # = services/auth-server 의 auth-server.issuer
      jwks-uri: ${AUTH_SERVER_JWKS_URI:}           # 생략 시 {issuer}/oauth2/jwks 자동
      roles-claim: roles                           # AS RoleClaimTokenCustomizer 와 정합
      clock-skew: 60s
      jwk-cache-ttl: 1h
      audiences: []                                # 비우면 aud 검증 생략(기본). 설정 시 토큰 aud 중 하나가 목록에 있어야 통과
```
> `audiences` 를 설정하면 서명·`iss`·`exp` 에 더해 **`aud` 클레임**까지 검증한다(혼동된 대리 방지 — 다른 RP/리소스용으로 발급된 AS 토큰을 게이트웨이가 받아주지 않는다). 보통 게이트웨이 뒤 리소스 식별자(또는 AS 가 발급에 넣는 audience 값)를 넣는다. 비우면 하위호환(검증 생략).

```bash
export GATEWAY_AS_ENABLED=true
export AUTH_SERVER_ISSUER="https://auth.yourcompany.com"   # auth-server 와 동일 값
```

기본 `authorization-server.enabled=false` → 켜기 전에는 자체 JWT(HMAC)만 검증(도입 전 동작 100% 동일).
`enabled=true` 인데 `issuer` 가 비면 기동 시 fail-fast.

---

## 레이트리밋 연동 (덤)

기존 `RequestRateLimiter` 의 `principalKeyResolver` 는 인증이 없어 늘 IP 로 강등됐다. 이제 엣지 인증이 검증한
userId 를 우선 사용하므로 **사용자 단위 레이트리밋**이 동작한다(로그인 안 한 트래픽만 IP 기준).

---

## 검증 (받는 환경에서)

```bash
./gradlew :services:gateway:compileJava :services:gateway:test
```

⚠️ 작성 환경은 Maven Central 접근이 막혀 빌드를 직접 돌리지 못했다. 게이트웨이 런타임은 점검 보류 상태였으므로,
위 명령으로 컴파일/단위테스트를 먼저 확인하고, 가능하면 `bootRun` 으로 401/통과/헤더주입을 실제로 찍어보길 권한다.
