package com.company.framework.client.core;

/**
 * 연속 실패 기반 경량 서킷브레이커(호스트 단위). 스레드세이프(synchronized).
 * CLOSED → (연속실패 threshold) → OPEN → (wait 경과) → HALF_OPEN → (성공) CLOSED / (실패) OPEN.
 * 외부 라이브러리 없이 동작. 더 정교한 정책이 필요하면 Resilience4j 인터셉터로 교체(override) 가능.
 */
public class CircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final long waitMillis;
    private final int halfOpenMaxCalls;

    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private long openedAt = 0L;
    private int halfOpenInFlight = 0;

    public CircuitBreaker(int failureThreshold, long waitMillis, int halfOpenMaxCalls) {
        this.failureThreshold = failureThreshold;
        this.waitMillis = waitMillis;
        this.halfOpenMaxCalls = Math.max(1, halfOpenMaxCalls);
    }

    /** 호출 직전 게이트. 통과 못 하면 false(차단). */
    public synchronized boolean tryAcquire() {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - openedAt >= waitMillis) {
                state = State.HALF_OPEN;
                halfOpenInFlight = 0;
            } else {
                return false;
            }
        }
        if (state == State.HALF_OPEN) {
            if (halfOpenInFlight >= halfOpenMaxCalls) {
                return false;
            }
            halfOpenInFlight++;
        }
        return true;
    }

    public synchronized void onSuccess() {
        consecutiveFailures = 0;
        if (state == State.HALF_OPEN) {
            halfOpenInFlight = Math.max(0, halfOpenInFlight - 1);
        }
        state = State.CLOSED;
    }

    public synchronized void onFailure() {
        consecutiveFailures++;
        if (state == State.HALF_OPEN) {
            halfOpenInFlight = Math.max(0, halfOpenInFlight - 1);
            trip();
        } else if (consecutiveFailures >= failureThreshold) {
            trip();
        }
    }

    private void trip() {
        state = State.OPEN;
        openedAt = System.currentTimeMillis();
    }

    public synchronized State state() {
        return state;
    }
}
