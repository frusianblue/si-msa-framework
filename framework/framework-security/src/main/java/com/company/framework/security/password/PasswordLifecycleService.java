package com.company.framework.security.password;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 수명주기(만료 + 직전 N개 재사용 금지) — ISMS-P/보안성 심의 대응.
 * 프레임워크는 "정책 판정 + 이력 적재"만 제공하고, 회원가입/비밀번호변경 흐름은 각 서비스가 호출한다.
 *
 * 비밀번호 변경 흐름 예:
 *   policy.validate(raw);                       // 강도(기존)
 *   lifecycle.assertNotReused(userId, raw);     // 직전 N개 재사용 금지(신규)
 *   String enc = passwordEncoder.encode(raw);
 *   user.setPassword(enc);
 *   lifecycle.recordChange(userId, enc);        // 이력 적재 + 변경시각 갱신(신규)
 *
 * 로그인 직후/마이페이지 등에서:
 *   if (lifecycle.isExpired(userId)) -> 비밀번호 변경 화면으로 강제 유도
 *
 * 기능이 꺼져 있으면(enabled=false) 모든 메서드는 무해하게 통과/no-op 한다(기존 동작 보존).
 */
public class PasswordLifecycleService {

    private final PasswordHistoryStore historyStore;
    private final PasswordEncoder passwordEncoder;
    private final PasswordProperties props;

    public PasswordLifecycleService(
            PasswordHistoryStore historyStore, PasswordEncoder passwordEncoder, PasswordProperties props) {
        this.historyStore = historyStore;
        this.passwordEncoder = passwordEncoder;
        this.props = props;
    }

    /** 직전 N개 비밀번호 재사용 시 BusinessException(INVALID_INPUT) 을 던진다. */
    public void assertNotReused(String userId, String rawPassword) {
        if (!props.getHistory().isEnabled() || userId == null || rawPassword == null) return;
        int count = props.getHistory().getCount();
        for (String oldHash : historyStore.recentEncoded(userId, count)) {
            if (oldHash != null && passwordEncoder.matches(rawPassword, oldHash)) {
                throw new BusinessException(
                        ErrorCode.Common.INVALID_INPUT,
                        "최근 사용한 비밀번호 " + count + "개는 다시 사용할 수 없습니다.");
            }
        }
    }

    /** 비밀번호 변경 완료 후 호출. 이력 적재 + 변경시각 갱신. (history/expiry 중 하나라도 켜져 있으면 기록) */
    public void recordChange(String userId, String encodedPassword) {
        if (userId == null || encodedPassword == null) return;
        if (!props.getHistory().isEnabled() && !props.getExpiry().isEnabled()) return;
        historyStore.record(userId, encodedPassword, props.getHistory().getCount());
    }

    /** 만료(변경주기 초과) 여부. 만료 기능 off 또는 변경이력 없음(미설정)이면 false. */
    public boolean isExpired(String userId) {
        if (!props.getExpiry().isEnabled() || userId == null) return false;
        Optional<Instant> last = historyStore.lastChangedAt(userId);
        if (last.isEmpty()) return false; // 이력 미적재 사용자는 강제하지 않음(레거시 사용자 보호)
        return Instant.now().isAfter(last.get().plus(props.getExpiry().getMaxAge()));
    }

    /** 만료 예정 시각. 이력/만료 미설정이면 empty. */
    public Optional<Instant> expiresAt(String userId) {
        if (!props.getExpiry().isEnabled() || userId == null) return Optional.empty();
        return historyStore.lastChangedAt(userId).map(t -> t.plus(props.getExpiry().getMaxAge()));
    }

    /** 만료 임박(경고 구간 진입) 여부. 클라이언트에 '곧 만료' 배너를 띄우는 용도. */
    public boolean isExpiringSoon(String userId) {
        Optional<Instant> exp = expiresAt(userId);
        if (exp.isEmpty()) return false;
        Instant warnFrom = exp.get().minus(props.getExpiry().getWarnBefore());
        Instant now = Instant.now();
        return !now.isBefore(warnFrom) && now.isBefore(exp.get());
    }

    /** 만료까지 남은 기간(이미 만료/미설정이면 empty). */
    public Optional<Duration> remainingUntilExpiry(String userId) {
        Optional<Instant> exp = expiresAt(userId);
        if (exp.isEmpty()) return Optional.empty();
        Duration remaining = Duration.between(Instant.now(), exp.get());
        return remaining.isNegative() ? Optional.empty() : Optional.of(remaining);
    }
}
