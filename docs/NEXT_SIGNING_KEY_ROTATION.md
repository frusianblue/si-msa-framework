# NEXT — AS 서명키 회전 스케줄러 (착수 설계 노트)

> 다음 세션 착수용. 큰 기능 전 `docs/NEXT_*.md` 설계 노트 컨벤션(`NEXT_SSO.md` 선례)에 따른다.
> 대상: `services/auth-server`(OP) + `framework-lock`(리더 선출) + `framework-core`(`AesCryptoService`, 개인키 암호화).
> 전제 환경: Spring Boot 4.0.6 / Java 21 / SF7 / Spring Cloud 2025.1.1 / **Jackson 3(tools.jackson.*)** / Nimbus(SAS 전이).

---

## 0. 한 줄 목표
AS(OP)의 RSA 서명키를 **주기적으로 자동 회전**한다 — 다중 파드에서 **단일 파드만**(리더 선출) 새 ACTIVE 키를 발급하고 직전 키를 RETIRE,
grace 지난 키를 정리하며, DB에 저장되는 **개인키는 암호화**한다.

---

## 1. 이미 있는 골격 (재사용 — 다시 만들지 말 것)
회전의 **읽기 측 + 부트스트랩 + 상태전이**는 6.3(Authorization Server) 세션에서 완성됨. 비어 있는 건 **회전 스케줄러 하나**다.

- `services/auth-server` `jose/`:
  - `auth_signing_key` 테이블(Flyway `V2__auth_signing_key.sql`): `kid` PK · `jwk_json TEXT`(Nimbus `RSAKey.toJSONString()`, **개인키 포함**) · `status`(ACTIVE|RETIRED) · `created_at`. 인덱스 `(status, created_at DESC)`. **DDL 주석에 "운영=암호화 필요, 골격은 평문" 이미 명시.**
  - `SigningKey`(record: kid, jwkJson, status, createdAt · 상수 ACTIVE/RETIRED · `isActive()`).
  - `SigningKeyMapper`(MyBatis): `findAllUsable()`(ACTIVE+RETIRED 최신순) · `findNewestActive()` · `insert(SigningKey)` · `updateStatus(kid, status)`. **`mapper/SigningKeyMapper.xml` 동반.**
  - `JdbcRotatingJwkSource`(`JWKSource<SecurityContext>`): `get()`=JWKSet 최신순(ACTIVE+RETIRED **오버랩** → 회전 직후 이전 키 토큰 검증 가능) · 캐시 TTL(전파 지연 = 최대 TTL) · `ensureBootstrapKey()`(ACTIVE 없으면 RSA2048 1개 생성·삽입) · `static RSAKey generateRsaKey()`(RS256/2048/UUID kid, **현재 package-private**). 클래스 javadoc/`ensureBootstrapKey` TODO에 **"회전 스케줄러는 framework-lock @SchedulerLock 리더 선출, 본 골격은 확장점"** 명시.
- `framework-lock`(리더 선출 — 그대로 사용):
  - `@SchedulerLock(name, atMostFor, atLeastFor)` + `SchedulerLockAspect` → `@Scheduled` 메서드를 "한 번에 한 파드"로. **메서드는 `void`**.
  - `DistributedLock`(memory|redis|**jdbc**). 운영 다중 파드 = `framework.lock.type=jdbc`(SAS가 이미 JDBC 사용) 또는 redis.
  - JDBC 락 테이블 = `framework_lock`(lock_key PK·lock_owner·expires_at·created_at). DDL `framework/framework-lock/src/main/resources/db/lock-postgres.sql`.
- `framework-core` `AesCryptoService`(개인키 암호화 — 재사용): `new AesCryptoService(secret)` · `encrypt(plain):String`(AES-GCM, Base64 `IV(12)||ct+tag`) · `decrypt(cipher):String`. 마스터키 = `framework.crypto.aes-secret`/`AES_SECRET`(이미 `ENC()` EPP가 쓰는 값). **새 외부 의존성 0.**

