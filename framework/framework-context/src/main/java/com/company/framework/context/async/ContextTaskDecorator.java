package com.company.framework.context.async;

import com.company.framework.context.ContextHolder;
import com.company.framework.context.RequestContext;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * {@code @Async}/스레드풀 작업에 호출 시점의 {@link RequestContext} 와 MDC 를 전파한다.
 *
 * <p>제출 스레드의 스냅샷을 캡처해 작업 스레드에서 복원하고, 작업 종료 후 작업 스레드의 원래 상태로 되돌린다
 * (풀 스레드 재사용 시 누수 방지). core 의 가상 스레드 실행기에 적용하려면 실행기에
 * {@code setTaskDecorator(contextTaskDecorator)} 로 연결한다(README 참조).
 */
public class ContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        RequestContext capturedContext = ContextHolder.get();
        Map<String, String> capturedMdc = MDC.getCopyOfContextMap();

        return () -> {
            RequestContext previousContext = ContextHolder.get();
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();

            ContextHolder.set(capturedContext);
            setMdc(capturedMdc);
            try {
                runnable.run();
            } finally {
                ContextHolder.set(previousContext);
                setMdc(previousMdc);
            }
        };
    }

    private static void setMdc(Map<String, String> map) {
        if (map != null) {
            MDC.setContextMap(map);
        } else {
            MDC.clear();
        }
    }
}
