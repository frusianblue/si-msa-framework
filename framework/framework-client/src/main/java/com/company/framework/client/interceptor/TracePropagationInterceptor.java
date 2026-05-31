package com.company.framework.client.interceptor;

import com.company.framework.core.trace.MdcTraceFilter;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** MDC traceId 를 하위 호출에 X-Trace-Id 헤더로 전파(요청 단위 추적 일관성). */
public class TracePropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String traceId = MDC.get(MdcTraceFilter.TRACE_ID);
        if (traceId != null && !request.getHeaders().containsKey(MdcTraceFilter.TRACE_HEADER)) {
            request.getHeaders().add(MdcTraceFilter.TRACE_HEADER, traceId);
        }
        return execution.execute(request, body);
    }
}
