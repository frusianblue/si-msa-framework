# 토큰 검증 아키텍처 가이드 (Token Verification Architecture Guide)

> 이 프레임워크에서 **요청이 들어왔을 때 "이 사람이 누구인지"를 어디서·어떻게·무엇을 근거로 믿을지**를
> 결정하는 방법을 정리한 문서. 실제 배치(운영) 전에 검증 방식을 고르는 의사결정 가이드 + 업계 표준 카탈로그 +
> 현재 설계 평가 + 확장 방법을 담는다.
>
> 관련: 구현/켜는 법은 [`modules/GATEWAY_EDGE_AUTH.md`](./modules/GATEWAY_EDGE_AUTH.md),
> 발급자(OP) 측은 [`modules/AUTH_SERVER.md`](./modules/AUTH_SERVER.md), 중앙 로그아웃은
> [`modules/SSO_CENTRAL_LOGOUT.md`](./modules/SSO_CENTRAL_LOGOUT.md).

---

## 0. 5초 요약

- 현재 우리 기본 구성은 업계에서 **가장 흔하고 합리적인 출발점**이다: *엣지(게이트웨이)에서 JWT를 로컬 검증하고, 통과한 요청에만 신뢰 헤더를 박아 다운스트림으로 넘긴다.*
- 이 방식의 안전성은 **"게이트웨이를 우회해 다운스트림에 직접 닿을 수 없다"는 네트워크 가정**에 의존한다. 그 가정을 K8s NetworkPolicy(또는 service mesh mTLS)로 **반드시 보강**해야 설계가 완성된다.
- 더 강한 보장이 필요하면 (고규제·외부 개방·멀티 클러스터) **무신뢰(zero-trust) 재검증**이나 **introspection**, **mTLS 토큰 바인딩**으로 단계적으로 올린다. 전부 한 번에 갈 필요는 없다.

---

## 1. 먼저 — "토큰 검증"이 정확히 무엇을 보장하나

토큰 하나가 들어오면, 검증기는 사실 **다섯 개의 독립된 질문**에 답한다. 어떤 방식을 고르든 결국 이 다섯 개 중 무엇을, 얼마나 강하게 답하느냐의 차이다.

| # | 질문 | 무엇으로 답하나 | stateless JWT에서 |
|---|------|----------------|-------------------|
| ① | **위조 아님?** (무결성) | 서명 검증(HMAC 대칭키 / RSA·EC 공개키) | 쉽다(키만 있으면) |
| ② | **만료 안 됨?** | `exp`/`nbf` + clock skew | 쉽다 |
| ③ | **누가 발급?** (issuer) | `iss` 클레임 == 신뢰하는 발급자 | 쉽다 |
| ④ | **나한테 온 게 맞나?** (audience) | `aud` 클레임 == 내 서비스/리소스 식별자 | 쉽다(단 **현재 미검증** — §5) |
| ⑤ | **아직 살아있나?** (폐기 여부) | 블랙리스트 / introspection / 짧은 TTL | **어렵다** ← 핵심 함정 |

①~④는 토큰만 보면 네트워크 없이(stateless) 즉시 판정된다. JWT가 빠르고 확장 잘 되는 이유다.
**⑤가 stateless JWT의 아킬레스건**이다 — "로그아웃했는데 토큰이 아직 안 만료됨" 문제. 이걸 어떻게 메우느냐가 검증 설계의 절반을 차지한다.

> **authn ≠ authz.** 위 다섯 질문은 전부 "누구인가(authentication)"에 관한 것이다. "무엇을 할 수 있나(authorization)"는 그 다음 단계 — 우리 구성에서는 검증 후 주입한 `X-User-Roles` 와 `framework-security` 의 RBAC(`@PreAuthorize`/`DynamicAuthorizationManager`)가 담당한다. 이 문서는 ①~⑤(authn)에 집중한다.

---

## 2. 현재 구성은 어떻게 흐르는가 (as-is)

### 2.1 토큰이 두 종류다 (이중 발급기)

| 구분 | 자체 JWT (INTERNAL) | AS 토큰 (AUTHORIZATION_SERVER) |
|---|---|---|
| 발급자 | `framework-security` `JwtProvider` | `services/auth-server` (OP) |
| 서명 | **HMAC**(대칭키, `JWT_SECRET` 공유) | **RS256**(비대칭, JWKS 공개키) |
| `iss` | 없음 | AS issuer(설정값) |
| 용도 | 내부 1차 로그인/세션 | 외부·그룹사 위임 발급 |
| 폐기 | jti 블랙리스트(중앙 로그아웃) | AS `/oauth2/revoke` |

