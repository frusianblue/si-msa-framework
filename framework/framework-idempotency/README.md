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
      type: redis            # memory(기본) | redis(다중 인스턴스 공유)
```
> 운영(replicas≥2)은 `store.type=redis` 필수. memory 는 인스턴스별이라 중복 차단이 새어나간다
> (HANDOFF 의 TokenStore/LoginAttempt 와 동일한 원리).

## 쓰는 법
```java
@PostMapping("/transfers")
@Idempotent                                   // 이 메서드만 멱등 검사
public ApiResponse<Void> transfer(@RequestBody TransferRequest req) { ... }
```
- 헤더 없음 → 400, 중복 키 → 409. 클라이언트는 재시도 시 같은 키를 보낸다.

## 끄는 법
- `framework.idempotency.enabled: false` 또는 키 자체 생략 → 빈/인터셉터 미등록, 런타임 비용 0.
- 의존성을 빼면 클래스가 사라져 오토컨피그가 `@ConditionalOnClass` 에서 탈락.

## 덮어쓰기(프로젝트 커스텀)
프로젝트가 `IdempotencyStore` 빈을 직접 등록하면 `@ConditionalOnMissingBean` 으로 프레임워크 기본 구현이 양보한다.

## 버전 관리
새 외부 라이브러리를 쓰면 `gradle/libs.versions.toml` 에 추가하고 `STACK.md` 표를 갱신(HANDOFF 8절).
