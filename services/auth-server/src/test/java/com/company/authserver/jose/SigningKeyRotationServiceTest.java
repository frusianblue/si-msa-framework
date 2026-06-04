package com.company.authserver.jose;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 회전 오케스트레이션 순수 단위 검증. Nimbus/암호화/스케줄/락을 배제하고(인메모리 매퍼 스텁 + 스텁 생성기),
 * 회전 로직 자체("RETIRE 먼저 → INSERT", grace 정리, 멱등 스킵)만 본다.
 */
class SigningKeyRotationServiceTest {

    /** 인메모리 매퍼 스텁(MyBatis 없이 회전 로직만 검증). */
    static final class InMemoryMapper implements SigningKeyMapper {
        final List<SigningKey> rows = new ArrayList<>();

        @Override
        public List<SigningKey> findAllUsable() {
            List<SigningKey> copy = new ArrayList<>(rows);
            copy.sort(Comparator.comparing(SigningKey::createdAt).reversed());
            return copy;
        }

        @Override
        public SigningKey findNewestActive() {
            return rows.stream()
                    .filter(SigningKey::isActive)
                    .max(Comparator.comparing(SigningKey::createdAt))
                    .orElse(null);
        }

        @Override
        public int insert(SigningKey key) {
            rows.add(key);
            return 1;
        }

        @Override
        public int updateStatus(String kid, String status) {
            int n = 0;
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).kid().equals(kid)) {
                    SigningKey r = rows.get(i);
                    rows.set(i, new SigningKey(r.kid(), r.jwkJson(), status, r.createdAt(), r.retiredAt()));
                    n++;
                }
            }
            return n;
        }

        @Override
        public int retireAllActive(Instant retiredAt) {
            int n = 0;
            for (int i = 0; i < rows.size(); i++) {
                SigningKey r = rows.get(i);
                if (r.isActive()) {
                    rows.set(i, new SigningKey(r.kid(), r.jwkJson(), SigningKey.RETIRED, r.createdAt(), retiredAt));
                    n++;
                }
            }
            return n;
        }

        @Override
        public int deleteRetiredOlderThan(Instant cutoff) {
            int before = rows.size();
            rows.removeIf(r -> SigningKey.RETIRED.equals(r.status())
                    && r.retiredAt() != null
                    && r.retiredAt().isBefore(cutoff));
            return before - rows.size();
        }
    }

    /** 매번 새 kid 의 ACTIVE 키를 만드는 스텁 생성기(키 본문은 의미 없음). */
    static final SigningKeyGenerator STUB_GEN =
            () -> SigningKey.active(UUID.randomUUID().toString(), "{stub-jwk}", Instant.now());

    private InMemoryMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new InMemoryMapper();
    }

    @Test
    @DisplayName("회전: 직전 ACTIVE 가 RETIRE 되고 새 ACTIVE 가 정확히 1개 남는다")
    void rotate_retiresPreviousAndKeepsOneActive() {
        // 부트스트랩 ACTIVE 1개(40일 전 생성) — 멱등 가드(1h)에 안 걸리도록 충분히 과거.
        SigningKey old = SigningKey.active("old", "{old}", Instant.now().minus(Duration.ofDays(40)));
        mapper.insert(old);

        var svc = new SigningKeyRotationService(mapper, STUB_GEN, Duration.ofDays(14), Duration.ofHours(1));
        var outcome = svc.rotateOnce();

        assertThat(outcome.rotated()).isTrue();
        long active = mapper.rows.stream().filter(SigningKey::isActive).count();
        assertThat(active).isEqualTo(1);
        assertThat(outcome.retiredCount()).isEqualTo(1);
        // 직전 키는 RETIRED + retired_at 기록.
        SigningKey retired = mapper.rows.stream()
                .filter(r -> r.kid().equals("old"))
                .findFirst()
                .orElseThrow();
        assertThat(retired.status()).isEqualTo(SigningKey.RETIRED);
        assertThat(retired.retiredAt()).isNotNull();
    }

    @Test
    @DisplayName("멱등 가드: 최근(min-interval 이내) ACTIVE 가 있으면 회전 스킵")
    void rotate_skipsWhenRecentActiveExists() {
        mapper.insert(SigningKey.active("fresh", "{fresh}", Instant.now())); // 방금 생성

        var svc = new SigningKeyRotationService(mapper, STUB_GEN, Duration.ofDays(14), Duration.ofHours(1));
        var outcome = svc.rotateOnce();

        assertThat(outcome.rotated()).isFalse();
        assertThat(mapper.rows).hasSize(1); // 새 키 안 생김
        assertThat(mapper.findNewestActive().kid()).isEqualTo("fresh");
    }

    @Test
    @DisplayName("grace 정리: 폐기된 지 grace 지난 RETIRED 만 삭제, 갓 폐기된 키는 오버랩 위해 유지")
    void rotate_purgesOnlyKeysRetiredBeyondGrace() {
        // 20일 전 폐기된 RETIRED(grace 14d 초과 → 정리 대상).
        mapper.insert(new SigningKey(
                "veryOld", "{}", SigningKey.RETIRED,
                Instant.now().minus(Duration.ofDays(60)),
                Instant.now().minus(Duration.ofDays(20))));
        // 직전 ACTIVE(40일 전 생성) — 이번 회전에서 RETIRE 되며 retired_at=now → 유지돼야 함.
        mapper.insert(SigningKey.active("prev", "{}", Instant.now().minus(Duration.ofDays(40))));

        var svc = new SigningKeyRotationService(mapper, STUB_GEN, Duration.ofDays(14), Duration.ofHours(1));
        var outcome = svc.rotateOnce();

        assertThat(outcome.rotated()).isTrue();
        assertThat(outcome.purgedCount()).isEqualTo(1); // veryOld 삭제
        List<String> kids = mapper.rows.stream().map(SigningKey::kid).toList();
        assertThat(kids).doesNotContain("veryOld"); // grace 초과 → 삭제
        assertThat(kids).contains("prev"); // 갓 폐기 → 오버랩 유지
        // 결과: 새 ACTIVE 1 + 갓 RETIRE 된 prev = 2건.
        assertThat(mapper.rows).hasSize(2);
    }
}
