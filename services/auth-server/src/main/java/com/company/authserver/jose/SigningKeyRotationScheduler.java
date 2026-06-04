package com.company.authserver.jose;

import com.company.framework.lock.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 서명키 회전 스케줄 트리거(얇은 래퍼). 실제 작업은 {@link SigningKeyRotationService#rotateOnce()} 가 한 트랜잭션으로 수행한다.
 *
 * <p><b>리더 선출</b>: {@code @SchedulerLock} 이 다중 파드 중 한 파드만 회전하게 강제한다(framework-lock). 메서드는 반드시 {@code void}
 * (락 스킵 시 반환값 없음). {@code @SchedulerLock} 과 {@code @Transactional} 의 어드바이저 순서 다툼을 피하려고, 락은 이 메서드(트랜잭션
 * 미적용)에 걸고 트랜잭션은 호출 대상 서비스 메서드에 두어 "락 획득 → 트랜잭션 시작" 순서를 보장한다.
 *
 * <p><b>전제</b>: {@code @SchedulerLock} 가 동작하려면 {@code framework.lock.enabled=true} 로 애스펙트가 등록돼야 한다. 누락 시
 * 애너테이션이 무시되어 <b>모든 파드가 회전</b>하므로(경합), 운영 다중 파드에서는 lock(type=jdbc|redis) 활성이 필수다.
 * 또한 {@code @Scheduled} 가 돌려면 {@code @EnableScheduling}(AuthServerApplication)이 필요하다.
 */
public class SigningKeyRotationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SigningKeyRotationScheduler.class);

    private final SigningKeyRotationService service;

    public SigningKeyRotationScheduler(SigningKeyRotationService service) {
        this.service = service;
    }

    @Scheduled(cron = "${auth-server.signing-key.rotation.cron:0 0 4 1 * *}")
    @SchedulerLock(name = "auth-signing-key-rotation", atMostFor = "5m", atLeastFor = "1m")
    public void rotate() {
        try {
            SigningKeyRotationService.Outcome outcome = service.rotateOnce();
            if (!outcome.rotated()) {
                log.debug("[signing-key] 회전 트리거 — 멱등 스킵. activeKid={}", outcome.activeKid());
            }
        } catch (RuntimeException e) {
            // 다음 트리거에서 재시도. 회전 실패가 토큰 발급/검증을 즉시 깨지는 않는다(기존 ACTIVE 유지).
            log.error("[signing-key] 회전 실패 — 다음 주기 재시도.", e);
        }
    }
}
