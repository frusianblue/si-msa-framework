package com.company.gateway.auth;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * reactive Redis 기반 블랙리스트 조회. {@code framework-security} 의 {@code RedisTokenStore} 가 쓰는
 * <b>동일한 키 규약</b>({@code bl:{jti}})으로 {@code hasKey} 만 수행한다(논블로킹, 게이트웨이 핫패스 적합).
 *
 * <p><b>키 동기화 주의</b>: 이 prefix 는 {@code RedisTokenStore.BL} 과 반드시 같아야 한다. 한쪽을 바꾸면
 * 중앙 로그아웃이 조용히 깨진다(토큰이 블랙리스트에 있어도 게이트웨이가 못 찾음).
 */
public class RedisGatewayTokenBlacklist implements GatewayTokenBlacklist {

    /** RedisTokenStore 의 블랙리스트 키 prefix 와 동일. */
    private static final String BLACKLIST_PREFIX = "bl:";

    private final ReactiveStringRedisTemplate redis;

    public RedisGatewayTokenBlacklist(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Boolean> isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            // jti 가 없는 토큰은 개별 무효화 대상이 아니다(서명/만료 검증만으로 판단).
            return Mono.just(false);
        }
        return redis.hasKey(BLACKLIST_PREFIX + jti).defaultIfEmpty(false);
    }
}