---

## 2. 구현 항목 (다음 세션)

### 2-A. 회전 스케줄러 (핵심)
`services/auth-server` `jose/` 에 신규 `SigningKeyRotationScheduler`(또는 `…RotationService` + 얇은 `@Scheduled` 트리거).

```java
@Scheduled(cron = "${auth.signing-key.rotation.cron:0 0 4 1 * *}")   // 예: 매월 1일 04:00
@SchedulerLock(name = "auth-signing-key-rotation", atMostFor = "5m", atLeastFor = "1m")
public void rotate() { ... }   // void 필수(@SchedulerLock 스킵 시 반환값 없음)
```

`rotate()` 의 책임(한 트랜잭션 권장 — 새 ACTIVE 삽입 + 직전 ACTIVE→RETIRED 원자적):
1. 새 RSA 키 생성(`generateRsaKey()` 재사용) → **암호화하여** `insert(ACTIVE)`.
2. 직전 ACTIVE 들 → `updateStatus(kid, RETIRED)`(여럿이면 새것 외 전부).
3. **grace 지난 RETIRED 정리**: `created_at < now - retireGrace` 인 RETIRED 삭제. → 매퍼에 **신규 메서드 필요**(아래 2-C).
4. 캐시는 `JdbcRotatingJwkSource` TTL 로 자동 전파(명시적 무효화 불필요, 오버랩이 흡수).

> **단일 실행**: `@SchedulerLock` 이 다중 파드 중복 회전을 막는다. 추가로 회전은 멱등하게 — 같은 분에 두 번 돌아도 ACTIVE 가 둘 생기지 않게 트랜잭션 + "직전 ACTIVE 가 충분히 최신이면 skip" 가드 고려.

### 2-B. 개인키 암호화 (저장 시 암호화 / 읽을 때 복호화)
- **쓰기**: `insert` 전 `jwkJson = aes.encrypt(rsaKey.toJSONString())`. 부트스트랩 경로(`ensureBootstrapKey`)도 **동일하게 암호화**(평문/암호문 혼재 금지).
- **읽기**: `JdbcRotatingJwkSource.loadFromDb()` 가 `RSAKey.parse(aes.decrypt(row.jwkJson()))`. (현재는 `RSAKey.parse(row.jwkJson())` 평문.)
- **마스터키**: `framework.crypto.aes-secret`/`AES_SECRET`(prod 평문 주입, `ENC()` 불가 — 닭-달걀). prod 가드 `AesMasterKeySafetyGuard` 패턴 재사용 검토.
- **혼재/마이그레이션 함정**: 기존 평문 키와 신규 암호문 키 구분 필요. 제안 = **암호문에 접두 마커**(예: `enc:` 또는 기존 `ENC(...)` 규약 재사용) 후 읽기 시 마커 유무로 분기. 신규 배포(키 0)면 전부 암호문이라 불필요하지만, 데모→운영 전환·롤백 안전을 위해 마커 권장.

### 2-C. 매퍼/스키마 보강
- `SigningKeyMapper` 에 **회전 정리용 메서드 추가**: `findRetiredOlderThan(Instant cutoff)` 또는 `deleteRetiredOlderThan(Instant cutoff)`(+ xml). (현재 delete 없음.)
- (선택) `findAllActive()` — 직전 ACTIVE 들을 한 번에 RETIRE 하기 위해. 또는 `findNewestActive()` 로 직전 1건만 다뤄도 충분(설계 단순화).
- **Flyway `V4__framework_lock.sql` 신규**: `framework_lock` 테이블(`lock-postgres.sql` 반영). H2 데모 호환 SQL 주의(6.3에서 H2 이식성 관문 밟음 — §6 참조).

