package com.company.framework.oauthclient.store;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 운영 표준 state 저장소(다중 파드 공유 + TTL 네이티브 만료). 키: {keyPrefix}{state}.
 * 값: 비OIDC = providerId, OIDC = providerId + '\n' + nonce(개행은 providerId 에 등장하지 않음).
 * 소비는 getAndDelete 로 원자적 1회용을 보장(재사용 공격 차단).
 */
public class RedisOAuthStateStore implements OAuthStateStore {

    private static final char SEP = '\n';

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public RedisOAuthStateStore(StringRedisTemplate redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = (keyPrefix == null || keyPrefix.isBlank()) ? "oauth:state:" : keyPrefix;
    }

    @Override
    public void save(String state, String providerId, Duration ttl) {
        redis.opsForValue().set(keyPrefix + state, providerId, ttl);
    }

    @Override
    public void save(String state, String providerId, String nonce, Duration ttl) {
        String value = nonce == null ? providerId : providerId + SEP + nonce;
        redis.opsForValue().set(keyPrefix + state, value, ttl);
    }

    @Override
    public Optional<String> consume(String state) {
        return consumeState(state).map(StateData::providerId);
    }

    @Override
    public Optional<StateData> consumeState(String state) {
        String value = redis.opsForValue().getAndDelete(keyPrefix + state);
        if (value == null) {
            return Optional.empty();
        }
        int sep = value.indexOf(SEP);
        if (sep < 0) {
            return Optional.of(new StateData(value, null));
        }
        return Optional.of(new StateData(value.substring(0, sep), value.substring(sep + 1)));
    }
}
