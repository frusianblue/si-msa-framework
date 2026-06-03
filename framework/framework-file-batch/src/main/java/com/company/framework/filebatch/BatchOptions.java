package com.company.framework.filebatch;

/**
 * 일괄처리 실행 옵션(불변).
 *
 * @param parallelism     동시 처리 상한(가상스레드는 무제한이라 Semaphore 로 디스크/스토리지 보호). 1 미만은 1로 보정.
 * @param continueOnError true(기본)면 한 건 실패해도 나머지를 계속 처리(부분 실패 격리). false 면 fail-fast.
 * @param dryRun          true 면 실제 IO 없이 계획만 산출.
 */
public record BatchOptions(int parallelism, boolean continueOnError, boolean dryRun) {

    /** 기본 동시도. */
    public static final int DEFAULT_PARALLELISM = 16;

    public BatchOptions {
        if (parallelism < 1) {
            parallelism = 1;
        }
    }

    /** 기본: 동시도 16, 부분 실패 격리(continueOnError), 실제 실행. */
    public static BatchOptions defaults() {
        return new BatchOptions(DEFAULT_PARALLELISM, true, false);
    }

    public BatchOptions withParallelism(int value) {
        return new BatchOptions(value, continueOnError, dryRun);
    }

    public BatchOptions withContinueOnError(boolean value) {
        return new BatchOptions(parallelism, value, dryRun);
    }

    public BatchOptions withDryRun(boolean value) {
        return new BatchOptions(parallelism, continueOnError, value);
    }
}
