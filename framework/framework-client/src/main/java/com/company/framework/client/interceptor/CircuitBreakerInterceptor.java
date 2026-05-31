package com.company.framework.client.interceptor;

import com.company.framework.client.config.ClientProperties;
import com.company.framework.client.core.CircuitBreaker;
import com.company.framework.client.core.CircuitOpenException;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** 호스트 단위 서킷브레이커. IOException/5xx 를 실패로 집계. OPEN 이면 CircuitOpenException 으로 차단. */
public class CircuitBreakerInterceptor implements ClientHttpRequestInterceptor {

    private final ClientProperties.CircuitBreakerProps props;
    private final Map<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    public CircuitBreakerInterceptor(ClientProperties.CircuitBreakerProps props) {
        this.props = props;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String host = request.getURI().getHost();
        CircuitBreaker cb = breakers.computeIfAbsent(host, h -> new CircuitBreaker(
                props.getFailureThreshold(), props.getWaitDuration().toMillis(), props.getHalfOpenMaxCalls()));

        if (!cb.tryAcquire()) {
            throw new CircuitOpenException(host);
        }
        try {
            ClientHttpResponse response = execution.execute(request, body);
            if (response.getStatusCode().is5xxServerError()) {
                cb.onFailure();
            } else {
                cb.onSuccess();
            }
            return response;
        } catch (IOException e) {
            cb.onFailure();
            throw e;
        }
    }
}
