package com.company.framework.idempotency.web;

import com.company.framework.idempotency.config.IdempotencyProperties;
import com.company.framework.idempotency.core.Idempotent;
import com.company.framework.idempotency.store.IdempotencyStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

/**
 * {@link Idempotent} 메서드에 한해 Idempotency-Key 헤더로 중복요청을 처리한다.
 *
 * <p>두 모드(하위호환):
 *
 * <ul>
 *   <li><b>레거시(기본, replay.enabled=false)</b>: 키 없음 400, 최초 선점 통과, 이미 처리/처리중 409.
 *   <li><b>재생(replay.enabled=true)</b>: 완료된 동일 요청은 <b>저장된 응답을 그대로 재생</b>, 처리중은 409,
 *       최초 선점은 통과시키고 {@code afterCompletion}에서 응답을 캡처해 저장한다(다음 동일 키 요청이 재생).
 * </ul>
 *
 * <p>재생 모드에서 캡처는 {@link IdempotencyResponseFilter}가 응답을 {@link ContentCachingResponseWrapper}로
 * 감싼 경우에만 가능하다. 5xx/예외/캡처불가 시에는 실패를 캐시하지 않고 선점을 해제해 다음 재시도가 다시 처리한다.
 */
public class IdempotencyInterceptor implements HandlerInterceptor {

    /** 이번 요청이 키를 선점한 처리 주체임을 afterCompletion 에 전달(값=선점 키). 미설정이면 캡처하지 않음. */
    private static final String CAPTURE_ATTR = IdempotencyInterceptor.class.getName() + ".captureKey";

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

        if (!props.getReplay().isEnabled()) {
            // 레거시 모드: 결과 존재 또는 선점 실패 = 중복 → 409.
            if (store.findResult(key).isPresent() || !store.putIfAbsent(key, props.getTtl())) {
                res.sendError(HttpServletResponse.SC_CONFLICT, "duplicate request");
                return false;
            }
            return true;
        }

        // 재생 모드.
        Optional<String> done = store.findResult(key);
        if (done.isPresent()) {
            ResponseSnapshot.decode(done.get()).writeTo(res); // 완료된 동일 요청 → 저장 응답 재생
            return false;
        }
        if (store.putIfAbsent(key, props.getTtl())) {
            req.setAttribute(CAPTURE_ATTR, key); // 이번 요청이 처리 주체 → 완료 후 캡처
            return true;
        }
        // 선점 실패: findResult 와 putIfAbsent 사이에 완료됐을 수 있으니 한 번 더 확인 후 재생.
        Optional<String> raced = store.findResult(key);
        if (raced.isPresent()) {
            ResponseSnapshot.decode(raced.get()).writeTo(res);
            return false;
        }
        res.sendError(HttpServletResponse.SC_CONFLICT, "request in progress"); // 아직 처리중 → 409
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
        Object attr = req.getAttribute(CAPTURE_ATTR);
        if (!(attr instanceof String key)) {
            return; // 이번 요청이 선점 주체가 아니면(재생/통과/레거시) 캡처하지 않음
        }
        int status = res.getStatus();
        ContentCachingResponseWrapper wrapper = WebUtils.getNativeResponse(res, ContentCachingResponseWrapper.class);
        if (ex != null || status >= 500 || wrapper == null) {
            // 예외/서버오류/본문 캡처 불가 → 실패는 캐시하지 않고 선점 해제(다음 재시도가 재처리).
            store.remove(key);
            return;
        }
        byte[] body = wrapper.getContentAsByteArray();
        String snapshot = new ResponseSnapshot(status, res.getContentType(), body).encode();
        store.saveResult(key, snapshot, props.getTtl());
    }
}