### 2.2 흐름도

```
                         ┌──────────────────────── 게이트웨이 (WebFlux, GlobalFilter order=-100) ────────────────────────┐
[클라이언트] --Bearer--> │ (1) 클라가 보낸 X-User-* 헤더 제거(스푸핑 차단)                                              │
                         │ (2) 화이트리스트(/api/*/auth/**, /actuator/**, /fallback/**)면 토큰 없이 통과               │
                         │ (3) iss 엿보기로 분기:                                                                       │
                         │        ├─ AS issuer면 → RS256/JWKS 로컬 검증 (GatewayJwksTokenVerifier, boundedElastic)      │
                         │        └─ 그 외     → HMAC 로컬 검증 (GatewayTokenVerifier, 이벤트 루프)                     │
                         │ (4) 폐기 검사: INTERNAL만 jti 블랙리스트(reactive Redis). AS는 건너뜀(§4 경계)               │
                         │ (5) 통과 → sub→X-User-Id, roles→X-User-Roles 주입 (Authorization 헤더는 유지)               │
                         └──────────────────────────────────────────────┬──────────────────────────────────────────────┘
                                                                         ▼
                  ┌──────────────── user-service (servlet) ────────────────┐   ┌──── admin-service ... ────┐
                  │ framework-security: Bearer를 **독립 HMAC 재검증**       │   │ framework-context만:       │
                  │   (게이트웨이 헤더를 authn 근거로 신뢰하지 않음)        │   │   X-User-Id → RequestContext│
                  │ framework-context: X-User-Id → RequestContext 바인딩    │   │   (헤더 신뢰)               │
                  └─────────────────────────────────────────────────────────┘   └────────────────────────────┘
```

### 2.3 지금 신뢰 경계가 어디 그어져 있나 (중요)

- **외부(인터넷) → 게이트웨이**: 게이트웨이가 토큰을 검증하고, 클라이언트가 직접 보낸 `X-User-*` 헤더는 **무조건 제거**한다. → 외부에서 헤더로 신분 위조 불가. ✅
- **게이트웨이 → 다운스트림**: 여기가 핵심. 서비스마다 다르다.
  - `user-service` 는 `framework-security` 가 `Authorization` Bearer 를 **다시 HMAC 검증**한다(헤더만 믿지 않음 = 부분적 zero-trust). 단 **자체 JWT만** 재검증할 수 있고, AS 토큰(RS256)은 user-service 가 검증기를 안 가지고 있어 게이트웨이 검증에 의존한다.
  - `framework-context` 만 켠 서비스는 게이트웨이가 박은 `X-User-Id` 를 **그대로 신뢰**한다.
- **그래서 "게이트웨이를 거치지 않은 내부 요청"이 다운스트림에 직접 닿으면?** `framework-context`-only 서비스는 헤더를 그대로 믿어버린다. → **이 갭을 네트워크에서 막아야 한다**(NetworkPolicy/mTLS). 코드만으로는 못 막는다. (§5에서 다시)

---

## 3. 검증 방식의 선택지 — 업계 표준 카탈로그

검증 설계를 한 덩어리로 보면 헷갈린다. **서로 독립된 세 개의 축**으로 분해하면 명확해진다. 실제 시스템은 (축A 하나) × (축B 하나) × (축C 하나)의 조합이다.

### 축 A — 토큰을 **"어디서"** 검증하나 (검증 위치)

| 옵션 | 설명 | 장점 | 단점 | 쓰는 곳 |
|------|------|------|------|---------|
| **A1. 엣지 전용** (gateway-only) + 내부망 신뢰 | 게이트웨이만 검증, 다운스트림은 헤더 신뢰 | 단순·빠름, 다운스트림 부담 0 | 내부망 침투 시 무방비(측면 이동) → 네트워크 보강 필수 | **현재 기본**, 대부분의 사내 MSA |
| **A2. 다층 방어** (defense-in-depth) | 게이트웨이 + 각 서비스도 재검증 | 게이트웨이 우회·내부 위협에도 견고 | 매 홉 검증 비용·키 배포 | 금융·고규제, 멀티팀 대형 |
| **A3. 서비스별 검증** (게이트웨이 없음) | 각 서비스가 알아서 검증 | 게이트웨이 단일 장애점 없음 | 정책 중복·일관성 깨짐 | 게이트웨이 없는 소규모 |

