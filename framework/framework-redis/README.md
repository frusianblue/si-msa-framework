# framework-redis

`framework-security` 의 **TokenStore / LoginAttempt 를 Redis 백엔드**로 구현한다. 멀티 인스턴스에서 토큰·로그인 잠금을 파드 간 공유하기 위한 선택 모듈.

## 켜는 법
```gradle
dependencies { implementation project(':framework:framework-redis') }   // spring-boot-starter-data-redis 전이
```
`application.yml` — security 쪽 타입을 redis 로:
```yaml
spring:
  data:
    redis: { host: ${REDIS_HOST}, port: 6379 }
framework:
  security:
    token-store:   { type: redis }   # → RedisTokenStore
    login-attempt: { type: redis }   # → RedisLoginAttemptService
```
> 이 모듈은 두 오토컨피그(`RedisTokenStoreAutoConfiguration`, `RedisLoginAttemptAutoConfiguration`)를 모두 `.imports` 에 등록한다. 각각 security 의 해당 `type=redis` 일 때 활성.

## 쓰는 법
직접 호출할 API 는 없다. `framework-security` 의 인증 흐름이 자동으로 Redis 구현을 사용한다(토큰 저장/무효화, 실패 횟수 카운트/잠금).

## 끄는 법
`type` 을 `memory`/`jdbc` 로 두거나 의존성 미포함. 그러면 security 기본(InMemory)/JDBC 구현이 쓰인다.

## 덮어쓰기(프로젝트 커스텀)
프로젝트가 `TokenStore`/`LoginAttemptService` 빈을 직접 등록하면 그게 우선(`@ConditionalOnMissingBean`).

## 버전 관리
spring-boot-starter-data-redis 는 Boot BOM 관리. 신규 의존성 없음.
