package com.company.framework.context.client;

import com.company.framework.context.ContextHolder;
import com.company.framework.context.RequestContext;
import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * 현재 {@link RequestContext} 의 tenantId/userId 를 하위 호출에 헤더로 전파한다(요청 단위 컨텍스트 일관성).
 *
 * <p>{@code framework-client} 의 트레이스 전파 인터셉터와 같은 패턴. 이미 같은 헤더가 있으면 덮어쓰지 않는다.
 * 사용처의 {@code RestClient}/{@code RestTemplate} 인터셉터 목록에 추가해 쓴다.
 */
public class ContextPropagationInterceptor implements ClientHttpRequestInterceptor {

    private final String tenantHeader;
    private final String userHeader;

    public ContextPropagationInterceptor(String tenantHeader, String userHeader) {
        this.tenantHeader = tenantHeader;
        this.userHeader = userHeader;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        RequestContext ctx = ContextHolder.get();
        addIfAbsent(request, tenantHeader, ctx.tenantId());
        addIfAbsent(request, userHeader, ctx.userId());
        return execution.execute(request, body);
    }

    private static void addIfAbsent(HttpRequest request, String header, String value) {
        if (value != null && !request.getHeaders().containsHeader(header)) {
            request.getHeaders().add(header, value);
        }
    }
}