> 우리는 **A1을 기본**으로 하되, `user-service` 가 자체 JWT를 재검증해 **부분적으로 A2**를 이미 하고 있다.

### 축 B — 토큰을 **"어떻게"** 검증하나 (검증 메커니즘)

| 옵션 | 설명 | 폐기 즉시성 | 비용/지연 | 토큰 형태 |
|------|------|-----------|-----------|-----------|
| **B1. 로컬 JWT 검증** (JWKS/HMAC) | 서명·클레임을 자체 판정, 네트워크 없음 | ❌ 만료까지 유효(TTL) | 매우 낮음(캐시된 키) | JWT(자기기술) |
| **B2. Introspection** (RFC 7662) | 매 요청 AS에 "이 토큰 살아있나?" 질의 | ✅ 즉시 | 높음(AS 왕복) → 캐시 필요 | opaque 가능 |
| **B3. 하이브리드** | B1 + 짧은 TTL + 블랙리스트(폐기분만 공유) | 🔶 준실시간 | 낮음(블랙리스트만 조회) | JWT |

핵심 트레이드오프는 **"폐기를 얼마나 빨리 반영하느냐 vs 매 요청 비용"**이다.
- **B1**: 가장 빠르고 확장 잘 됨. 그러나 탈취·로그아웃된 토큰이 TTL 동안 살아있음.
- **B2**: 폐기 즉시 반영, opaque 토큰 가능(토큰에 정보 노출 0). 그러나 AS가 매 요청 경로에 들어와 부하·지연·SPOF. → 반드시 짧은 캐시.
- **B3**: 둘의 절충. **우리 자체 JWT가 이미 B3다** — JWT 로컬 검증 + 로그아웃 시 jti만 Redis 블랙리스트에 올려 게이트웨이가 조회.

> 우리 현재: 자체 JWT = **B3**(JWT + jti 블랙리스트), AS 토큰 = **B1**(JWKS, 블랙리스트 없음 — 폐기는 AS revoke 호출 시점의 TTL 의존).

### 축 C — 서비스 간 신뢰를 **"무엇으로"** 보장하나 (전송/워크로드 신원)

A1(엣지 전용)을 택하는 순간 "게이트웨이를 거친 요청만 신뢰"를 **무언가로** 보장해야 한다. 이게 축 C다.

| 옵션 | 설명 | 강도 | 운영 복잡도 |
|------|------|------|------------|
| **C1. NetworkPolicy** (K8s) | 다운스트림 인그레스를 게이트웨이 파드로만 제한 | 중 (L3/L4) | 낮음 — **최소 필수** |
| **C2. mTLS / Service Mesh** (Istio·Linkerd) | 서비스 간 양방향 TLS, 사이드카가 강제 | 강 (L7 워크로드 인증) | 중~높음(메시 도입) |
| **C3. SPIFFE/SPIRE** | 워크로드에 단기 X.509/JWT-SVID 신원 자동 발급 | 강 (플랫폼 독립 신원) | 높음 |
| **C4. 상수 헤더** (`X-Gateway: true` 등) | 게이트웨이가 비밀 헤더 추가 | **거의 없음(안티패턴)** | 낮음 |

> ⚠️ **C4는 신뢰 근거로 쓰지 말 것.** 헤더는 누구나 위조 가능하다. 우리 게이트웨이가 `AddRequestHeader` 상수를 붙이더라도 그건 신뢰 근거가 아니다. **C1을 최소선으로, 보안 요구가 높으면 C2**로 올린다.

### (참고) 그 외 토큰 강화 기법

- **Sender-constrained tokens** — 토큰을 특정 클라이언트에 묶어 탈취해도 못 쓰게 함. **mTLS 바인딩(RFC 8705)**, **DPoP(RFC 9449)**. 외부 파트너 개방 시 고려.
- **BFF(Backend-for-Frontend) / 토큰 핸들러 패턴** — 브라우저(SPA)에는 토큰을 절대 안 주고 `HttpOnly` 쿠키만, 실제 토큰은 BFF가 보관·부착. XSS 토큰 탈취 차단. 공개 웹 프런트가 있으면 강력 권장.
- **단명 access + refresh 회전** — access TTL을 짧게(예 5~15분) 해 B1의 폐기 갭을 구조적으로 줄임. refresh는 회전(rotation)+재사용 탐지.

---

## 4. 어떻게 고를까 — 의사결정 가이드

### 4.1 질문 순서 (위에서부터)

