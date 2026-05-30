package com.company.framework.idempotency.web;

import com.company.framework.idempotency.config.IdempotencyProperties;
import com.company.framework.idempotency.core.Idempotent;
import com.company.framework.idempotency.store.IdempotencyStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * @Idempotent 메서드에 한해 Idempotency-Key 헤더로 중복요청을 차단한다.
 * - 키 없음        → 400 (헤더 필수)
 * - 최초 선점       → 통과(정상 처리)
 * - 이미 처리/처리중 → 409 (중복) — 운영 정책에 맞게 저장 결과 재생으로 확장 가능
 */
public class IdempotencyInterceptor implements HandlerInterceptor {

    private final IdempotencyStore store;
    private final IdempotencyProperties props;

    public IdempotencyInterceptor(IdempotencyStore store, IdempotencyProperties props) {
        this.store = store;
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod hm) || !hm.hasMethodAnnotation(Idempotent.class)) {
            return true;
        }
        String key = req.getHeader(props.getHeader());
        if (key == null || key.isBlank()) {
            res.sendError(HttpServletResponse.SC_BAD_REQUEST, props.getHeader() + " header required");
            return false;
        }
        if (store.findResult(key).isPresent() || !store.putIfAbsent(key, props.getTtl())) {
            res.sendError(HttpServletResponse.SC_CONFLICT, "duplicate request");
            return false;
        }
        return true;
    }
}
