package com.company.framework.client.interceptor;

import com.company.framework.client.config.ClientProperties;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * 연계 호출 로그: 메서드/URI/상태/소요(ms). 민감 헤더는 *** 로 가린다.
 * 본문 로깅은 기본 off(민감정보/성능). 로거: framework.client.integration
 */
public class IntegrationLoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger("framework.client.integration");

    private final ClientProperties.Logging props;

    public IntegrationLoggingInterceptor(ClientProperties.Logging props) {
        this.props = props;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        long start = System.nanoTime();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("[OUT] {} {} -> {} ({}ms){}", request.getMethod(), request.getURI(),
                    response.getStatusCode().value(), ms, headersFor(request));
            return response;
        } catch (IOException e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.warn("[OUT] {} {} -> FAIL ({}ms): {}", request.getMethod(), request.getURI(), ms, e.toString());
            throw e;
        }
    }

    private String headersFor(HttpRequest request) {
        if (!props.isIncludeHeaders()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" headers={");
        request.getHeaders().forEach((name, values) -> {
            boolean masked = props.getMaskedHeaders().stream().anyMatch(name::equalsIgnoreCase);
            sb.append(name).append("=").append(masked ? "***" : String.join(",", values)).append(" ");
        });
        return sb.append("}").toString();
    }

}
