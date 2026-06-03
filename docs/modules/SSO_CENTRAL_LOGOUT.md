# 사내 다중 서비스 SSO — 중앙 로그아웃 (Central Logout)

> 인증 로드맵 3) SSO 의 첫 조각 **(A) 사내 다중 서비스 SSO**. "한 번 로그인 → 전 서비스 사용 →
> **한 번 로그아웃 → 전 서비스 즉시 차단**" 을 완성한다. 신규 모듈 없이 기존
> `framework-security`(TokenStore) + `services/gateway`(엣지 필터) 확장으로 구현.

---

## 왜 필요한가

stateless JWT 는 같은 비밀키를 공유하면 모든 서비스가 같은 토큰을 인정한다(이미 게이트웨이 엣지 인증으로 달성).
하지만 JWT 는 **서명/만료만으로는 무효화할 수 없다** — 로그아웃해도 토큰 유효기간이 끝날 때까지 살아 있다.

해결: 로그아웃 시 access 토큰의 `jti` 를 **공유 저장소(Redis)** 에 블랙리스트로 남기고, 게이트웨이가 매 요청마다
그 `jti` 를 **논블로킹으로 조회**해 차단한다. 발급기가 stateless 인 장점은 유지하면서, 무효화만 공유 저장소로
일원화한다.

---

## 동작

```
[로그인 서비스] /logout
   └ LoginService.logout(access, refresh)
        ├ refresh 제거 + 동시세션 해제
        └ access 의 jti 를 bl:{jti} 로 블랙리스트(남은 TTL 동안)   ──┐
                                                                      │ 공유 Redis
[게이트웨이] 매 요청                                                   │
   └ JWT 서명/만료/typ 검증 OK → jti 추출 → hasKey("bl:{jti}") ◀──────┘
        ├ 블랙리스트에 있으면 → 401 "로그아웃된 토큰입니다."(전 서비스 차단)
        └ 없으면 → X-User-Id/X-User-Roles 주입 후 통과
```

- `LoginService.logout` 은 **이미** jti 블랙리스트 + refresh 제거 + 동시세션 해제 + 감사이벤트를 수행한다.
- 이번에 추가된 것은 **게이트웨이 쪽 조회**뿐: `GatewayTokenVerifier` 가 `jti` 를 추출하고,
  `GatewayTokenBlacklist`(reactive Redis)가 `bl:{jti}` 존재 여부를 본다.
- 키 규약 `bl:{jti}` 는 `framework-security` 의 `RedisTokenStore` 와 **동일**해야 한다(둘 중 하나만 바꾸면 조용히 깨짐).

---

## 켜는 법

전제: 엣지 인증(`gateway.auth.enabled=true`)이 이미 켜져 있고, **로그인 서비스와 게이트웨이가 같은 Redis** 를
바라봐야 한다(공유 저장소). `framework.security.token.store=redis` 권장.

```yaml
# 게이트웨이 (services/gateway/application.yml — 이미 반영)
gateway:
  auth:
    enabled: ${GATEWAY_AUTH_ENABLED:true}
    jwt-secret: ${JWT_SECRET:}                              # = framework.security.jwt.secret
    blacklist-check:
      enabled: ${GATEWAY_BLACKLIST_CHECK_ENABLED:true}      # ← 중앙 로그아웃 ON
spring:
  data:
    redis:
      host: ${REDIS_HOST:redis}                             # 로그인 서비스와 동일 Redis

# 로그인 서비스 (framework-security)
framework:
  security:
    token:
      store: redis                                          # 공유 블랙리스트 저장소
```

```bash
export JWT_SECRET="32바이트-이상-운영-비밀키"   # 게이트웨이/서비스 동일
export GATEWAY_AUTH_ENABLED=true
export GATEWAY_BLACKLIST_CHECK_ENABLED=true
export REDIS_HOST=redis                          # K8s redis 서비스명
```

기본 `blacklist-check.enabled=false` 라 켜기 전에는 서명/만료만 검증한다(현행 동작 그대로). `true` 인데 reactive
Redis 가 없으면 기동 시 명확히 실패한다(fail-fast).

---

## 설계 메모 / 함정

- **게이트웨이는 WebFlux** — 블로킹 IO 금지. 블랙리스트 조회는 `ReactiveStringRedisTemplate.hasKey` (논블로킹)
  로만 한다. JDBC/memory TokenStore 는 블로킹이라 엣지 핫패스에 부적합 → 게이트웨이 블랙리스트는 **Redis 전용**.
  (서비스 측 `TokenStore` 는 여전히 memory|jdbc|redis 선택형. 다만 SSO 는 공유 저장소가 필요하므로 redis.)
- **키 prefix 동기화**: `RedisGatewayTokenBlacklist.BLACKLIST_PREFIX` == `RedisTokenStore.BL` == `"bl:"`.
- **jti 없는 토큰**: 개별 무효화 대상이 아니므로 통과(서명/만료 검증은 그대로). `JwtProvider` 는 항상 jti 를 부여.
- **심층 방어 유지**: 게이트웨이는 `Authorization` 헤더를 제거하지 않는다. 서비스가 토큰을 재검증할 수 있다.
  (다만 서비스 측 재검증은 블랙리스트를 보지 않을 수 있으니, 무효화의 1차 책임은 게이트웨이.)
- **성능**: 매 요청 Redis `hasKey` 1회(O(1)). 부담되면 짧은 TTL 의 게이트웨이 로컬 캐시(예: Caffeine)로 확장
  가능 — 단, 캐시 TTL 만큼 로그아웃 반영이 지연되므로 트레이드오프.

---

## 후속(선택)

- **전 세션 로그아웃("모든 기기에서 로그아웃")**: 현재는 제시된 access 토큰 1개의 jti 만 무효화. 동시세션 레지스트리
  (`applyConcurrentSessionLimit` 가 user→sessions(refresh+jti) 추적)를 순회해 사용자의 모든 jti 를 블랙리스트하면
  "전 기기 로그아웃" 이 된다.
- **SSO 로그인 리다이렉트 흐름**: 미인증 브라우저 요청을 로그인 서비스로 보내고 로그인 후 원위치 복귀(`continue` 파라미터).
- 다음 SSO 단계: (B) 표준 프로토콜 — OIDC 강화 + SAML SP, (C) Authorization Server(별도 서비스).