### 2-D. 배선/의존
- `services/auth-server/build.gradle`: `implementation project(':framework:framework-lock')` **추가**(현재 없음).
- `application.yml`: `framework.lock.enabled=true` + `framework.lock.type=${LOCK_TYPE:jdbc}` + 회전 프로퍼티(아래 §4). `@Scheduled` 활성 위해 `@EnableScheduling` 필요(SAS config 클래스 또는 부트 메인에).
- `AesCryptoService` 빈: framework-core `CryptoAutoConfiguration` 이 등록하는지 확인 → 있으면 주입, 없으면 auth-server config 에서 `new AesCryptoService(aesSecret)` 빈 정의.

---

## 3. 결정 사항 (다음 세션에서 사용자 확인 — 제안값)
1. **암호화 백엔드**: 1차 = `AesCryptoService`(AES-GCM 컬럼 암호화, 자립·새 의존성 0). KMS/Vault 는 **후속**(인터페이스 `SigningKeyCipher` 로 추상화해 두면 교체 용이). → 제안: **AES 컬럼 암호화로 시작**.
2. **회전 주기**: 제안 `cron = 0 0 4 1 * *`(매월 1일). 규제/정책에 따라 분기(90일) 조정.
3. **RETIRE grace**: RETIRED 보존 기간 ≥ (access 토큰 최대 수명 + 캐시 TTL + 여유). 제안 **14일**. 회전 주기보다 길면 안 됨(키 누적).
4. **부트스트랩 책임**: 현행처럼 `JdbcRotatingJwkSource` ctor 부트스트랩 유지(키 0 → 1개) + 회전이 이후 관리. 또는 부트스트랩을 회전 스케줄러로 일원화(운영 권장, 데모 편의는 ctor). → 제안: **ctor 부트스트랩 유지하되 암호화 경로로 통일.**
5. **키 스펙**: RS256 / RSA 2048 유지(현행).
6. **락 백엔드**: `type=jdbc`(SAS DB 공유, 추가 인프라 0) 제안. redis 도입 시 `type=redis`.

---

## 4. 토글 / 프로퍼티 (제안)
```yaml
framework:
  lock:
    enabled: true
    type: ${LOCK_TYPE:jdbc}          # 다중 파드 = jdbc | redis (memory 는 단일 JVM)
  crypto:
    aes-secret: ${AES_SECRET:...}    # 서명키 개인키 암호화 마스터키(이미 존재)

auth:
  signing-key:
    rotation:
      enabled: ${SIGNING_KEY_ROTATION_ENABLED:false}   # 기본 off(안전 컨벤션) — 운영에서 의도적 on
      cron: ${SIGNING_KEY_ROTATION_CRON:0 0 4 1 * *}
      retire-grace: ${SIGNING_KEY_RETIRE_GRACE:14d}     # RETIRED 보존 기간
    encryption:
      enabled: ${SIGNING_KEY_ENCRYPTION_ENABLED:true}   # 개인키 컬럼 암호화(운영 필수)
```
토글 기본값은 프로젝트 컨벤션대로 **회전 enabled=false(의도적 on)**, **encryption enabled=true(개인키 보호는 기본 안전쪽)**.

---

