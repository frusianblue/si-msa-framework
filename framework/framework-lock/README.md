# framework-lock

분산 락 / 스케줄러 리더 선출. k8s 다중 파드·다중 인스턴스에서 **"한 번에 한 주체"** 를 강제한다. **표준 3단 토글**을 따르며 백엔드(memory/redis/jdbc)를 옵트인으로 고른다.

## 켜는 법

**1단 · 모듈 등록** — `settings.gradle`
```gradle
include 'framework:framework-lock'   // 선택형: 분산 락/스케줄러 중복방지가 필요한 프로젝트만
```
프로젝트 `build.gradle`
```gradle
dependencies { implementation project(':framework:framework-lock') }
```

**2·3단 · 기능/구현** — `application.yml`
```yaml
framework:
  lock:
    enabled: true              # 끄면(또는 미설정) 락 빈/애스펙트 자체가 등록 안 됨
    type: redis                # memory(기본·단일JVM) | redis(다중인스턴스 공유) | jdbc(영속·DB 공유)
    default-at-most-for: 5m    # @SchedulerLock.atMostFor 미지정 시 기본 리스 상한
    default-at-least-for: 0    # @SchedulerLock.atLeastFor 미지정 시 기본 최소 보유
    scheduler:
      enabled: true            # @SchedulerLock 애스펙트 등록(기본 on; 끄면 애너테이션 무시)
```
> 운영(replicas≥2)은 `type=redis` 또는 `jdbc` 필수. `memory` 는 인스턴스마다 별도 맵이라 파드 간 상호배제가 새어나간다
> (HANDOFF 의 TokenStore/LoginAttempt/Idempotency 와 동일한 원리).

### 백엔드 선택

| type | 저장소 | 활성 조건 | 용도 |
|---|---|---|---|
| `memory` | JVM 내 `ConcurrentHashMap` | 기본(`matchIfMissing`) | 개발/테스트·단일 인스턴스 |
| `redis` | Redis (SET NX PX + Lua CAS) | 호스트에 `StringRedisTemplate`(data-redis 스타터) | 다중 인스턴스 공유 — 권장 |
| `jdbc` | 기존 DataSource 테이블 | 호스트에 `JdbcTemplate`(jdbc 스타터) | Redis 없는 폐쇄망/온프렘 |

모듈은 redis/jdbc 의존성을 `compileOnly` 로만 참조한다 — 별도 라이브러리 추가 없음. 호스트 앱에 해당 스타터가 있어야 그 백엔드가 활성(`@ConditionalOnClass`).

### JDBC 백엔드 (type=jdbc) — 테이블 생성

`src/main/resources/db/lock-postgres.sql` (H2/PostgreSQL/Oracle 공통). 운영은 Flyway 권장.
```sql
CREATE TABLE IF NOT EXISTS framework_lock (
    lock_key   VARCHAR(200) PRIMARY KEY,
    lock_owner VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL);
```
- **선점**: `PK(lock_key)` 유니크 제약 + INSERT 충돌(`DataIntegrityViolationException`)로 상호배제. 살아있는 행이 있으면 false.
- **만료 재선점**: 선점 전 만료된 동일 키 행만 정리 → 동시에 정리돼도 INSERT 는 정확히 하나만 성공(소유자 1개 보장). idempotency 의 JDBC 스토어와 동일 패턴.
- **소유자 한정 해제/연장**: `unlock`/`keepUntil` 은 `WHERE lock_key=? AND lock_owner=?` 로 원자 보장.
- `lock_owner` 컬럼명은 예약어 회피를 위해 `owner` 가 아닌 `lock_owner` 를 쓴다.
- **테이블 청소**: 만료 행은 동일 키 재선점 때만 정리되므로, 전체 정리는 운영 잡(`DELETE ... WHERE expires_at <= now`)으로 별도 권장.

## 쓰는 법

### 1) `@SchedulerLock` — 다중 파드 `@Scheduled` 중복방지(주 용도)

