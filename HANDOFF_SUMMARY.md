# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**직전 = 그릇 정비 \"게이트웨이 런타임 점검\" 처리(2026-06-04).** 라우팅/CORS/레이트리밋/서킷브레이커/프로브가 실제 기동 시 의도대로 도는지 점검 → **결함 2건 보강 + 검증 공백 3건 메움**. ① **서킷브레이커 폴백 404→503**: `application.yml` 의 `fallbackUri: forward:/fallback/user` 를 받는 핸들러가 없어 회로 개방 시 404 가 샜음 → `web/FallbackController` 신설(`/fallback/{service}`+`/fallback`, 모든 메서드, ApiResponse.fail 형식 **503**, 서비스명 영숫자/`-_` 정화). ② **레이트리밋 선행 콤마 XFF 누출**: `principalKeyResolver.clientIp` 의 `comma > 0` 가드가 `", 1.2.3.4"` 같은 기형/위조 XFF 에서 원문 통째를 키로 누출(`ip:, 1.2.3.4`) → `comma >= 0` 으로 보강(remote 폴백, 정상 케이스 전부 동일). ③ 오해 소지 주석 정정(`default-filters`=`X-Gateway`, X-Trace-Id 오기; 트레이스는 micrometer-tracing). **신규 테스트 3종** — `PrincipalKeyResolverTest`(키 우선순위, Redis 불요)·`FallbackControllerTest`(503/정화, 컨텍스트 불요)·`GatewayCorsPreflightTest`(`@SpringBootTest` RANDOM_PORT+WebTestClient: 허용 origin echo/미허용 차단/비라우트 경로/methods/max-age). **순수 로직 JDK 단독 하네스 12+8 통과.** **새 외부 의존성 0.** **바로 다음 = 받는 쪽 `:services:gateway:test` 실행 확인 → commit/push.**

## 최종 갱신
- 일자: 2026-06-04 · 갱신자: 게이트웨이 런타임 점검 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / Jackson 3(tools.jackson.*) / Spring Cloud Gateway(WebFlux) · jjwt 0.12.6

## 직전에 한 것 (Done — 정적 리뷰 + 순수 로직 하네스 통과 / Spring 경로는 받는 쪽 대기)
- **① `web/FallbackController` 신설**: `@RestController`, `@RequestMapping("/fallback/{service}")`(+`/fallback`), 모든 HTTP 메서드(원 요청 메서드 보존 포워딩). 응답 = `{"success":false,"code":"E0503","message":"<service> 서비스가 일시적으로 응답하지 않습니다...","timestamp":...}` **503**(401 필터 응답과 동일 고정 JSON — 게이트웨이는 framework-core 미의존). 서비스명 `sanitize`(영숫자/`-_` 외 제거·빈값=upstream)로 JSON 주입 방지(permit-all 이라 외부 직접 호출 가능).
- **② `config/RateLimitConfiguration.clientIp` 보강**: XFF 첫 홉 추출을 `comma > 0` → `comma >= 0`. 선행 콤마 XFF 는 첫 토큰이 빈 문자열이 되어 remote addr 로 폴백(원문 누출 차단). 단일/다중 홉·콤마 없음·공백·trim 등 정상 케이스 전부 동일 동작.
- **③ `application.yml` 주석 정정**: `default-filters` 는 `X-Gateway: si-msa-gateway` 만 붙인다(주석은 X-Trace-Id 오기였음). 분산 추적 헤더 전파는 micrometer-tracing 자동 처리 → 주석 현실화.
- **신규 테스트 3종**: `config/PrincipalKeyResolverTest`(검증 userId→Principal→XFF 첫 홉→remote→unknown 우선순위·선행 콤마 폴백·deny-empty-key 안전, MockServerWebExchange, **Redis 불요**) · `web/FallbackControllerTest`(503/형식/서비스명 보간/정화/빈값, **컨텍스트 불요**) · `GatewayCorsPreflightTest`(**`@SpringBootTest` RANDOM_PORT + WebTestClient**: 허용 origin echo·credentials·max-age·methods, 미허용 origin 차단, `add-to-simple-url-handler-mapping` 비라우트 경로 처리; 인증 off·Redis 불요 — preflight 는 라우팅/레이트리밋보다 앞에서 단락).
- **검증(작성환경, JRE→JDK 설치 후 standalone 하네스 — clientIp/키산출 알고리즘 1:1 복제 실행)**: 키 산출 우선순위/XFF/폴백/deny-empty-key **12/12**, `>= 0` 수정안의 정상 케이스 보존 + 선행 콤마 폴백 + 현재 결함 재현 **8/8**. CORS preflight·서킷브레이커 실포워딩은 Spring 기동 필요 → 받는 쪽.
- **문서**: `docs/modules/GATEWAY_RUNTIME_CHECK.md` **신설**(발견→보강·런타임 거동 요약·검증 체크리스트·gotcha) · `services/gateway/README.md` §2(테스트 3종 추가)·§5(폴백 503·CORS preflight·429 Redis 확인) · `HANDOFF.md`(§6 함정 묶음·§7 완료 항목·우선순위 마킹) · `HANDOFF_SUMMARY.md`(이 문서).