1. **공개 인터넷에 직접 노출되는 브라우저 프런트가 있나?**
   → 예: **BFF/토큰 핸들러 + `HttpOnly` 쿠키**를 먼저 깔아라(토큰을 JS에 노출하지 마라).
2. **다운스트림이 게이트웨이 외 경로로 닿을 수 있나?** (다른 네임스페이스, 잡(Job), 디버그 포트, 멀티 클러스터…)
   → 예: 최소 **C1(NetworkPolicy)**, 닿을 수 있는 표면이 넓으면 **A2(재검증)** 또는 **C2(mTLS)**.
3. **로그아웃/폐기를 몇 초 안에 강제해야 하나?** (규제·보안 사고 대응)
   → 즉시 필요: **B2(introspection)** 또는 **B3 + 짧은 TTL**. 분 단위 허용: **B1 + 짧은 TTL**로 충분.
4. **누가 토큰을 발급하나? 외부에 공개하나?**
   → 외부 파트너에 발급: **sender-constrained(mTLS/DPoP)** + `aud` 엄격 검증 + introspection 고려.
5. **고규제 도메인인가?** (금융·의료·공공 민감)
   → A2(다층) + B3/B2 + C2(mTLS) 조합으로 올린다.

### 4.2 시나리오별 권장 조합

| 시나리오 | 축 A | 축 B | 축 C | 비고 |
|----------|------|------|------|------|
| **일반 사내 MSA** (대부분) | A1 | B1/B3 | **C1** | **현재 구성 + NetworkPolicy면 충분** |
| **공공/내부망 분리** | A1(+민감 서비스만 A2) | B3 | C1(+가능하면 C2) | 망분리 환경은 C1로도 강함 |
| **금융·고규제** | **A2** | **B3/B2** | **C2(mTLS)** | 다층 + 폐기 즉시성 + 워크로드 신원 |
| **외부 파트너 개방** | A2 | B1+`aud`엄격, 부분 B2 | C2 | **mTLS/DPoP 토큰 바인딩** 강력 권장 |
| **공개 SPA 프런트** | A1 | B3 | C1 | **BFF/쿠키** 선행 |
| **멀티 클러스터/멀티 클라우드** | A2 | B1/B2 | **C3(SPIFFE)** | 플랫폼 독립 신원 |

### 4.3 "지금 기본값으로 충분 / 강화 필요" 신호

- ✅ **충분 신호**: 단일 클러스터·사내 트래픽 위주·NetworkPolicy 적용·로그아웃 분 단위 허용 → 현재 A1+B3/B1+C1 유지.
- 🔶 **강화 신호**: 다운스트림이 여러 네임스페이스에 노출 / 외부에 토큰 발급 / 초 단위 폐기 요구 / 감사에서 "내부 측면 이동" 지적 / 멀티 클러스터 → 위 시나리오 표대로 단계 상향.

---

## 5. 현재 설계 평가 — 잘 된 건가?

**한 줄 평: 출발점으로 매우 표준적이고 좋다(A1 + B1/B3 + 권장 C1).** 다만 운영 배치 전에 보강해야 할 항목이 명확히 있다.

### 5.1 잘 한 점

- **엣지 1차 검증 + 헤더 주입**은 Spring Cloud Gateway 류에서 가장 널리 쓰는 정석 패턴이다. 다운스트림이 인증 부담을 덜고 일관된 정책을 한곳에서 건다.
- **클라이언트 위조 헤더 무조건 제거** — 외부 스푸핑 차단의 핵심을 정확히 구현.
- **이중 발급기를 `iss` 라우팅으로 깔끔히 분리**하고, AS 토큰은 jti 블랙리스트와 혼용하지 않는 **폐기 경계(§4)를 코드·문서로 못 박음**. 이중 발급기에서 가장 흔한 혼란을 선제적으로 막았다.
- **검증 출력을 공통 타입(`Verified`)으로 통일** → 어느 경로로 검증하든 다운스트림 헤더 주입 코드가 하나. 확장에 유리(§6).
- **WebFlux 이벤트 루프 보호** — 블로킹 JWKS 조회를 AS 토큰일 때만 `boundedElastic` 으로 오프로드. 성능 함정을 피함.
- **옵트인·기본 off** — 점진 도입이 가능하고, 켜기 전 동작이 100% 동일.
- `user-service` 의 **자체 JWT 재검증**은 이미 부분적 A2(다층 방어)다.

### 5.2 솔직한 약점 / 배치 전 점검 항목

