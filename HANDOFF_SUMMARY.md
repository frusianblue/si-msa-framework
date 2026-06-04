# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**직전 = AS 서명키 회전 스케줄러 + 개인키 암호화 완료(2026-06-04).** OP RSA 서명키를 framework-lock `@SchedulerLock`(리더 선출)로 단일 파드만 주기 회전(직전 ACTIVE 전부 RETIRE → 새 ACTIVE INSERT → grace 정리, 한 트랜잭션), DB 개인키는 `AesCryptoService` AES-GCM 컬럼 암호화(`enc:` 마커). 새 외부 의존성 0. **순수 JDK 로직 검증 23/23 통과** — 받는 쪽 Gradle 빌드/`bootRun` 대기. **바로 다음 = 토큰 발급 라운드트립 통합테스트.**

## 최종 갱신
- 일자: 2026-06-04 · 갱신자: 서명키 회전 스케줄러 마감 세션
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)** / Nimbus(SAS 전이)

## 직전에 한 것 (Done, 순수 JDK 로직 검증 — 받는 쪽 빌드 대기)
- **AS 서명키 회전 + 개인키 암호화** (`services/auth-server`, framework-lock/framework-core 재사용):
  - 신규: `jose/SigningKeyCipher`(추상)·`AesSigningKeyCipher`(AES-GCM, `enc:` 마커, `AesCryptoService` 재사용)·`SigningKeyGenerator`(@FunctionalInterface)·`RsaSigningKeyGenerator`·`SigningKeyRotationService`(@Transactional `rotateOnce()`: RETIRE→INSERT→grace정리 + 멱등 가드, `Outcome` record)·`SigningKeyRotationScheduler`(@Scheduled cron + @SchedulerLock thin wrapper)·`config/SigningKeyProperties`(`auth-server.signing-key.{rotation,encryption}`).
  - 수정: `SigningKey`(+`retiredAt`·`active()` 팩토리)·`SigningKeyMapper`(+`retireAllActive`/`deleteRetiredOlderThan`)·`SigningKeyMapper.xml`·`JdbcRotatingJwkSource`(ctor+cipher, 읽기 `reveal`·부트스트랩 `protect`)·`AuthorizationServerConfig`(@EnableConfigurationProperties + cipher/generator/service/scheduler 빈, 중첩 `SigningKeyRotationConfig` @ConditionalOnProperty)·`AuthServerApplication`(@EnableScheduling)·`build.gradle`(framework-lock)·`application.yml`.
  - 마이그레이션: `V4__framework_lock.sql`(H2/PG 호환)·`V5__auth_signing_key_retired_at.sql`.
  - 테스트: `SigningKeyRotationServiceTest`(회전·멱등·grace)·`AesSigningKeyCipherTest`(마커 라운드트립·평문 passthrough·암호화 off·null) — JUnit5+AssertJ, 받는 쪽 실행.
- **핵심 결정 2건 (설계 노트와 의도적 편차)**:
  - ① **grace 정리 기준 `created_at`→`retired_at`**: grace(14d) < 회전주기(30d)면 `created_at` 기준 정리가 직전 키를 RETIRE 즉시 삭제해 오버랩 붕괴 → `retired_at` 컬럼(V5) 앵커.
  - ② **회전 순서 retire-then-insert + 단일 트랜잭션**: insert-then-retire 는 다중 파드 경합 시 0-ACTIVE(서명 불가) 위험 → RETIRE 먼저 + 한 트랜잭션이면 독자에게 원자적, 최악도 ACTIVE 2개(오버랩 흡수).
  - (부가) 프로퍼티 prefix `auth-server.signing-key.*`(노트 `auth.signing-key` 대신 기존 네임스페이스 통합), 환경변수명 `SIGNING_KEY_*` 유지.
- 문서: `NEXT_SIGNING_KEY_ROTATION.md`(완료/편차 배너)·`modules/AUTH_SERVER.md` §3/§7/§8·`HANDOFF.md`(§3·§6 함정 묶음 a~g·§7).

## 새로 밟은/확정한 함정 (HANDOFF §6 등록)
- **grace=`retired_at` 앵커**(created_at 기준이면 grace<주기 시 오버랩 붕괴) · **회전 순서 retire-then-insert**(insert-then-retire 는 0-ACTIVE) · **`@SchedulerLock` 없으면 모든 파드 회전**(memory 락은 파드 간 무력 → 운영은 jdbc|redis 필수, 멱등 가드는 2차 안전망일 뿐) · **`@EnableScheduling` 누락 시 조용히 무동작** · **개인키 평문↔암호문 분기 = `enc:` 마커**(읽기는 토글 무관 항상 인지 → 혼재/롤백 안전, 설정 암호화 `ENC()` 와 다른 표기) · **`generateRsaKey()` package-private** → 회전 신규 클래스를 `jose` 패키지에 · **스케줄러=thin wrapper / 서비스=@Transactional 분리**(AOP 어드바이저 충돌·자기호출 프록시 우회 회피).

## 실행/검증 (받는 쪽 — 다음 세션 필수)
```bash
# auth-server 회전 활성화 후 검증:
export SIGNING_KEY_ROTATION_ENABLED=true LOCK_TYPE=jdbc
export AES_SECRET="<32+자 강한 마스터키>"   # prod 는 약한키/기본값이면 부팅 차단(AesMasterKeySafetyGuard)
./gradlew :services:auth-server:compileJava :services:auth-server:test :services:auth-server:bootRun
# 회전 트리거(cron 또는 수동) 후 /.well-known/openid-configuration 의 jwks 에 새 kid 등장
#  + 직전 kid 가 grace 동안 잔존(오버랩) + auth_signing_key 의 jwk_json 이 enc: 마커로 저장됨 확인
```
> 작성환경은 JRE only(javac 부재)·Maven Central 차단으로 Gradle 빌드 불가 → cipher 보호/해제 + 회전 오케스트레이션(순서·멱등·grace=retired_at·0-ACTIVE 불변식)을 순수 JDK standalone 으로 재현해 **23/23 통과**. 최종 검증은 받는 쪽 빌드.

## 다음 (Next) 후보
- **▶ 토큰 발급 라운드트립 통합테스트** — demo-web authorization_code+PKCE / demo-service client_credentials → 게이트웨이 이중 발급기 → user-service zero-trust 재검증(전 구간 e2e).
- (선택) 게이트웨이측 AS `aud` 검증 · introspection · 서명키 KMS/Vault 백엔드(`SigningKeyCipher` 교체).
- (devops) CI 게이트(archtest + 전 모듈 test PR 차단) · 멀티모듈 jacoco 집계 · k8s 멀티서비스/observability 실배포.
- (보류) SSO 6.2-B SP-initiated SLO · 6.4 Passwordless(WebAuthn).
<!-- 갱신 끝 -->
