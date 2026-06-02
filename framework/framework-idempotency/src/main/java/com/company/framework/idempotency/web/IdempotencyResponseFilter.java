package com.company.framework.idempotency.web;

import com.company.framework.idempotency.config.IdempotencyProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * 재생(replay) 모드에서만 등록되는 응답 캡처 필터. Idempotency-Key 헤더가 있는 요청에 한해 응답을
 * {@link ContentCachingResponseWrapper}로 감싸 인터셉터({@code afterCompletion})가 본문을 읽을 수 있게 한다.
 *
 * <p>헤더 없는 트래픽은 감싸지 않으므로 버퍼링 비용 0. {@code @Order(LOWEST_PRECEDENCE)}로 가장 안쪽에 두어
 * secure-web 의 요청 필터(HIGHEST_PRECEDENCE+n)들 뒤, 디스패처 직전에서 응답을 감싼다(디스패처가 이 래퍼에 기록).
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class IdempotencyResponseFilter extends OncePerRequestFilter {

    private final IdempotencyProperties props;

    public IdempotencyResponseFilter(IdempotencyProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(props.getHeader());
        if (key == null || key.isBlank()) {
            chain.doFilter(request, response); // 멱등 대상 아님 → 그대로 통과(버퍼링 없음)
            return;
        }
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, wrapped);
        } finally {
            // 버퍼에 모인 본문을 실제 응답으로 복사(필수). 재생/정상 처리 모두 이 시점에 클라이언트로 전송된다.
            wrapped.copyBodyToResponse();
        }
    }
}