1. **내부망 신뢰 가정이 코드 밖에 있다 (가장 중요).** A1의 안전성은 전적으로 "게이트웨이 우회 불가"에 달렸는데, 이건 코드가 아니라 **NetworkPolicy(C1)** 가 보장해야 한다. **미설정 시, 클러스터 내 다른 파드가 `framework-context`-only 서비스에 `X-User-Id` 헤더를 직접 박아 신분 위조가 가능하다.** → **배치 전 NetworkPolicy 필수**, 민감 서비스는 mTLS 고려.
2. **`aud`(audience) 미검증.** 현재 AS 토큰 검증은 `iss`/서명/만료/`sub`만 본다. `aud` 를 안 보면, **다른 리소스용으로 발급된 토큰이 우리 게이트웨이에 재사용(token confusion)** 될 수 있다. RFC 9068(JWT Access Token Profile)은 `aud` 검증을 권장한다. → **`aud` 검증 추가 권장**(§6.1, 작은 변경).
3. **AS 토큰 폐기 즉시성.** AS 토큰은 블랙리스트가 없어 revoke 후에도 TTL 동안 유효하다. 외부 발급이면 **access TTL을 짧게**(분 단위) 하거나, 민감하면 **introspection(B2)** 을 부분 도입.
4. **HMAC 대칭키 전면 공유.** 자체 JWT는 게이트웨이와 모든 서비스가 **같은 `JWT_SECRET`** 으로 검증한다. 즉 **어느 한 서비스에서 키가 유출되면 누구나 유효 토큰을 위조**할 수 있다(검증 권한 = 발급 권한). → 보안 등급이 올라가면 **자체 JWT도 비대칭(RS256/JWKS)으로 전환**해 "발급은 한 곳, 검증은 공개키로"를 분리하는 게 정석(§6.3). AS 토큰은 이미 비대칭이라 이 문제에서 자유롭다.
5. **다운스트림 AS 토큰 미검증.** `user-service` 는 자체 JWT만 재검증 가능하고 AS 토큰(RS256)은 게이트웨이에만 의존한다. 완전한 A2를 원하면 다운스트림에도 동일한 이중 issuer 검증이 필요(§6.4, framework-security 변경 수반 → 별도 드롭).

### 5.3 표준 충족 체크리스트

| 항목 | 표준 권장 | 현재 | 조치 |
|------|----------|------|------|
| 서명 검증 | 필수 | ✅ | — |
| `exp`/`nbf` + skew | 필수 | ✅ | — |
| `iss` 검증 | 필수 | ✅ | — |
| `aud` 검증 | 권장(RFC 9068) | ❌ | §6.1 추가 |
| 폐기 메커니즘 | 권장 | 🔶(자체만) | AS는 TTL/§6.2 |
| 비대칭 서명(검증/발급 분리) | 권장 | 🔶(AS만) | §6.3 고려 |
| 전송 신뢰(NetworkPolicy/mTLS) | 필수(A1 전제) | ⚠️ 코드 밖 | **배치 전 C1 필수** |
| 위조 헤더 제거 | 필수 | ✅ | — |
| HTTPS 전구간 | 필수 | (인프라) | TLS 종단 확인 |

---

## 6. 어떻게 확장하나 — 검증 방식을 유연하게 바꾸기

좋은 소식: **현재 코드가 이미 확장에 맞는 모양이다.** `GatewayTokenAuthenticator` 가 전략 라우터이고, `Verified` 가 공통 출력 계약이다. 새 검증 방식은 "새 검증기를 만들어 라우터에 끼우는" 형태로 추가된다.

### 6.0 확장점의 구조

```
요청 토큰
   │
   ▼
GatewayTokenAuthenticator   ← (라우터: kindOf로 어떤 검증기를 쓸지 결정)
   ├─ GatewayTokenVerifier        (HMAC)        ─┐
   ├─ GatewayJwksTokenVerifier    (RS256/JWKS)  ─┤→ 전부 같은 GatewayTokenVerifier.Verified 반환
   └─ (추가) GatewayIntrospectionVerifier ...   ─┘
   │
   ▼
Verified(userId, jti, roles)  ← 공통 계약. 다운스트림 헤더 주입은 이거 하나만 안다.
```

전략을 늘리려면 **공통 인터페이스를 추출**하는 게 첫 단계다.

```java
// 권장: 검증기 공통 계약을 인터페이스로 승격
public interface EdgeTokenVerifier {
    GatewayTokenVerifier.Verified verify(String token);   // 실패 시 RuntimeException(JwtException 등)
    boolean supports(TokenIssuerKind kind);               // 또는 라우터가 kind→verifier 맵을 보유
}
```

