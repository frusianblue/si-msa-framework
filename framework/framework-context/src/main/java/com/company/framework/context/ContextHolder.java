package com.company.framework.context;

/**
 * 현재 스레드의 {@link RequestContext} 를 보관하는 정적 다리(ThreadLocal).
 *
 * <p><b>설계상 {@code InheritableThreadLocal} 을 쓰지 않는다.</b> 가상 스레드/스레드풀 환경에서 상속형
 * ThreadLocal 은 컨텍스트 누수를 일으키므로, 다른 스레드로의 전파는 반드시 명시적으로 한다:
 *
 * <ul>
 *   <li>{@code @Async}/풀 → {@code ContextTaskDecorator}(컨텍스트+MDC 스냅샷 복원).
 *   <li>아웃바운드 HTTP → {@code ContextPropagationInterceptor}(헤더 전파).
 * </ul>
 *
 * <p>요청 스레드에서는 {@code ContextBindingFilter} 가 진입 시 {@link #set} 하고 종료 시 {@link #clear} 한다.
 */
public final class ContextHolder {

    private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();

    private ContextHolder() {}

    /** 현재 컨텍스트(미바인딩이면 {@link RequestContext#EMPTY}). 절대 null 을 반환하지 않는다. */
    public static RequestContext get() {
        RequestContext ctx = CONTEXT.get();
        return ctx != null ? ctx : RequestContext.EMPTY;
    }

    /** 현재 스레드에 컨텍스트를 바인딩(null 이면 {@link RequestContext#EMPTY}). */
    public static void set(RequestContext context) {
        CONTEXT.set(context != null ? context : RequestContext.EMPTY);
    }

    /** 현재 스레드의 컨텍스트를 제거(요청 종료/작업 종료 시 필수). */
    public static void clear() {
        CONTEXT.remove();
    }
}
