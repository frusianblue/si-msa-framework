package com.company.framework.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ContextHolder ThreadLocal 다리")
class ContextHolderTest {

    @AfterEach
    void cleanup() {
        ContextHolder.clear();
    }

    @Test
    @DisplayName("미바인딩 시 EMPTY 를 반환(절대 null 아님)")
    void returnsEmptyWhenUnbound() {
        assertThat(ContextHolder.get()).isSameAs(RequestContext.EMPTY);
    }

    @Test
    @DisplayName("set/get 으로 현재 스레드에 바인딩한다")
    void setAndGet() {
        RequestContext ctx = RequestContext.builder().tenantId("acme").build();
        ContextHolder.set(ctx);
        assertThat(ContextHolder.get()).isSameAs(ctx);
    }

    @Test
    @DisplayName("null 을 set 하면 EMPTY 로 정규화된다")
    void setNullNormalizesToEmpty() {
        ContextHolder.set(null);
        assertThat(ContextHolder.get()).isSameAs(RequestContext.EMPTY);
    }

    @Test
    @DisplayName("clear 후에는 다시 EMPTY")
    void clearResets() {
        ContextHolder.set(RequestContext.builder().userId("u-1").build());
        ContextHolder.clear();
        assertThat(ContextHolder.get()).isSameAs(RequestContext.EMPTY);
    }

    @Test
    @DisplayName("상속형이 아니라 다른 스레드에는 전파되지 않는다")
    void notInheritedToOtherThread() throws InterruptedException {
        ContextHolder.set(RequestContext.builder().tenantId("acme").build());
        boolean[] childSawTenant = {true};
        Thread t = new Thread(() -> childSawTenant[0] = ContextHolder.get().hasTenant());
        t.start();
        t.join();
        assertThat(childSawTenant[0]).isFalse();
    }
}