`GatewayTokenAuthenticator` 는 `kindOf(token)` 으로 `kind` 를 정하고 해당 `EdgeTokenVerifier` 에 위임 — 지금 구조 그대로다. 아래 확장들은 전부 이 틀 안에서 더해진다.

### 6.1 (작게) `aud` 검증 추가 — 권장

`GatewayJwksTokenVerifier.verify` 에 `iss` 검증 옆으로 `aud` 검사를 추가한다.

```java
// expectedAudience 설정값을 생성자로 주입(예: gateway.auth.authorization-server.audience)
List<String> aud = claims.getAudience() == null ? List.of() : List.copyOf(claims.getAudience());
if (expectedAudience != null && !aud.contains(expectedAudience)) {
    throw new JwtException("AS 토큰 audience 불일치(기대=" + expectedAudience + ").");
}
```

`AuthorizationServer` properties에 `audience` 필드를 더하고, 비어 있으면(미설정) 기존처럼 건너뛰면 하위호환이 유지된다.

### 6.2 (중간) Introspection 검증기 추가 — 폐기 즉시성이 필요할 때 (B2)

opaque 토큰이나 즉시 폐기가 필요하면, JWKS 대신(또는 함께) AS introspection(RFC 7662)을 호출하는 검증기를 새로 만든다.

```java
public class GatewayIntrospectionVerifier {           // 새 검증기, 라우터에 등록
    // POST {introspectionUri}  body: token=...   (client auth 필요)
    // 응답 {"active":true,"sub":"...","aud":"...","roles":[...]} 파싱 → Verified
    // ★ 매 요청 AS 왕복이므로 짧은 TTL 캐시(예: Caffeine 30~60s) 필수
    //   reactive 게이트웨이이므로 호출은 boundedElastic 오프로드(JWKS와 동일 패턴)
}
```

도입 시 주의: introspection은 AS를 요청 경로에 넣으므로 **반드시 단기 캐시 + AS 가용성(타임아웃·서킷브레이커)** 을 함께 본다. 우리는 `framework-client` 의 resilience(재시도/서킷) 패턴을 재사용할 수 있다. 라우팅은 "특정 issuer/토큰 형태 → introspection 검증기"로 `kindOf` 를 확장한다.

### 6.3 (중간) 자체 JWT를 HMAC → RS256/JWKS로 전환 — 대칭키 공유 제거

검증 권한과 발급 권한을 분리하려면 `framework-security` `JwtProvider` 를 RSA 서명으로 바꾸고, 검증 측(게이트웨이·각 서비스)은 **이미 만들어 둔 `GatewayJwksTokenVerifier` / `JwksKeyResolver` 패턴을 그대로 재사용**한다. 이때 자체 JWT에도 `iss` 를 부여하면 라우팅이 더 명확해진다(현재는 `iss` 부재로 INTERNAL 분기). 점진 전환은 "검증은 RS256·HMAC 둘 다 한동안 수용 → 발급을 RS256로 전환 → HMAC 검증 제거" 순으로.

### 6.4 (큰 작업) 다운스트림 zero-trust 재검증 — 완전한 A2

`framework-context`-only 서비스도 `Authorization` Bearer 를 직접 이중 issuer 재검증하게 하려면, 게이트웨이에 만든 라우터/JWKS 검증 로직을 **servlet 버전으로** `framework-security` 에 이식해야 한다(WebFlux ↔ servlet 차이만 흡수, 로직은 동일). 이건 라이브러리 변경을 수반하므로 **독립 드롭**으로 다루는 게 맞다. 전환 후엔 NetworkPolicy(C1)는 여전히 심층 방어로 유지한다(둘은 배타가 아니라 겹겹이).

### 6.5 런타임 전환을 위한 설정 골격

검증 방식을 환경마다 토글로 바꾸려면 properties를 전략 선택형으로 둔다(기존 토글 컨벤션 유지, 기본은 현행과 동일).

```yaml
gateway:
  auth:
    enabled: true
    authorization-server:
      enabled: ${GATEWAY_AS_ENABLED:false}
      issuer: ${AUTH_SERVER_ISSUER:}
      audience: ${AUTH_SERVER_AUDIENCE:}          # §6.1 (비면 검증 생략 — 하위호환)
      validation: jwks                            # jwks | introspection | jwks-then-introspect (§6.2)
      introspection-uri: ${AS_INTROSPECTION_URI:} # validation에 introspection 포함 시
      introspection-cache-ttl: 30s
```

