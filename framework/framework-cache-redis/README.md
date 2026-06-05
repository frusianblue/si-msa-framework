# framework-cache-redis

분산 캐시(Redis 백엔드). k8s 다중 파드가 **하나의 캐시를 공유**하도록 한다. framework-core 가 기본 제공하는 **로컬 Caffeine 캐시**를 Redis 로 **대체**하는 옵트인 모듈이다(표준 3단 토글, 기본 off).

## 왜 별도 모듈인가 (중요)

framework-core 의 `CacheAutoConfiguration` 은 Caffeine `CacheManager` 를 `@ConditionalOnMissingBean(CacheManager) + matchIfMissing=true` 로 **항상** 등록한다. 그래서:

- Boot 네이티브 redis 캐시(`spring.cache.type=redis`)는 이미 `CacheManager` 빈이 있어 **백오프 → 적용되지 않는다.**
- 분산 캐시를 쓰려면 **core 보다 먼저** Redis `CacheManager` 를 등록해 core 가 자기 `@ConditionalOnMissingBean` 으로 물러나게 해야 한다.

본 모듈의 `RedisCacheAutoConfiguration` 은 `@AutoConfiguration(before = CacheAutoConfiguration.class)` 로 그 역할을 한다. 켜면 Redis, 끄면(기본) core 의 Caffeine 이 그대로 동작한다.

## 켜는 법

**1단 · 모듈 등록** — `settings.gradle`
```gradle
include 'framework:framework-cache-redis'   // 선택형: 파드 간 캐시 공유가 필요한 프로젝트만
```
프로젝트 `build.gradle`
```gradle
dependencies {
    implementation project(':framework:framework-cache-redis')
    // Redis 백엔드(호스트가 제공) — 본 모듈은 compileOnly 로만 참조하므로 앱에 스타터가 있어야 활성
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
}
```

**2·3단 · 기능/구현** — `application.yml`
```yaml
framework:
  cache:
    redis:
      enabled: true            # 끄면(또는 미설정) Redis 매니저 미등록 → core Caffeine 사용
      time-to-live: 10m        # 기본 TTL(캐시별 override 없으면 적용)
      key-prefix: ""           # 비우면 "캐시명::키"; 채우면 "prefix캐시명::키"
      cache-null-values: true  # null 캐시 허용(캐시 관통 완화). false 면 null 미저장
      ttls:                    # 캐시별 TTL override
        commonCode: 1h
        userProfile: 5m
spring:
  data:
    redis: { host: redis, port: 6379 }   # 호스트 앱의 Redis 연결(RedisConnectionFactory)
```
> 사용처 코드는 그대로다 — `@Cacheable("이름")` 만 붙이면 로컬/분산이 설정으로 바뀐다(`@EnableCaching` 은 core 가 이미 켬).

## 직렬화 — Jackson 3 규약

값은 기본 **JDK 직렬화**(`RedisSerializer.java()`), 키는 String 으로 저장한다. 캐시 값 객체는 `Serializable` 이어야 한다.

- 본 스택은 **Jackson 3(`tools.jackson.*`)** 이라 Spring Data Redis 의 `GenericJackson2JsonRedisSerializer`(Jackson 2)를 **의도적으로 쓰지 않는다**(레포 전역 규약 — `RedisTokenStore`/MFA 도 Redis 에 Jackson 미사용).
- JSON 직렬화가 필요하면 앱이 `RedisCacheConfiguration` 빈을 직접 등록하면 된다. 본 모듈의 기본 설정은 `@ConditionalOnMissingBean(RedisCacheConfiguration.class)` 라 그 빈이 우선한다.

```java
@Bean
RedisCacheConfiguration redisCacheConfiguration() {
    // 앱이 Jackson 3 JsonMapper 기반 RedisSerializer 를 직접 구성해 주입 가능(폴리모픽 타이핑 정책은 앱 책임)
    return RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(30));
}
```


## 실전 사용 예 (코드)

이 모듈은 Spring Cache 매니저를 Redis 로 **교체만** 한다 — 코드는 표준 `@Cacheable`/`@CacheEvict` 그대로다(직렬화는 Jackson 3 규약 적용됨).
```java
@Service
public class ProductService {
    @Cacheable(cacheNames = "product", key = "#id")
    public ProductDto get(Long id) { /* 미스일 때만 DB 조회 */ return mapper.findById(id); }

    @CacheEvict(cacheNames = "product", key = "#dto.id")
    public void update(ProductDto dto) { mapper.update(dto); }
}
```
```yaml
spring.cache.type: redis
framework.cache.redis:
  enabled: true
  default-ttl: 10m
  ttls: { product: 1h }   # 캐시별 TTL
```
확인: `redis-cli KEYS 'product*'` 로 키 생성 확인.

## 끄는 법
```yaml
framework.cache.redis.enabled: false   # 기본값(opt-in) — 명시하지 않으면 비활성
```
끄면 core 의 로컬 Caffeine `CacheManager` 가 그대로 쓰인다(위 동작 매트릭스 참고). 애플리케이션 코드(`@Cacheable`/`@CacheEvict`)는 바꿀 필요 없이 **캐시 위치만 로컬로** 돌아간다.

## 동작 매트릭스

| `framework.cache.redis.enabled` | 호스트에 data-redis | 결과 |
|---|---|---|
| 미설정/false | — | core Caffeine 로컬 캐시(기본) |
| true | 있음 | **Redis 분산 캐시**(core Caffeine 백오프) |
| true | 없음 | 컨텍스트 기동 실패(명시적 opt-in 이므로 Redis 연결은 앱 책임) |

## 한계 / 주의

- 단일 Redis(또는 단일 마스터) 전제. 멀티마스터 합의 수준은 범위 밖(대다수 SI 캐시 공유에는 충분).
- **2단(near-cache + Redis)** 은 본 v1 범위 밖이다(로컬 near-cache 무효화는 Redis pub/sub 일관성 설계가 필요 → 별도 모드로 후속). 지금은 "Redis 로 대체(replace)" 만 제공.
- 신규 외부 의존성 0(spring-data-redis 는 Boot BOM 관리, compileOnly 비노출).
