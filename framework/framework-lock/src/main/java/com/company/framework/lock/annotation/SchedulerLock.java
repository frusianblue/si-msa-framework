package com.company.framework.lock.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 다중 파드/인스턴스에서 메서드(주로 스프링 {@code @Scheduled})를 "한 번에 한 곳에서만" 실행하도록 강제한다. k8s 다중 replica
 * 에서 매 트리거마다 모든 파드가 동시에 도는 중복 실행을 막는 용도.
 *
 * <pre>{@code
 * @Scheduled(cron = "0 0 2 * * *")
 * @SchedulerLock(name = "nightlySettlement", atMostFor = "10m", atLeastFor = "30s")
 * public void settle() { ... }
 * }</pre>
 *
 * <p>동작: 트리거 시 {@code DistributedLock} 으로 {@code name} 락을 잡는다. 잡으면 메서드를 실행하고, 못 잡으면(다른 파드가
 * 이미 실행 중) 조용히 스킵한다. 적용 메서드는 {@code void} 여야 한다(스킵 시 반환값이 없으므로).
 *
 * <p><b>batch 모듈과의 차이</b>: Spring Batch + Quartz 잡은 {@code spring.quartz.job-store-type=jdbc} 클러스터링으로
 * 중복을 막는다. 본 애너테이션은 Quartz 가 아닌 <i>평범한 {@code @Scheduled}</i> 메서드의 중복방지 갭을 메운다.
 *
 * <p>의미(ShedLock 동등):
 *
 * <ul>
 *   <li><b>atMostFor</b>: 락 리스 상한. 보유 인스턴스가 죽어도 이 시간 후 자동 해제(교착 방지). 메서드 예상 실행시간보다 넉넉히.
 *       비우면 {@code framework.lock.default-at-most-for}(기본 5분).
 *   <li><b>atLeastFor</b>: 최소 보유. 메서드가 더 빨리 끝나도 이 시간까지 락을 유지한다. 트리거 시각의 파드 간 클럭 스큐로 인한
 *       직후 재실행을 막는다. 비우면 {@code framework.lock.default-at-least-for}(기본 0 = 끝나는 즉시 해제).
 * </ul>
 *
 * <p>기간 표기는 단순형(예: {@code "30s"}, {@code "10m"}, {@code "500ms"}) 또는 ISO-8601({@code "PT10M"}) 모두 허용.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SchedulerLock {

    /** 락 이름. 비우면 {@code 선언타입.메서드명} 으로 자동 생성. 동일 작업을 가리키는 여러 메서드는 같은 이름을 쓴다. */
    String name() default "";

    /** 락 리스 상한(자동 해제 시각). 비우면 {@code framework.lock.default-at-most-for}. */
    String atMostFor() default "";

    /** 최소 보유 시간(빨리 끝나도 유지). 비우면 {@code framework.lock.default-at-least-for}. */
    String atLeastFor() default "";
}
