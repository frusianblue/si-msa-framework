package com.company.framework.redis;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.security.concurrent.ConcurrentSessionProperties;
import com.company.framework.security.concurrent.ConcurrentSessionService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 운영(다중 파드) 표준 동시세션 백엔드. 사용자별 활성 세션을 Redis 에 두어 인스턴스 간 일관되게 한도를 적용한다
 * (InMemory 는 파드별 분리라 다중 파드에서 부정확 — 그 공백을 메운다).
 *
 * <p>저장 모델
 *
 * <ul>
 *   <li>사용자 인덱스 ZSET {@code cs:u:{userId}} — member=sessionId, score=issuedAtEpochMs (정렬·오래된 것 선별).
 *   <li>세션 본문 {@code cs:s:{sessionId}} — {@code userId<US>sessionId<US>accessJti<US>refreshToken<US>issuedAt}
 *       (구분자 = 단위구분자 0x1F — 토큰/식별자에 나타나지 않음).
 * </ul>
 *
 * <p><b>원자성</b>: {@code register}(한도 판정 → 초과분 축출 → 신규 등록)을 <b>단일 Lua 스크립트</b>로 실행해
 * 다중 파드 동시 로그인 경합에서도 한도가 정확하다(JDBC 구현이 {@code @Transactional} 로 얻는 원자성과 동등).
 * {@code framework-lock} 의 Redis Lua CAS 와 같은 사상이다.
 *
 * <p>JDBC 구현과 동일하게 TTL 을 두지 않는다(세션은 {@code unregister}/축출로 정리). 노드 비정상 종료로 남는
 * 잔여 키는 다음 로그인 시 동일 사용자 한도 판정/축출로 수렴한다.
 */
public class RedisConcurrentSessionService implements ConcurrentSessionService {

    private static final String USER = "cs:u:";
    private static final String SESSION = "cs:s:";
    private static final char US = '\u001f'; // unit separator

    /**
     * KEYS[1]=userKey, ARGV: 1=max, 2=reject(1/0), 3=sessionId, 4=payload, 5=score, 6=sessionKeyPrefix.
     * 반환: 첫 원소가 상태("OK"|"REJECT"), 그 뒤로 축출된 세션 payload 목록.
     */
    private static final RedisScript<List> REGISTER = new DefaultRedisScript<>(
            "local userKey = KEYS[1]\n"
                    + "local max = tonumber(ARGV[1])\n"
                    + "local reject = tonumber(ARGV[2]) == 1\n"
                    + "local sid = ARGV[3]\n"
                    + "local payload = ARGV[4]\n"
                    + "local score = tonumber(ARGV[5])\n"
                    + "local sprefix = ARGV[6]\n"
                    + "local count = redis.call('ZCARD', userKey)\n"
                    + "local result = {'OK'}\n"
                    + "if count >= max then\n"
                    + "  if reject then return {'REJECT'} end\n"
                    + "  local toEvict = count - max + 1\n"
                    + "  local oldest = redis.call('ZRANGE', userKey, 0, toEvict - 1)\n" // 오름차순 = 오래된 것 먼저
                    + "  for i = 1, #oldest do\n"
                    + "    local osid = oldest[i]\n"
                    + "    local oval = redis.call('GET', sprefix .. osid)\n"
                    + "    if oval then table.insert(result, oval) end\n"
                    + "    redis.call('DEL', sprefix .. osid)\n"
                    + "    redis.call('ZREM', userKey, osid)\n"
                    + "  end\n"
                    + "end\n"
                    + "redis.call('ZADD', userKey, score, sid)\n"
                    + "redis.call('SET', sprefix .. sid, payload)\n"
                    + "return result\n",
            List.class);

    private final StringRedisTemplate redis;
    private final ConcurrentSessionProperties props;

    public RedisConcurrentSessionService(StringRedisTemplate redis, ConcurrentSessionProperties props) {
        this.redis = redis;
        this.props = props;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ActiveSession> register(ActiveSession session) {
        int max = Math.max(1, props.getMaxSessions());
        boolean reject = props.getStrategy() == ConcurrentSessionProperties.Strategy.REJECT;
        List<String> raw = redis.execute(
                REGISTER,
                Collections.singletonList(USER + session.userId()),
                String.valueOf(max),
                reject ? "1" : "0",
                session.sessionId(),
                encode(session),
                String.valueOf(session.issuedAtEpochMs()),
                SESSION);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if ("REJECT".equals(raw.get(0))) {
            throw new BusinessException(
                    ErrorCode.Common.CONFLICT, "이미 다른 기기/브라우저에서 로그인되어 있습니다. 기존 세션을 로그아웃한 뒤 다시 시도하세요.");
        }
        List<ActiveSession> evicted = new ArrayList<>();
        for (int i = 1; i < raw.size(); i++) {
            ActiveSession s = decode(raw.get(i));
            if (s != null) {
                evicted.add(s);
            }
        }
        return evicted;
    }

    @Override
    public void unregister(String sessionId) {
        if (sessionId == null) {
            return;
        }
        String payload = redis.opsForValue().get(SESSION + sessionId);
        if (payload != null) {
            ActiveSession s = decode(payload);
            if (s != null) {
                redis.opsForZSet().remove(USER + s.userId(), sessionId);
            }
        }
        redis.delete(SESSION + sessionId);
    }

    @Override
    public List<ActiveSession> activeSessions(String userId) {
        // score 내림차순(reverseRange) = 최신순 — JDBC 의 ORDER BY issued_at DESC 와 동일.
        java.util.Set<String> sids = redis.opsForZSet().reverseRange(USER + userId, 0, -1);
        List<ActiveSession> out = new ArrayList<>();
        if (sids == null) {
            return out;
        }
        for (String sid : sids) {
            String payload = redis.opsForValue().get(SESSION + sid);
            if (payload != null) {
                ActiveSession s = decode(payload);
                if (s != null) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    private static String encode(ActiveSession s) {
        return s.userId()
                + US
                + s.sessionId()
                + US
                + nullToEmpty(s.accessJti())
                + US
                + nullToEmpty(s.refreshToken())
                + US
                + s.issuedAtEpochMs();
    }

    private static ActiveSession decode(String payload) {
        String[] p = payload.split(String.valueOf(US), -1);
        if (p.length != 5) {
            return null;
        }
        long issuedAt;
        try {
            issuedAt = Long.parseLong(p[4]);
        } catch (NumberFormatException e) {
            return null;
        }
        return new ActiveSession(p[0], p[1], emptyToNull(p[2]), emptyToNull(p[3]), issuedAt);
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    private static String emptyToNull(String v) {
        return (v == null || v.isEmpty()) ? null : v;
    }
}
