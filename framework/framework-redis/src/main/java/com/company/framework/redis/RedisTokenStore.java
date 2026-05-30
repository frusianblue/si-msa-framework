package com.company.framework.redis;

import com.company.framework.security.token.TokenStore;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 운영 표준 TokenStore. TTL 네이티브 만료로 블랙리스트/refresh 정리가 자동.
 * 키: rt:{refreshToken} -> "userId|role1,role2", bl:{jti} -> "1"
 */
public class RedisTokenStore implements TokenStore {

    private static final String RT = "rt:";
    private static final String BL = "bl:";

    private final StringRedisTemplate redis;

    public RedisTokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void saveRefresh(String refreshToken, RefreshEntry entry, Duration ttl) {
        String value = entry.userId() + "|" + String.join(",", entry.roles());
        redis.opsForValue().set(RT + refreshToken, value, ttl);
    }

    @Override
    public Optional<RefreshEntry> findRefresh(String refreshToken) {
        String value = redis.opsForValue().get(RT + refreshToken);
        if (value == null) return Optional.empty();
        int sep = value.indexOf('|');
        String userId = value.substring(0, sep);
        String rolesCsv = value.substring(sep + 1);
        List<String> roles = rolesCsv.isBlank() ? List.of() : Arrays.asList(rolesCsv.split(","));
        return Optional.of(new RefreshEntry(userId, roles));
    }

    @Override
    public void removeRefresh(String refreshToken) {
        redis.delete(RT + refreshToken);
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        redis.opsForValue().set(BL + jti, "1", ttl);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(BL + jti));
    }
}