```java
@Scheduled(cron = "0 0 2 * * *")
@SchedulerLock(name = "nightlySettlement", atMostFor = "10m", atLeastFor = "30s")
public void settle() { ... }   // 적용 메서드는 void
```
- 매 트리거마다 `DistributedLock` 으로 `name` 락을 잡는다. 잡으면 실행, 못 잡으면(다른 파드가 실행 중) **조용히 스킵**.
- `name` 비우면 `선언타입.메서드명` 으로 자동 생성. 동일 작업을 가리키는 메서드는 같은 이름을 쓴다.
- **atMostFor**: 락 리스 상한 — 보유 파드가 죽어도 이 시간 후 자동 해제(교착 방지). 메서드 예상 실행시간보다 넉넉히. 비우면 `default-at-most-for`(5분).
- **atLeastFor**: 최소 보유 — 메서드가 더 빨리 끝나도 이 시간까지 락 유지. 트리거 시각의 파드 간 **클럭 스큐로 인한 직후 재실행**을 막는다. 비우면 `default-at-least-for`(0). 내부적으로 조기 종료 시 `keepUntil(atLeastFor - 경과)` 로 구현.
- 기간 표기: 단순형(`"30s"`, `"10m"`, `"500ms"`) 또는 ISO-8601(`"PT10M"`) 모두 허용(`DurationStyle.detectAndParse`).

> **batch 모듈과의 차이**: Spring Batch + Quartz 잡은 `spring.quartz.job-store-type=jdbc` 클러스터링으로 중복을 막는다.
> 본 모듈은 Quartz 가 아닌 *평범한 `@Scheduled`* 메서드의 중복방지 갭을 메운다.

### 2) `DistributedLock` SPI — 직접 사용

```java
private final DistributedLock lock;

// 편의 API — 여러 파드 중 한 곳에서만 1회 실행
boolean ran = lock.runIfLocked("cache-warmup", Duration.ofMinutes(2), () -> warmUp());

// 저수준 — 소유자 토큰 직접 관리(연장 등)
String token = UUID.randomUUID().toString();
if (lock.tryLock("job", token, Duration.ofMinutes(5))) {
    try { doWork(); }
    finally { lock.unlock("job", token); }   // token 일치할 때만 해제(원자적)
}
```
- **리스 기반**: 모든 락은 TTL 보유 → 보유 인스턴스가 죽어도 TTL 후 자동 해제(영구 교착 방지). TTL 은 보호 구간 예상 실행시간보다 넉넉히.
- **소유자 토큰**: `unlock`/`keepUntil` 은 `token` 이 현재 소유자와 일치할 때만 동작 → "내 락이 TTL 로 만료된 뒤 다른 인스턴스가 재획득한 락을, 뒤늦게 끝난 내가 잘못 해제/연장"하는 사고를 막는다(Redis Lua CAS, JDBC `WHERE lock_owner=?`).


## 실전 사용 예 (코드)

**스케줄러 중복 실행 방지** — `@SchedulerLock` 한 줄(다중 인스턴스에서 한 노드만 실행).
```java
// com.company.framework.lock.SchedulerLock
@Scheduled(cron = "0 0 * * * *")
@SchedulerLock(name = "hourlySettlement", atMostFor = "PT10M")
public void settle() { ... }
```
**임의 임계 구역** — `DistributedLock.runIfLocked` 로 락을 못 잡으면 건너뛴다(인메모리/JDBC/Redis 자동 선택).
```java
private final DistributedLock lock;
boolean ran = lock.runIfLocked("close:" + accountId, Duration.ofSeconds(30), () -> {
    accountService.close(accountId);   // 잡았을 때만 실행
});
```

## 끄는 법
- `framework.lock.enabled: false` 또는 키 자체 생략 → 빈/애스펙트 미등록, 런타임 비용 0.
- `framework.lock.scheduler.enabled: false` → `DistributedLock` SPI 는 살아있되 `@SchedulerLock` 애스펙트만 끔(애너테이션 무시).
- 의존성을 빼면 클래스가 사라져 오토컨피그가 `@ConditionalOnClass(DistributedLock)` 에서 탈락.

## 덮어쓰기(프로젝트 커스텀)
프로젝트가 `DistributedLock` 빈을 직접 등록하면 `@ConditionalOnMissingBean` 으로 프레임워크 기본 구현(memory/redis/jdbc)이 양보한다.

## 버전 관리
새 외부 라이브러리를 쓰면 `gradle/libs.versions.toml` 에 추가하고 `STACK.md` 표를 갱신(HANDOFF 8절). 본 모듈은 기존 redis/jdbc(+테스트 H2)만 쓰므로 신규 의존성 없음.
