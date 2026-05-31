package com.company.framework.client.interceptor;

import com.company.framework.client.config.ClientProperties;
import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * 재시도: 멱등 메서드(GET/HEAD/PUT/DELETE/OPTIONS 기본)에 한해 IOException/지정 5xx 에서 재시도.
 * 지수 백오프(backoff * multiplier^n). POST 는 기본 비재시도(부작용 방지).
 */
public class RetryInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RetryInterceptor.class);

    private final ClientProperties.Retry props;
    private final Set<HttpMethod> retryableMethods;
    private final Set<Integer> retryableStatuses;

    public RetryInterceptor(ClientProperties.Retry props) {
        this.props = props;
        this.retryableMethods =
                props.getMethods().stream().map(HttpMethod::valueOf).collect(java.util.stream.Collectors.toSet());
        this.retryableStatuses = Set.copyOf(props.getRetryOnStatus());
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!retryableMethods.contains(request.getMethod())) {
            return execution.execute(request, body);
        }
        int attempt = 0;
        while (true) {
            try {
                ClientHttpResponse response = execution.execute(request, body);
                if (attempt < props.getMaxAttempts()
                        && retryableStatuses.contains(response.getStatusCode().value())) {
                    response.close();
                    sleepBackoff(++attempt, request);
                    continue;
                }
                return response;
            } catch (IOException e) {
                if (attempt < props.getMaxAttempts()) {
                    sleepBackoff(++attempt, request);
                    continue;
                }
                throw e;
            }
        }
    }

    private void sleepBackoff(int attempt, HttpRequest request) throws IOException {
        long delay = (long) (props.getBackoff().toMillis() * Math.pow(props.getMultiplier(), attempt - 1.0));
        log.debug("[retry] {} {} attempt={} delay={}ms", request.getMethod(), request.getURI(), attempt, delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("retry interrupted", ie);
        }
    }
}