`GatewayAuthConfiguration` 이 `validation` 값에 따라 어떤 `EdgeTokenVerifier` 빈을 묶을지 고르면, **코드 재배포 없이 환경 설정만으로 검증 메커니즘을 바꿀 수 있다.** 기본값(`jwks`)은 현재 동작과 동일하게 둔다.

---

## 7. 배치 환경별 신뢰 경계 (K8s vs VM) — 그리고 환경별 선택을 코드로 분기하기

엣지 전용 검증(A1)의 안전성은 "게이트웨이를 우회해 다운스트림에 직접 못 닿는다"에 달려 있다(§2.3, §5.2-①).
그런데 **그 "못 닿게 강제하는 수단"은 배치 환경마다 다르고, 대부분 애플리케이션 코드 밖(인프라)에 있다.**

### 7.1 신뢰 "강제" 수단은 환경마다 다르다

| 배치 환경 | 신뢰 경계 강제 수단 | 격리 단위 | 게이트웨이-only 보장 강도 |
|-----------|---------------------|-----------|---------------------------|
| **K8s** | **NetworkPolicy**(필수) → 강화 시 mesh mTLS | **워크로드(파드)** 단위 | 강함(정책이 정확하면) |
| **VM** | 보안그룹 / 호스트 방화벽 / iptables | **호스트** 단위(거침) | 중간~약함(평평한 망·동거 서비스면 약함) |
| **서버리스/PaaS** | 플랫폼 IAM·VPC·프라이빗 엔드포인트 | 함수/서비스 | 플랫폼 의존 |

핵심: **VM은 "이 워크로드만 통과"를 K8s NetworkPolicy만큼 깔끔히 못 그린다.** 한 호스트에 여러 서비스가 뜨거나
내부망이 평평하면, 네트워크만으로 게이트웨이 우회를 막기 어렵다. → **네트워크 격리가 약할수록 앱 계층 재검증으로 메운다.**

NetworkPolicy 예시(다운스트림에 "게이트웨이 파드 인그레스만" 허용):

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata: { name: user-service-allow-gateway-only }
spec:
  podSelector: { matchLabels: { app: user-service } }   # 보호 대상
  policyTypes: [Ingress]
  ingress:
    - from:
        - podSelector: { matchLabels: { app: gateway } } # 게이트웨이에서 온 것만
```

> ⚠️ **점검 3가지**: (1) `framework-context`-only 서비스에도 정책이 걸렸나(가장 절실) (2) `ingress.from` 셀렉터가
> 게이트웨이 파드 라벨을 **정확히** 집나(오타=조용히 allow-all) (3) 클러스터 **CNI가 NetworkPolicy 를 실제 강제**하나(Calico·Cilium=OK, 일부 flannel 구성=무시).

### 7.2 그래서 환경별 "검증 자세(posture)"가 갈린다

| 환경 | 네트워크가 게이트웨이-only 보장? | 권장 다운스트림 자세 |
|------|--------------------------------|---------------------|
| K8s + NetworkPolicy | 예 | **헤더 신뢰(A1)** — 저렴 |
| VM(격리 불확실) | 보장 약함 | **재검증(zero-trust/A2)** — 안전 |

**강제 수단(NetworkPolicy/방화벽)은 인프라라 못 바꾸지만, "다운스트림이 헤더를 믿을지 / Bearer 를 다시 검증할지"라는
자세는 앱 설정으로 분기할 수 있다.**

### 7.3 환경별 선택을 코드로 분기하기 — profile + 토글

`services/auth-server` 가 이미 쓰는 3-프로파일 yml 방식으로, **같은 산출물(jar/이미지)이 환경에 맞는 자세로** 뜨게 한다.

```yaml
# application.yml — 기본은 안전한 쪽(strict)
framework:
  edge-trust:
    mode: zero-trust          # zero-trust(기본) | gateway-headers(완화)
