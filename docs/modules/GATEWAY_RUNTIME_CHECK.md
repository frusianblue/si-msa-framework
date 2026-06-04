# 게이트웨이 런타임 점검 (Gateway Runtime Check)

> 작성 2026-06-04. 라우팅·CORS·레이트리밋·서킷브레이커·프로브가 **실제 기동 시** 의도대로 도는지 점검하고,
> 점검에서 드러난 결함을 보강한 기록. 설정 의미는 `docs/modules/GATEWAY_EDGE_AUTH.md`, 사용법은
> `services/gateway/README.md` 참고.

---

## 1. 점검에서 보강한 것 (Findings → Fixes)

### (a) 서킷브레이커 폴백이 404 로 샜다 — `FallbackController` 신설
`application.yml` 의 user-service 라우트는 회로 개방 시 `fallbackUri: forward:/fallback/user` 로
내부 포워딩하고 `/fallback/**` 는 엣지 인증 permit-all 에 포함돼 있다. 그런데 **`/fallback/user` 를 받는
핸들러가 없어** 다운스트림 장애 시 graceful 503 이 아니라 **404**(없는 경로)가 클라이언트로 나갔다.

→ `web/FallbackController` 신설. `/fallback/{service}`(+`/fallback`)를 모든 메서드로 받아
`{"success":false,"code":"E0503","message":"<service> 서비스가 일시적으로 응답하지 않습니다...","timestamp":...}`
형식의 **503** 으로 응답한다(401 응답과 동일한 ApiResponse.fail 형식 — 게이트웨이는 framework-core 미의존이라
손수 고정 JSON). 서비스명은 영숫자/`-_` 로 정화해 JSON 주입을 막는다.

### (b) 레이트리밋 키의 선행 콤마 XFF 엣지 — `RateLimitConfiguration` 보강
`principalKeyResolver` 는 익명 요청을 XFF 첫 홉 → remote addr 순으로 강등한다. 기존 코드는
`int comma = xff.indexOf(','); first = (comma > 0 ? xff.substring(0, comma) : xff).trim();` 였는데,
**선행 콤마**로 시작하는 기형/위조 XFF(`", 70.41.3.18"`)는 `comma == 0` 이라 `comma > 0` 가드가 실패 →
**원문 통째**가 키로 새서 `ip:, 70.41.3.18` 같은 잘못된 버킷 키가 됐다.

→ `comma >= 0` 으로 바꿔, 선행 콤마면 첫 토큰이 빈 문자열이 되어 **remote addr 로 폴백**한다. 정상 케이스
(단일 홉·다중 홉·콤마 없음·공백)는 전부 동일 동작. (JDK 단독 하네스 12+8 케이스로 검증.)

### (c) 오해 소지 주석 정정
`default-filters` 주석이 "X-Trace-Id 를 붙인다"였으나 실제 필터는 `X-Gateway: si-msa-gateway` 만 붙인다.
분산 추적 헤더 전파는 `micrometer-tracing` 이 자동 처리한다 → 주석을 실제 동작에 맞게 정정.

---

## 2. 런타임 거동 요약 (필터 순서)

```
요청 ──> [GatewayAuthGlobalFilter(order=-100, 옵트인)]   # 스푸핑 헤더 제거→화이트리스트→JWT 검증→신뢰헤더 주입
      ──> [CORS]                                         # globalcors. preflight(OPTIONS)는 여기서 단락(다운스트림 X)
      ──> [RequestRateLimiter]                           # principalKeyResolver 키로 토큰버킷(Redis), 초과 429
      ──> [CircuitBreaker(userCb)]                        # 개방 시 forward:/fallback/user → FallbackController 503
      ──> [LoadBalancer lb://user-service]                # K8s 서비스 DNS/디스커버리
```

- **CORS**: 진입점(게이트웨이) 한 곳에서만 처리. 다운스트림 중복 설정 금지. `allowCredentials=true` 라
  `allowedOrigins:"*"` 불가 → `allowedOriginPatterns: https://*.yourdomain.com`. preflight 는
  `add-to-simple-url-handler-mapping: true` 덕에 라우트 predicate 에 안 걸리는 경로도 처리된다.
- **레이트리밋 키 우선순위**: 검증 userId(`u:`) → Principal(`u:`) → XFF 첫 홉(`ip:`) → remote addr(`ip:`)
  → `ip:unknown`. 키는 절대 비지 않아 `deny-empty-key`(기본 true)에 걸리지 않는다.
- **프로브**: `management.endpoint.health.probes.enabled=true` → `/actuator/health/{liveness,readiness}`.

---

## 3. 검증 체크리스트

### 자동 테스트 (받는 쪽 gradle)
```bash
./gradlew :services:gateway:test --tests "*PrincipalKeyResolverTest"   # 키 산출 우선순위/XFF/폴백 (Redis 불요)
./gradlew :services:gateway:test --tests "*FallbackControllerTest"     # 서킷브레이커 503 (컨텍스트 불요)
./gradlew :services:gateway:test --tests "*GatewayCorsPreflightTest"   # CORS preflight (게이트웨이 기동, Redis 불요)
./gradlew :services:gateway:test                                       # 전체
```

### 수동 (로컬 기동 — `README.md §4~5`)
- 헬스: `curl -s localhost:8000/actuator/health` → `{"status":"UP",...}`
- CORS preflight: `OPTIONS /api/v1/users/me` + 허용/미허용 Origin (README §5).
- 레이트리밋 429: 같은 키로 `burstCapacity` 초과 연타 (Redis 필요, README §5).
- 폴백 503: user-service 를 내려/지연시켜 회로 개방 → `/api/v1/users/**` 가 503 JSON.

---

## 4. 알아둘 점 (Gotchas)

- **`lb://` 는 로컬 단독 실행에서 해석 안 됨** — K8s 배포가 정석. 로컬은 라우트 uri override(README §4).
- **레이트리밋 429 실측엔 reactive Redis 필요** — 키 산출 로직은 단위 테스트로, 토큰버킷 자체는 Spring Cloud
  Gateway 상위 구현 책임. 통합 429 자동화가 필요하면 Testcontainers Redis 를 별도 도입(미도입).
- **CORS preflight 는 인증 off 여도 동작** — preflight 는 라우팅/레이트리밋/인증보다 앞에서 단락된다.
- **폴백 서비스명 정화** — `/fallback/{service}` 의 service 는 영숫자/`-_` 외 제거. 외부에서 직접 호출 가능
  (permit-all)하므로 JSON 본문 주입 방지가 목적.
