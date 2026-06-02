package com.company.framework.saga;

import java.util.function.Supplier;

/**
 * 오케스트레이터의 상태변경+커맨드 발행을 한 트랜잭션으로 묶기 위한 실행기.
 * 기본 구현은 Spring TransactionTemplate 기반. (Spring 의존을 코어에서 분리)
 */
@FunctionalInterface
public interface SagaTransactionRunner {

    <T> T run(Supplier<T> work);

    default void runWithoutResult(Runnable work) {
        run(() -> {
            work.run();
            return null;
        });
    }
}