```
```yaml
# application-k8s.yml — NetworkPolicy 로 격리 보장됨 → 완화
framework: { edge-trust: { mode: gateway-headers } }
```
```yaml
# application-vm.yml — 격리 불확실 → 앱에서 재검증
framework: { edge-trust: { mode: zero-trust } }
```

`SPRING_PROFILES_ACTIVE=k8s` 또는 `vm` 로 활성. 자세별 동작:

| `edge-trust.mode` | 다운스트림 동작 | 신원(authn) 근거 |
|-------------------|-----------------|------------------|
| `gateway-headers` | 주입된 `X-User-*` 를 그대로 신뢰(현재 `HeaderContextResolver`) | 게이트웨이가 박은 헤더 |
| `zero-trust` | `Authorization` Bearer 를 **직접 재검증**(`framework-security` 체인) 후에만 바인딩; 검증 없는 `X-User-*` 는 authn 근거로 불사용 | 로컬 재검증 결과 |

지킬 원칙 둘:

1. **기본값은 엄격한 쪽(zero-trust).** 완화(`gateway-headers`)는 "네트워크 격리를 확인했다"는 **의도적 옵트인**이어야 한다. 토글을 잘못 켜면 곧 구멍이므로, 이 프레임워크의 "기본 disabled = 안전" 철학과 같은 결.
2. **솔직한 갭**: 현재 다운스트림(`user-service`)은 **자체 JWT(HMAC)만** 재검증 가능. VM에서 **AS 토큰(RS256)까지** zero-trust로 재검증하려면 게이트웨이의 이중 issuer 검증기를 servlet 버전으로 `framework-security` 에 이식해야 한다(§6.4, 별도 드롭). 자체 JWT만 쓰는 배치면 이미 커버.

### 7.4 지금 권장

- **K8s + NetworkPolicy 이미 적용** → K8s 환경의 A1 설계는 사실상 완성. 위 7.1 점검 3가지만 확인.
- **VM 배치가 로드맵에 있으면**: 7.3의 `edge-trust.mode` 토글을 지금 설계(기본 strict, k8s 프로파일만 완화). AS 토큰 VM zero-trust가 필요하면 §6.4를 함께.
- **VM 계획이 아직 없으면**: 원칙만 이 문서로 남기고 토글 구현은 보류(안 쓸 추상화를 미리 만들지 않는다). NetworkPolicy(C1) + `aud`(§6.1) + 짧은 TTL이 당장의 우선순위.

---

## 8. 표준 레퍼런스 (용어가 막힐 때)

| 표준 | 무엇 | 우리와의 관계 |
|------|------|---------------|
| **RFC 7519** | JWT | 자체 JWT·AS 토큰 모두 |
| **RFC 7515/7516/7517/7518** | JWS·JWE·JWK(S)·JWA | JWKS=공개키 묶음(7517), 서명 알고리즘(7518) |
| **RFC 9068** | JWT Profile for OAuth2 Access Tokens | `aud`/`iss`/`exp` 등 검증 권장(§5.2-②) |
| **RFC 7662** | Token Introspection | B2 검증기(§6.2) |
| **RFC 7009** | Token Revocation | AS `/oauth2/revoke` (AS 토큰 폐기) |
| **RFC 8414** | AS Metadata / **OIDC Discovery** | `{issuer}/.well-known/...` 자동 설정 |
| **RFC 7636** | PKCE | AS 인가 코드 흐름 보호 |
| **RFC 8705** | mTLS client auth & **certificate-bound tokens** | sender-constrained(§3 참고) |
| **RFC 9449** | DPoP | sender-constrained(브라우저 친화) |
| **SPIFFE/SPIRE** | 워크로드 신원 | 축 C3(멀티 클러스터) |

---

## 9. 결론 — 지금 무엇을 하면 되나

1. **배치 전 필수**: K8s **NetworkPolicy** 로 다운스트림 인그레스를 게이트웨이로 제한(C1, §7.1). 이게 A1 설계를 "완성"시킨다. *(이미 적용 중이면 §7.1 점검 3가지만 확인.)*
2. **권장(작음)**: AS 토큰 **`aud` 검증 추가**(§6.1) + access TTL을 짧게.
3. **VM 배치를 고려한다면**: §7.3의 `edge-trust.mode` 프로파일 토글로 환경별 자세를 분기(기본 strict). 안 쓸 거면 원칙만 남기고 보류.
4. **보안 등급이 오르면**: introspection(§6.2) 또는 mTLS(C2)로 단계 상향, 자체 JWT의 RS256 전환(§6.3) 고려.
5. **고규제·외부 개방이면**: 다운스트림 zero-trust(§6.4) + sender-constrained 토큰까지.

현재 구성은 **틀린 설계가 아니라, "기본을 잘 깐 출발점"** 이다. 위 1번(NetworkPolicy)만 갖추면 일반적인 사내 MSA로서는 충분하고, 나머지는 실제 위협 모델이 요구할 때 축별로 하나씩 올리면 된다.
