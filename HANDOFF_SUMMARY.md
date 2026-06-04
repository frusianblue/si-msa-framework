# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**직전 = 다운스트림 zero-trust 재검증(AS 토큰까지) 완료 — ✅ 받는 쪽 컴파일 정상 확인(2026-06-04).** `framework.security.edge-trust.mode` 로 헤더 신뢰(`gateway-headers`) ↔ Bearer 재검증(`zero-trust`, 자체 JWT + AS RS256/JWKS) 분기. **바로 다음 = AS 서명키 회전 스케줄러** — 착수 설계 노트 `docs/NEXT_SIGNING_KEY_ROTATION.md` 준비 완료.

## 최종 갱신
- 일자: 2026-06-04 · 갱신자: zero-trust 마감 + 서명키 회전 착수 준비 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)**

## 직전에 한 것 (Done, 컴파일 확인됨)
- **다운스트림 zero-trust 재검증** (`framework-security`): 신규 `jwt/ResourceServerJwtVerifier`(AS RS256/JWKS, HS 거부·iss/aud/sub)·`jwt/DownstreamTokenAuthenticator`(iss 라우팅 → Spring `Authentication`)·`jwt/TokenIssuerKind`. 수정 `JwtAuthenticationFilter`(모드 분기: zero-trust=Bearer 재검증·INTERNAL 만 jti 블랙리스트 §4 / gateway-headers=주입 헤더 신뢰)·`FrameworkSecurityProperties`(`edge-trust`+`resource-server` 중첩)·`SecurityAutoConfiguration`(옵트인 빈 배선). user-service yml env 주입(`EDGE_TRUST_MODE`/`RESOURCE_SERVER_ENABLED`). 테스트 `DownstreamDualIssuerTest`(11). **build.gradle 무변경**(RestClient=starter-security 전이). 기본값(zero-trust + resource-server off)=도입 전 동작 동일.
- 문서: `TOKEN_VERIFICATION_GUIDE.md` §6.4/§7(K8s vs VM)·`AUTH_SERVER.md` §8·`HANDOFF.md`(§3·§6·§7).

## 바로 다음 할 것 — AS 서명키 회전 스케줄러
**착수 전 반드시 `docs/NEXT_SIGNING_KEY_ROTATION.md` 통독**(구현 항목·결정·프로퍼티·함정·체크리스트 전부 정리됨). 요지만:

- **이미 있는 골격(재사용)**: `services/auth-server` `jose/` — `auth_signing_key` 테이블(kid/jwk_json[개인키 포함]/status[ACTIVE|RETIRED]/created_at, Flyway V2) · `JdbcRotatingJwkSource`(읽기=JWKS 최신순 ACTIVE+RETIRED **오버랩**+캐시 TTL · 부트스트랩 1개 · `static RSAKey generateRsaKey()` **package-private**) · `SigningKeyMapper`(findAllUsable/findNewestActive/insert/updateStatus). **회전 스케줄러만 빈 확장점**(코드 주석/DDL/엔티티 javadoc 전부 "회전=framework-lock 리더선출, 미구현" 명시).
- **재사용 인프라**: `framework-lock` `@SchedulerLock(name, atMostFor, atLeastFor)`+`DistributedLock`(memory|redis|**jdbc**, 락 테이블 `framework_lock`, DDL `framework-lock/.../db/lock-postgres.sql`). `framework-core` `AesCryptoService`(`encrypt`/`decrypt` AES-GCM) = 개인키 컬럼 암호화. **새 외부 의존성 0.**
- **추가할 것**(5): ① `build.gradle` 에 `framework-lock` 의존 + `@EnableScheduling` ② Flyway `V4__framework_lock.sql`(H2 호환 주의) ③ `SigningKeyMapper` 정리 메서드(`deleteRetiredOlderThan`) ④ 개인키 암호화 — `JdbcRotatingJwkSource` 쓰기(부트스트랩)/읽기에 `AesCryptoService`(+평문/암호문 마커 분기) ⑤ `SigningKeyRotationScheduler`(`@Scheduled`+`@SchedulerLock` — 새 ACTIVE 생성·직전 RETIRE·grace 정리, 한 트랜잭션).
- **결정 대기(제안값 노트에)**: 암호화=AES 컬럼(KMS 후속) · 주기=월1회 cron · grace=14d(> 캐시 TTL + access 수명) · 락 type=jdbc · 키=RS256/2048 · 회전 토글 기본 off.
- **핵심 함정**: `@SchedulerLock` 메서드 void · `generateRsaKey()` 가시성(스케줄러를 `jose` 패키지에) · grace>캐시TTL+access수명 · `@EnableScheduling` 누락 시 조용히 안 돔 · H2 락 테이블 이식성(6.3 관문 재발) · 평문↔암호문 혼재 마커.

## 새로 밟은/확정한 함정
- (이번 세션은 직전 zero-trust 의 받는 쪽 **컴파일 정상 확인**만 — 새 함정 없음. zero-trust 함정 7건은 HANDOFF §6 등록 완료.)

## 실행/검증 (받는 쪽 — 다음 세션)
```bash
# (회전 구현 후) auth-server:
export SIGNING_KEY_ROTATION_ENABLED=true LOCK_TYPE=jdbc
export AES_SECRET="...개인키 암호화 마스터키..."
./gradlew :services:auth-server:compileJava :services:auth-server:test
./gradlew :services:auth-server:bootRun
# 회전 트리거 후 /.well-known/openid-configuration 의 jwks 에 새 kid 등장 + 직전 kid 가 grace 동안 잔존(오버랩)
```

## 다음 (Next) 후보
- **▶ AS 서명키 회전 스케줄러** — 위 + `docs/NEXT_SIGNING_KEY_ROTATION.md`.
- **토큰 발급 라운드트립 통합테스트**(demo-web PKCE / demo-service client_credentials → 게이트웨이 → user-service zero-trust 재검증).
- (선택) 게이트웨이측 AS `aud` 검증(가이드 §6.1) · introspection(§6.2).
- (보류) SSO 6.2-B SP-initiated SLO · 6.4 Passwordless(WebAuthn).
<!-- 갱신 끝 -->
