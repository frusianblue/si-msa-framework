package com.company.framework.mfa.store;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 운영 표준 챌린지 저장소(다중 파드 공유 + TTL 네이티브 만료). 키: {keyPrefix}{ticket}.
 *
 * <p><b>직렬화는 수기 고정형</b>으로 한다(Jackson 의존/주입 취약성 회피 — secure-web/JSON 원칙과 동일).
 * 필드 구분자 {@code |}, roles/methods 내부 구분자 {@code ,}. roles/userId 가 구분자를 포함할 수 있어
 * userId·roles·methods 는 Base64(URL-safe)로 인코딩해 충돌을 원천 차단한다.
 *
 * <p>레코드 형식: {@code attempts|userIdB64|otpHashOrDash|rolesCsvB64|methodsCsvB64}
 */
public class RedisMfaChallengeStore implements MfaChallengeStore {

    private final StringRedisTemplate redis;
    private final String keyPrefix;

    public RedisMfaChallengeStore(StringRedisTemplate redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = (keyPrefix == null || keyPrefix.isBlank()) ? "mfa:chal:" : keyPrefix;
    }

    @Override
    public void save(String ticket, PendingAuth pending, Duration ttl) {
        redis.opsForValue().set(keyPrefix + ticket, serialize(pending), ttl);
    }

    @Override
    public Optional<PendingAuth> find(String ticket) {
        String raw = redis.opsForValue().get(keyPrefix + ticket);
        return raw == null ? Optional.empty() : Optional.of(deserialize(raw));
    }

    @Override
    public void remove(String ticket) {
        redis.delete(keyPrefix + ticket);
    }

    private static String serialize(PendingAuth p) {
        return p.attempts()
                + "|" + b64(p.userId())
                + "|" + (p.otpCodeHash() == null ? "-" : p.otpCodeHash())
                + "|" + b64(csv(p.roles()))
                + "|" + b64(csv(p.methods()));
    }

    private static PendingAuth deserialize(String raw) {
        String[] parts = raw.split("\\|", -1);
        int attempts = Integer.parseInt(parts[0]);
        String userId = unb64(parts[1]);
        String otpHash = "-".equals(parts[2]) ? null : parts[2];
        List<String> roles = uncsv(unb64(parts[3]));
        List<String> methods = uncsv(unb64(parts[4]));
        return new PendingAuth(userId, roles, methods, otpHash, attempts);
    }

    private static String csv(List<String> values) {
        return values == null ? "" : String.join(",", values);
    }

    private static List<String> uncsv(String csv) {
        return (csv == null || csv.isBlank()) ? List.of() : List.of(csv.split(","));
    }

    private static String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString((value == null ? "" : value).getBytes());
    }

    private static String unb64(String value) {
        return new String(Base64.getUrlDecoder().decode(value));
    }
}
