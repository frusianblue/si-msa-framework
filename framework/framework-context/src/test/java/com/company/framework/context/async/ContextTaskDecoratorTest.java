package com.company.framework.context.async;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.context.ContextHolder;
import com.company.framework.context.RequestContext;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

@DisplayName("ContextTaskDecorator 비동기 전파")
class ContextTaskDecoratorTest {

    private final ContextTaskDecorator decorator = new ContextTaskDecorator();

    @AfterEach
    void cleanup() {
        ContextHolder.clear();
        MDC.clear();
    }

    @Test
    @DisplayName("제출 스레드의 컨텍스트와 MDC 를 작업 스레드로 전파한다")
    void propagatesContextAndMdc() throws Exception {
        ContextHolder.set(
                RequestContext.builder().tenantId("acme").userId("u-1").build());
        MDC.put("traceId", "t-123");

        String[] seen = new String[3];
        Runnable decorated = decorator.decorate(() -> {
            seen[0] = ContextHolder.get().tenantId();
            seen[1] = ContextHolder.get().userId();
            seen[2] = MDC.get("traceId");
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(decorated).get();
        } finally {
            pool.shutdown();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }

        assertThat(seen[0]).isEqualTo("acme");
        assertThat(seen[1]).isEqualTo("u-1");
        assertThat(seen[2]).isEqualTo("t-123");
    }

    @Test
    @DisplayName("작업 종료 후 풀 스레드의 원래 상태로 되돌린다(누수 방지)")
    void restoresWorkerThreadAfterRun() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // 1차: 컨텍스트 있는 작업 실행
            ContextHolder.set(RequestContext.builder().tenantId("acme").build());
            MDC.put("traceId", "t-1");
            pool.submit(decorator.decorate(() -> {})).get();

            // 2차: 컨텍스트 없는 평범한 작업 — 1차 잔재가 남아 있으면 안 됨
            boolean[] leaked = {true};
            String[] mdcLeak = {"x"};
            pool.submit(() -> {
                        leaked[0] = ContextHolder.get().hasTenant();
                        mdcLeak[0] = MDC.get("traceId");
                    })
                    .get();

            assertThat(leaked[0]).as("컨텍스트 누수").isFalse();
            assertThat(mdcLeak[0]).as("MDC 누수").isNull();
        } finally {
            pool.shutdown();
            pool.awaitTermination(2, TimeUnit.SECONDS);
        }
    }
}
