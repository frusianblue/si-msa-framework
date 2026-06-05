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

## framework-session 과의 관계 (둘 다 Redis 를 쓴다)
이 모듈은 **JWT 경로**(TokenStore/LoginAttempt)를 Redis 로, [`framework-session`](../framework-session/README.md) 은 **세션 경로**(HttpSession)를 Redis 로 외부화한다. 같은 사상의 형제 모듈이며, 한 프로젝트가 둘을 동시에 켜도 충돌하지 않는다:
- **커넥션은 공유** — Spring Boot 가 `spring.data.redis.*` 로 `RedisConnectionFactory` **하나**를 만들고 둘 다 그것을 쓴다(커넥션 풀 1벌).
- **키 네임스페이스는 분리** — TokenStore/LoginAttempt 키와 Spring Session 키(`spring.session.data.redis.namespace`)가 달라 서로 안 밟는다.
- 보통 JWT(순수 API) **또는** 세션(브라우저 콘솔) 중 하나만 쓰므로 둘 다 켜는 경우는 드물지만, 혼합(예: API 는 JWT + 어드민은 세션)도 같은 Redis 로 안전하게 가능하다.

## 앱 코드에서 Redis 를 직접 쓰고 싶을 때 (선택)
이 모듈을 넣으면 `spring-boot-starter-data-redis` 가 전이되어 `StringRedisTemplate` 이 빈으로 준비된다. 프로젝트 캐시/카운터 등에 그대로 주입해 쓸 수 있다.
```java
@Service
public class RankingService {
    private final StringRedisTemplate redis;

    public RankingService(StringRedisTemplate redis) { this.redis = redis; }

    public void hit(String itemId) {
        redis.opsForZSet().incrementScore("ranking:today", itemId, 1);
    }
    public List<String> top10() {
        return List.copyOf(redis.opsForZSet().reverseRange("ranking:today", 0, 9));
    }
}
```
> 단, 파드 간 **공유 캐시**가 목적이라면 [`framework-cache-redis`](../framework-cache-redis/README.md)(`@Cacheable` 분산화)를 우선 검토. 이 모듈의 1차 목적은 어디까지나 보안(토큰/잠금) 백엔드다.

### TokenStore/LoginAttempt 를 프로젝트가 직접 구현하려면 (덮어쓰기)
```java
@Bean
public TokenStore customTokenStore(StringRedisTemplate redis) {
    return new MyTokenStore(redis);   // @ConditionalOnMissingBean 이라 이 빈이 우선
}
```


## 실전 사용 예 (코드)

이 모듈은 **투명 백엔드**다 — `framework-security` 의 토큰 저장/로그인 실패 카운트가 자동으로 Redis 구현(`RedisTokenStore`, `RedisLoginAttemptService`)을 쓴다. 앱이 같은 커넥션으로 Redis 를 직접 쓰려면 `StringRedisTemplate` 를 주입한다.
```java
// org.springframework.data.redis.core.StringRedisTemplate (Spring Data Redis 가 제공)
private final StringRedisTemplate redis;

public void cacheNonce(String k, String v) {
    redis.opsForValue().set("app:nonce:" + k, v, Duration.ofMinutes(5));  // 앱 전용 네임스페이스 권장
}
```
토큰 저장 전략을 프로젝트가 직접 구현하려면 `TokenStore` 빈을 등록해 기본 `RedisTokenStore` 를 덮어쓴다(`@ConditionalOnMissingBean` 규약).
```bash
redis-cli KEYS 'framework:token:*'   # 프레임워크가 쓰는 키 확인
```

## 끄는 법
`type` 을 `memory`/`jdbc` 로 두거나 의존성 미포함. 그러면 security 기본(InMemory)/JDBC 구현이 쓰인다.

## 덮어쓰기(프로젝트 커스텀)
프로젝트가 `TokenStore`/`LoginAttemptService` 빈을 직접 등록하면 그게 우선(`@ConditionalOnMissingBean`).

## 버전 관리
spring-boot-starter-data-redis 는 Boot BOM 관리. 신규 의존성 없음.
