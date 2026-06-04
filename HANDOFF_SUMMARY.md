# HANDOFF_SUMMARY.md — 세션 단위 한 장 (매 세션 갱신)

> 사용법: 세션을 넘길 때마다 아래 **`<!-- 갱신 -->`** 구간만 새로 쓴다. 구조는 그대로 둔다.
> 전체 맥락은 `HANDOFF.md`, 사용법은 `README.md`, 버전은 `STACK.md`, 모듈 설계는 `docs/FRAMEWORK_MODULES.md`.

---
<!-- 갱신 시작 -->
## 이번 세션 한 줄 요약
**직전 = AS 서명키 회전 스케줄러 + 개인키 암호화 완료 및 받는 쪽 기동 확인(2026-06-04).** OP RSA 서명키를 framework-lock `@SchedulerLock`(리더 선출)로 단일 파드만 주기 회전(직전 ACTIVE 전부 RETIRE → 새 ACTIVE INSERT → grace 정리, 한 트랜잭션), DB 개인키는 `AesCryptoService` AES-GCM 컬럼 암호화(`enc:` 마커). 새 외부 의존성 0. 기동 과정에서 **함정 2건(framework-lock optional 의존 introspect 폭발·`@MapperScan` 매퍼 오인) 수정 → ✅ 받는 쪽 `bootRun` 정상 기동 확인.** + **서비스 모듈 4종 README(기동 방법) 신설.** **바로 다음 = 토큰 발급 라운드트립 통합테스트.**

## 최종 갱신
- 일자: 2026-06-04 · 갱신자: 서명키 회전 마감 + 기동 확정 + 서비스 README 작성 세션 (섹션 종료)
- 대상 브랜치: master · 환경: Spring Boot 4.0.6 / Java 21 / Spring Framework 7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)** / Nimbus(SAS 전이)

## 직전에 한 것 (Done, ✅ 받는 쪽 기동 확인)
- **AS 서명키 회전 + 개인키 암호화** (`services/auth-server`, framework-lock/framework-core 재사용):
  - 신규: `jose/SigningKeyCipher`(추상)·`AesSigningKeyCipher`(AES-GCM, `enc:` 마커, `AesCryptoService` 재사용)·`SigningKeyGenerator`(@FunctionalInterface)·`RsaSigningKeyGenerator`·`SigningKeyRotationService`(@Transactional `rotateOnce()`: RETIRE→INSERT→grace정리 + 멱등 가드, `Outcome` record)·`SigningKeyRotationScheduler`(@Scheduled cron + @SchedulerLock thin wrapper)·`config/SigningKeyProperties`(`auth-server.signing-key.{rotation,encryption}`).
  - 수정: `SigningKey`(+`retiredAt`·`active()` 팩토리)·`SigningKeyMapper`(+`retireAllActive`/`deleteRetiredOlderThan`)·`SigningKeyMapper.xml`·`JdbcRotatingJwkSource`(ctor+cipher, 읽기 `reveal`·부트스트랩 `protect`)·`AuthorizationServerConfig`(@EnableConfigurationProperties + cipher/generator/service/scheduler 빈, 중첩 `SigningKeyRotationConfig` @ConditionalOnProperty)·`AuthServerApplication`(@EnableScheduling, `@MapperScan(annotationClass=Mapper.class)`)·`build.gradle`(framework-lock)·`application.yml`.
  - 마이그레이션: `V4__framework_lock.sql`(H2/PG 호환)·`V5__auth_signing_key_retired_at.sql`.
  - 테스트: `SigningKeyRotationServiceTest`(회전·멱등·grace)·`AesSigningKeyCipherTest`(마커 라운드트립·평문 passthrough·암호화 off·null) — JUnit5+AssertJ.
- **기동 함정 2건 수정**(아래 함정 절): framework-lock `LockAutoConfiguration` 중첩 격리 + `@MapperScan` annotationClass 필터 → bootRun 정상 기동.
- **★ 서비스 모듈 README 4종 신설**: `services/{auth-server,user-service,admin-service,gateway}/README.md` — 포트·프로파일 오버레이·`bootRun` 명령·환경변수·대표 엔드포인트·`bootJar`·문서 링크. (기존 서비스 README 전무 → 규약화: HANDOFF §8.)
- **핵심 결정 2건 (설계 노트와 의도적 편차)**:
  - ① **grace 정리 기준 `created_at`→`retired_at`**: grace(14d) < 회전주기(30d)면 `created_at` 기준 정리가 직전 키를 RETIRE 즉시 삭제해 오버랩 붕괴 → `retired_at` 컬럼(V5) 앵커.
  - ② **회전 순서 retire-then-insert + 단일 트랜잭션**: insert-then-retire 는 다중 파드 경합 시 0-ACTIVE(서명 불가) 위험 → RETIRE 먼저 + 한 트랜잭션이면 독자에게 원자적, 최악도 ACTIVE 2개(오버랩 흡수).
  - (부가) 프로퍼티 prefix `auth-server.signing-key.*`(노트 `auth.signing-key` 대신 기존 네임스페이스 통합), 환경변수명 `SIGNING_KEY_*` 유지.
