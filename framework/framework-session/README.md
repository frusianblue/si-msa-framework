# framework-session

서버 **세션 기반 인증**(`framework.security.session.mode=session`)에서 `HttpSession` 을 **Redis 로 외부화**해 다중 인스턴스(K8s replicas≥2) 간 공유한다. JWT 경로의 `framework-redis`(TokenStore)와 **동일한 사상의 "세션 버전"** — 단일 인스턴스는 톰캣 로컬 세션으로 충분하고, 파드가 여러 개면 이 모듈로 세션을 공유한다.

> 인증 **모드 선택**(무상태 JWT vs 서버 세션)은 코어 `framework-security` 의 `framework.security.session.mode` 가 담당한다. 이 모듈은 "세션을 어디에 둘 것인가(클러스터 저장소)"만 더한다. 둘은 직교 — 모드는 코어에서 켜고, 공유가 필요할 때 이 모듈을 끼운다.

## 켜는 법
```gradle
dependencies { implementation project(':framework:framework-session') }  // spring-session-data-redis + starter-data-redis 전이
```
`application.yml`:
```yaml
spring:
  data:
    redis: { host: ${REDIS_HOST}, port: 6379 }
  session:
    timeout: 30m
    data:
      redis:
        namespace: si:session      # ⚠️ Boot 4: spring.session.redis.* → spring.session.data.redis.* 로 리네임됨
        flush-mode: on-save
framework:
  security:
    session: { mode: session }     # ← 코어에서 세션 모드로 전환(필수). 이게 stateless 면 이 모듈은 무의미.
```
실제 Redis 기반 `HttpSession` 외부화는 **Spring Boot 표준 `SessionAutoConfiguration`** 이 `spring-session-data-redis` 클래스패스 존재로 자동 구성한다(이 모듈이 그 의존성을 가져옴). 본 모듈은 재배선하지 않고 오설정 가드(`SessionStoreSafetyGuard`)와 `framework.session.*` 프로퍼티만 더한다.

## 쓰는 법
직접 호출할 API 는 없다. 세션 로그인은 코어가 제공하는 `POST /api/v1/auth/session/login` 으로 수립되고(`Set-Cookie: SESSION=...`), 이후 요청은 세션 쿠키로 식별된다. 이 모듈을 추가하면 그 세션이 톰캣 메모리 대신 Redis 에 저장돼 모든 파드가 공유한다.

## 끄는 법
- 의존성 미포함 → 세션은 톰캣 로컬(단일 인스턴스 전용, 파드 재시작/로드밸런싱 시 유실).
- `framework.session.enabled=false` → 이 모듈의 가드 비활성(Spring Session 자체는 클래스패스 기준).
- `framework.session.warn-if-mode-stateless=false` → "모듈 적재 + mode=stateless" 경고 끄기.

## 덮어쓰기(프로젝트 커스텀)
세션 직렬화(JSON 권장: 멀티앱 동일 Redis 공유 시 Java 직렬화 클래스버전 충돌 회피)·인덱싱 저장소(`RedisIndexedSessionRepository`, 동시세션 만료 이벤트 필요 시)는 Spring Boot 표준 프로퍼티/빈으로 조정한다. 가드 빈은 `@ConditionalOnMissingBean` 이라 프로젝트가 동명 빈을 등록하면 대체된다.

## 버전 관리
`spring-session-data-redis` / `spring-boot-starter-data-redis` 모두 **Boot BOM(Spring Session 4.x)** 관리 → 버전 명시 불필요. 신규 외부 버전 고정 없음.

## 주의 / 함정
- **Boot 4 프로퍼티 리네임**: `spring.session.redis.*` → `spring.session.data.redis.*`. 옛 키는 무시되어 조용히 기본값이 적용된다.
- **autoconfigure 모듈 분리(Boot 4)**: `spring-boot-starter-data-redis` 가 함께 있어야 Redis 오토컨피그가 동작(이 모듈이 동봉).
- **JDBC 백엔드가 필요하면**: 이 모듈 대신(또는 추가로) 앱이 `org.springframework.session:spring-session-jdbc` 를 직접 추가하고 `spring.session.store-type` 을 맞춘다. (이 모듈은 K8s 표준인 Redis 에 초점.)
