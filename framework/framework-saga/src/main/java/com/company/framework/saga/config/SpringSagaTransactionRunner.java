package com.company.framework.saga.config;

import com.company.framework.saga.SagaTransactionRunner;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link SagaTransactionRunner} 의 Spring 구현. 오케스트레이터의 상태변경+Outbox 적재를 한 트랜잭션으로 묶는다.
 * 기본 전파(REQUIRED): 리스너에서 호출되면 새 트랜잭션을 연다.
 */
public class SpringSagaTransactionRunner implements SagaTransactionRunner {

    private final TransactionTemplate template;

    public SpringSagaTransactionRunner(PlatformTransactionManager transactionManager) {
        this.template = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T run(Supplier<T> work) {
        return template.execute(status -> work.get());
    }
}