- 문서: `NEXT_SIGNING_KEY_ROTATION.md`(완료/편차 배너)·`modules/AUTH_SERVER.md` §3/§7/§8·`HANDOFF.md`(§3·§6 함정·§7·§8 README 규약)·서비스 README 4종·루트 `README.md`(서비스 README 링크).

## 새로 밟은/확정한 함정 (HANDOFF §6 등록)
- **🔧 framework-lock 런타임 버그 수정 (auth-server jdbc-only 소비자에서 노출)**: `LockAutoConfiguration` 최상위에 `redisDistributedLock(StringRedisTemplate)` 빈이 있고 형제 `schedulerLockAspect` 가 타입 미지정 `@ConditionalOnMissingBean` 이라, 빈 타입 추론이 클래스 introspect(`getDeclaredMethods()`) 를 트리거 → redis 없는 런타임에서 `NoClassDefFoundError: StringRedisTemplate` → 기동 불가. **해법 = redis/jdbc 빈을 `@ConditionalOnClass` 가드 중첩 `@Configuration`(`RedisLockConfiguration`/`JdbcLockConfiguration`)으로 격리 + 애스펙트 `@ConditionalOnMissingBean(SchedulerLockAspect.class)` 타입 명시.** 일반 교훈: autoconfig 최상위 @Configuration 은 런타임 보장 타입만, compileOnly/optional 타입은 중첩 @ConditionalOnClass 로 격리. 테스트(클래스패스에 redis 있음)는 무영향 → 그래서 잠복했었음.
- **🔧 `@MapperScan` 매퍼 오인 충돌 수정 (auth-server 기동)**: `@MapperScan("...jose")` 가 필터 없이 걸려 SPI 인터페이스 `SigningKeyCipher`/`SigningKeyGenerator` 까지 매퍼로 등록 → 동명 `@Bean` 과 `ConflictingBeanDefinitionException`. **해법 = `@MapperScan(annotationClass = Mapper.class)`** 로 `@Mapper` 인터페이스만 스캔. 일반 교훈: 명시적 `@MapperScan` 대상 패키지에 매퍼 외 인터페이스를 두려면 `annotationClass`/`markerInterface` 필터 필수(자동스캔은 기본 안전, 명시 스캔은 기본 무필터).
- 회전 함정: **grace=`retired_at` 앵커** · **회전 순서 retire-then-insert**(0-ACTIVE 방지) · **`@SchedulerLock` 없으면 모든 파드 회전** · **`@EnableScheduling` 누락 시 조용히 무동작** · **개인키 `enc:` 마커 분기**(읽기는 토글 무관 인지) · **스케줄러=thin wrapper / 서비스=@Transactional 분리**.

## 실행/검증 (✅ 받는 쪽 기동 확인 완료)
```bash
# auth-server 회전 활성화 기동 (확인됨):
export AES_SECRET="$(openssl rand -base64 32)"   # prod 약한키/기본값이면 부팅 차단(AesMasterKeySafetyGuard). 한 번 정하면 변경 금지
export SIGNING_KEY_ROTATION_ENABLED=true LOCK_TYPE=jdbc
./gradlew :services:auth-server:bootRun
# → /.well-known/openid-configuration 200, 서명키 부트스트랩(enc: 마커 저장) 정상 기동 확인
```
> 남은 런타임 확인(권장, 다음 세션 초입에 가볍게): 회전 1회 트리거 후 `/oauth2/jwks` 에 새 kid 등장 + 직전 kid grace 잔존(오버랩), 다중 파드면 `@SchedulerLock` 으로 회전 1회만.
> 각 서비스 기동 방법은 신설된 `services/*/README.md` 참조.

## 다음 (Next) 후보
- **▶ 토큰 발급 라운드트립 통합테스트** — demo-web authorization_code+PKCE / demo-service client_credentials → 게이트웨이 이중 발급기 → user-service zero-trust 재검증(전 구간 e2e).
- (선택) 게이트웨이측 AS `aud` 검증 · introspection · 서명키 KMS/Vault 백엔드(`SigningKeyCipher` 교체).
- (devops) CI 게이트(archtest + 전 모듈 test PR 차단) · 멀티모듈 jacoco 집계 · k8s 멀티서비스/observability 실배포.
- (보류) SSO 6.2-B SP-initiated SLO · 6.4 Passwordless(WebAuthn).
<!-- 갱신 끝 -->