## 5. 함정 / 주의 (예상 — 구현 후 HANDOFF §6 등록)
- **`@SchedulerLock` 메서드는 `void`** — 스킵 시 반환값 없음. atMostFor 는 회전 작업 예상시간보다 넉넉히(키 생성+DB 트랜잭션).
- **grace > 캐시 TTL + access 최대 수명** — 아니면 회전 직후 발급된(직전 ACTIVE 서명) 토큰이 grace 안에 RETIRE 삭제돼 검증 실패. JWKS 오버랩이 흡수하려면 RETIRED 가 충분히 남아야 함.
- **개인키 평문/암호문 혼재** — 마커(`enc:`/`ENC()`)로 분기하거나, 신규 배포(키 0)로 시작. 읽기 측이 둘 다 처리하면 롤백 안전.
- **`generateRsaKey()` 가시성** — 현재 package-private static. 스케줄러를 `com.company.authserver.jose` 패키지에 두면 재사용 가능(권장). 아니면 public 승격.
- **H2 데모 SQL 이식성** — 락 테이블 V4 는 6.3에서 밟은 H2 관문(타임스탬프/예약어) 재발 주의. `lock-postgres.sql` 을 H2 호환으로.
- **`@EnableScheduling` 누락** — 없으면 `@Scheduled` 가 아예 안 돈다(조용히). SAS config 에 추가 + 확인.
- **회전 멱등** — 같은 트리거가 두 파드에서 거의 동시에(락 직전) 평가될 일은 `@SchedulerLock` 이 막지만, 회전 로직 자체도 "방금 ACTIVE 가 생겼으면 skip" 가드로 이중 안전.
- **부트스트랩 vs 회전 경합** — ctor 부트스트랩과 첫 회전이 겹치면 ACTIVE 2개 가능 → 회전이 "최신 1건만 ACTIVE 유지" 정리.

---

## 6. 착수 체크리스트 (순서)
1. `build.gradle` 에 `framework-lock` 의존 추가 + `@EnableScheduling`.
2. Flyway `V4__framework_lock.sql`(락 테이블, H2 호환).
3. `SigningKeyMapper` + xml 에 정리용 메서드(`deleteRetiredOlderThan`/`findRetiredOlderThan`).
4. 개인키 암호화: `JdbcRotatingJwkSource` 쓰기(부트스트랩)·읽기 경로에 `AesCryptoService` 적용(+마커 분기). `encryption.enabled` 토글.
5. `SigningKeyRotationScheduler`(`@Scheduled`+`@SchedulerLock`) — 생성·RETIRE·정리(트랜잭션).
6. `application.yml` 프로퍼티/토글(§4).
7. 테스트(§7).
8. 문서: `docs/modules/AUTH_SERVER.md` §8 → 완료로 갱신 · `HANDOFF.md`(§3 AS 항목·§6 함정·§7 상태) · `HANDOFF_SUMMARY.md` 세션 한 장 · 본 노트 상단에 "완료" 표기.

---

## 7. 테스트 계획
- **순수/단위**: 회전 로직(새 ACTIVE 생성·직전 RETIRE·grace 정리 분기)을 매퍼 mock 으로 검증. 개인키 암호화 라운드트립(`encrypt`→`decrypt`→`RSAKey.parse` 동일성).
- **JDBC(H2)**: `SigningKeyMapper` CRUD + 정리 쿼리 의미(grace cutoff). 회전 후 `findAllUsable` 가 ACTIVE 1 + RETIRED N(grace 내) 반환.
- **리더 선출**: `@SchedulerLock` 단일 실행은 `framework-lock` 의 `SchedulerLockAspectTest`/`JdbcDistributedLockTest` 가 이미 커버 → 회전 쪽은 "락 잡으면 1회 실행, 못 잡으면 skip" 정도만.
- **통합(받는 쪽)**: bootRun 후 회전 트리거 → `/.well-known/openid-configuration` 의 jwks_uri 에 새 kid 등장 + 직전 kid 가 grace 동안 잔존(오버랩) → 회전 전 토큰이 회전 후에도 검증되는지.
- ⚠️ 작성 환경 Gradle/Central 차단 → 순수 로직만 JDK 단독 실행 검증 가능, 컴파일/기동은 받는 쪽 `:services:auth-server:compileJava :test bootRun`.

---

## 8. 관련 문서
- `docs/modules/AUTH_SERVER.md` §4(이중 issuer 정합)·§8(서명키 회전 — 본 작업으로 완료 예정).
- `docs/NEXT_SSO.md` §6.3(Authorization Server 완료) — 회전은 그 후속.
- `docs/TOKEN_VERIFICATION_GUIDE.md` §6.3(RS256 전환), §4 폐기 경계(회전 ≠ 폐기, 폐기는 `/oauth2/revoke`).
