package com.company.authserver.jose;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서명키 회전 오케스트레이션(스케줄/락과 분리된 순수 작업 단위). {@link SigningKeyRotationScheduler} 가 리더 선출 후 호출한다.
 *
 * <p><b>순서(한 트랜잭션 · 원자적)</b>: ① 직전 ACTIVE 전부 RETIRE → ② 새 ACTIVE INSERT → ③ grace 지난 RETIRED 정리.
 * "RETIRE 먼저 → INSERT" 순서가 핵심: 독자(JWKS)는 트랜잭션 커밋 전/후만 보므로 0-ACTIVE 중간상태가 노출되지 않고,
 * 락이 빠진 경합 상황의 최악도 ACTIVE 2개(오버랩이 흡수)라 <b>서명 불가(0-ACTIVE)로는 절대 떨어지지 않는다</b>.
 * (INSERT 먼저 → 남은 ACTIVE RETIRE 순서는 두 파드가 서로의 새 키를 RETIRE 해 0-ACTIVE 가 될 수 있어 채택하지 않음.)
 *
 * <p><b>멱등 가드</b>: 직전 ACTIVE 가 {@code minInterval} 안에 생성됐으면 스킵(수동 중복 트리거/오작동 2차 안전망). 다중 파드
 * 중복 회전의 1차 방지는 {@code @SchedulerLock}(리더 선출)이며, 운영 다중 파드에서는 {@code framework.lock.enabled=true} +
 * {@code type=jdbc|redis} 가 <b>필수</b>다(memory 는 파드 간 상호배제 불가).
 *
 * <p><b>grace 기준 = retired_at</b>: {@link SigningKeyMapper#deleteRetiredOlderThan}는 RETIRE 시각 기준으로 정리한다(생성 시각 아님).
 * grace ≥ (access 토큰 최대 수명 + JWKS 캐시 TTL + 여유) 여야 회전 직후 발급 토큰이 오버랩 안에서 검증된다.
 */
@Transactional
public class SigningKeyRotationService {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyRotationService.class);

    private final SigningKeyMapper mapper;
    private final SigningKeyGenerator generator;
    private final Duration retireGrace;
    private final Duration minInterval;

    public SigningKeyRotationService(
            SigningKeyMapper mapper, SigningKeyGenerator generator, Duration retireGrace, Duration minInterval) {
        this.mapper = mapper;
        this.generator = generator;
        this.retireGrace = retireGrace;
        this.minInterval = minInterval;
    }

    /** 회전 1회 수행(또는 멱등 가드로 스킵). 반환값으로 결과 요약. */
    public Outcome rotateOnce() {
        Instant now = Instant.now();

        SigningKey newest = mapper.findNewestActive();
        if (newest != null && newest.createdAt() != null && newest.createdAt().isAfter(now.minus(minInterval))) {
            log.info("[signing-key] 회전 스킵 — 최근({}) 내 ACTIVE 존재. kid={}", minInterval, newest.kid());
            return Outcome.skipped(newest.kid());
        }

        SigningKey fresh = generator.generateActive(); // 키 생성 + 개인키 보호(저장형)
        int retired = mapper.retireAllActive(now); // ① 직전 ACTIVE 전부 RETIRE(retired_at=now)
        mapper.insert(fresh); // ② 새 ACTIVE
        int purged = mapper.deleteRetiredOlderThan(now.minus(retireGrace)); // ③ grace 지난 RETIRED 정리

        log.info(
                "[signing-key] 회전 완료. newKid={} retired={} purged={} grace={}",
                fresh.kid(),
                retired,
                purged,
                retireGrace);
        return Outcome.rotated(fresh.kid(), retired, purged);
    }

    /** 회전 결과 요약(로깅/테스트용). */
    public record Outcome(boolean rotated, String activeKid, int retiredCount, int purgedCount) {

        static Outcome skipped(String activeKid) {
            return new Outcome(false, activeKid, 0, 0);
        }

        static Outcome rotated(String activeKid, int retiredCount, int purgedCount) {
            return new Outcome(true, activeKid, retiredCount, purgedCount);
        }
    }
}
