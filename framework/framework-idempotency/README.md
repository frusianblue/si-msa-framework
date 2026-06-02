# framework-idempotency (레퍼런스 모듈)

정확히-한번/멱등키 처리. **표준 3단 토글**을 모두 적용한 예시 — 신규 모듈은 이 구조를 그대로 복제한다.

## 켜는 법

**1단 · 모듈 등록** — `settings.gradle`
```gradle
include 'framework:framework-idempotency'   // 선택형: 멱등성 필요한 프로젝트만
```
프로젝트 `build.gradle`
```gradle
dependencies { implementation project(':framework:framework-idempotency') }
```

**2·3단 · 기능/구현** — `application.yml`
```yaml
framework:
  idempotency:
    enabled: true            # 끄면(또는 미설정) 인터셉터 자체가 등록 안 됨
    header: Idempotency-Key
    ttl: PT10M
    store:
      type: jdbc             # memory(기본·단일인스턴스) | redis(다중인스턴스 공유) | jdbc(영속·DB 공유)
    replay:
      enabled: false         # true 면 중복 시 409 대신 "저장된 응답 재생"(아래 재생 모드 참고)
```
> 운영(replicas≥2)은 `store.type=redis` 또는 `jdbc` 필수. memory 는 인스턴스별이라 중복 차단이 새어나간다
> (HANDOFF 의 TokenStore/LoginAttempt 와 동일한 원리).

### JDBC 스토어 (store.type=jdbc) — 영속/DB 공유

Redis 없이 기존 DataSource 만으로 다중 인스턴스 공유가 필요할 때(폐쇄망/공공·온프렘 등). 호스트 앱에 JDBC 스타터가
있어야 활성(`@ConditionalOnClass(JdbcTemplate)`). 별도 라이브러리 추가 없음 — 모듈은 `compileOnly` 로만 참조한다.

**테이블 생성** — `src/main/resources/db/idempotency-postgres.sql` (H2/PostgreSQL/Oracle 공통). 운영은 Flyway 권장.
```sql
CREATE TABLE IF NOT EXISTS framework_idempotency (
    idem_key VARCHAR(200) PRIMARY KEY, result VARCHAR(4000),
    expires_at TIMESTAMP NOT NULL, created_at TIMESTAMP NOT NULL);
```
- **선점**: `PK(idem_key)` 유니크 제약 + INSERT 충돌(`DataIntegrityViolationException`)로 상호배제. 살아있는 행이 있으면 false.
- **만료 재선점**: 선점 전 만료된 동일 키 행만 정리. 동시에 정리돼도 INSERT 는 정확히 하나만 성공 → 소유자 1개 보장.
- **전체 만료 청소**: `putIfAbsent` 는 동일 키만 정리하므로, 테이블 정리는 운영 잡(`DELETE ... WHERE expires_at <= now`)으로 별도 권장.
- `result VARCHAR(4000)` 은 결과 스냅샷 저장 시 사용(현재 인터셉터는 중복=409 정책; 결과 재생으로 확장 시 활용). 대용량이면 `TEXT/CLOB` 로 확장.

## 쓰는 법
```java
@PostMapping("/transfers")
@Idempotent                                   // 이 메서드만 멱등 검사
public ApiResponse<Void> transfer(@RequestBody TransferRequest req) { ... }
```
- 헤더 없음 → 400, 중복 키 → 409. 클라이언트는 재시도 시 같은 키를 보낸다.

### 재생(replay) 모드 — `replay.enabled=true`

중복 시 409 대신 **최초 요청의 응답을 그대로 재생**한다(같은 상태코드·콘텐츠타입·본문). 결제/주문처럼
클라이언트가 타임아웃 후 재시도해도 "같은 결과"를 받아야 하는 흐름에 쓴다.

- **최초 요청**: 키 선점 후 통과 → 처리. 완료 시 응답을 캡처해 저장(`saveResult`).
- **처리중 동일 요청**(선점됐으나 미완료): `409 request in progress`.
- **완료된 동일 요청**: 저장된 응답 재생(핸들러 재실행 없음).
- **실패는 캐시하지 않음**: 예외/`5xx`/본문 캡처 불가 시 선점을 해제 → 다음 재시도가 다시 처리. (2xx·4xx 등 `<500` 만 저장)

동작 방식: `IdempotencyResponseFilter`(재생 모드에서만 등록, `@Order(LOWEST_PRECEDENCE)`)가 **헤더 있는 요청만**
`ContentCachingResponseWrapper`로 감싸고, 인터셉터 `afterCompletion`이 버퍼 본문을 캡처한다. 헤더 없는 트래픽은
감싸지 않아 버퍼링 비용 0. 저장 포맷은 `status\ncontentType\nbase64(body)` 고정 셰이프(Jackson/문자셋 가정 없음).

> 주의: 본문을 메모리 버퍼링하므로 **작은 JSON 응답**(전형적 `ApiResponse`)에 적합하다. 스트리밍/대용량 응답에는 부적합.
> `store.type=jdbc|redis` 와 조합하면 다중 인스턴스/재기동 간에도 재생이 유지된다(`memory` 는 인스턴스 로컬).

## 끄는 법
- `framework.idempotency.enabled: false` 또는 키 자체 생략 → 빈/인터셉터 미등록, 런타임 비용 0.
- 의존성을 빼면 클래스가 사라져 오토컨피그가 `@ConditionalOnClass` 에서 탈락.

## 덮어쓰기(프로젝트 커스텀)
프로젝트가 `IdempotencyStore` 빈을 직접 등록하면 `@ConditionalOnMissingBean` 으로 프레임워크 기본 구현이 양보한다.

## 버전 관리
새 외부 라이브러리를 쓰면 `gradle/libs.versions.toml` 에 추가하고 `STACK.md` 표를 갱신(HANDOFF 8절).