## 새로 확정한 함정 (HANDOFF §6 등록)
- **서킷브레이커 `forward:/fallback/{service}` 는 받는 핸들러 필수**: `fallbackUri` 만 적고 핸들러 없으면 회로 개방 시 graceful 503 이 아니라 **404**(없는 경로)가 나간다. `FallbackController` 가 503 으로 받음. 서비스명 정화(외부 직접 호출 가능=permit-all → JSON 주입 방지).
- **레이트리밋 XFF 첫 홉은 `comma >= 0`**: `> 0` 가드는 선행 콤마(`", 1.2.3.4"`)에서 실패해 원문이 키로 샌다(`ip:, 1.2.3.4`). `>= 0` 으로 선행 콤마 → 빈 첫 토큰 → remote 폴백.
- **CORS preflight 는 인증/레이트리밋 앞에서 단락**: `add-to-simple-url-handler-mapping: true` 라 비라우트 경로 OPTIONS 도 처리, 인증 off 여도 동작(다운스트림 X). `allowCredentials=true` → `allowedOrigins:"*"` 불가, `allowedOriginPatterns` 사용(origin echo).
- **레이트리밋 429 실측엔 reactive Redis 필요**: 키 산출은 단위 테스트(Redis 불요)로, 토큰버킷 429 자체는 SCG 상위 구현 책임. 통합 429 자동화가 필요하면 Testcontainers Redis 별도 도입(미도입).

## 실행/검증 (받는 쪽 — gradle 가능 환경)
```bash
./gradlew :services:gateway:test --tests "*PrincipalKeyResolverTest"   # 키 산출 우선순위/XFF/폴백 (Redis 불요)
./gradlew :services:gateway:test --tests "*FallbackControllerTest"     # 서킷브레이커 503 (컨텍스트 불요)
./gradlew :services:gateway:test --tests "*GatewayCorsPreflightTest"   # CORS preflight (게이트웨이 기동, Redis 불요)
./gradlew :services:gateway:test                                       # 전체(기존 DualIssuer/AuthGlobalFilter/TokenVerifier 회귀 포함)
./gradlew :services:gateway:spotlessApply
# (수동 기동) README §4~5: 헬스 /actuator/health · CORS preflight · 429(Redis) · 폴백 503
```
> 작성환경은 Maven Central 차단으로 gradle 실행 불가 → 위 명령은 받는 쪽 실행 확인 필요. 순수 키 산출 로직은 JDK 단독 하네스 12+8 통과(2026-06-04). 남은 건 받는 쪽 테스트 통과 확인 → commit/push.

## 다음 (Next) 후보
- **▶ 받는 쪽 `:services:gateway:test`(+spotless) 통과 확인 후 commit/push** (이번 산출물 = FallbackController 신규 + RateLimitConfiguration 보강 + 테스트 3종 + 문서).
- **그릇 정비 잔여**(권장 다음): k8s redis/secret/멀티서비스 + observability ServiceMonitor 실배포 · CI-CD 멀티서비스화(현재 user-service 단일 → gateway/admin/auth-server 확장) · 게이트웨이 **레이트리밋 429 Testcontainers Redis 통합테스트**(선택).
- (선택 백로그) 아카이빙 tar/tar.gz(commons-compress 옵트인) · RetryUtils · 규제특화 잔여(pki/hsm/recon/egov) · saga 단계별 타임아웃/보상 재시도 · 암호화 파일 키 회전 · S3 멀티파트 병렬 업로드(TransferManager).
- (보류) SSO 6.2-B SP-initiated SLO · 6.4 Passwordless(WebAuthn) · OIDC B안 전체 흐름 e2e(confidential demo-rp) · 게이트웨이 AS aud 검증.
<!-- 갱신 끝 -->
