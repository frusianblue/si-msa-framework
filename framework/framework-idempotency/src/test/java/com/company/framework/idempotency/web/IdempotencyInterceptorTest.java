package com.company.framework.idempotency.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.idempotency.config.IdempotencyProperties;
import com.company.framework.idempotency.core.Idempotent;
import com.company.framework.idempotency.store.InMemoryIdempotencyStore;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.util.ContentCachingResponseWrapper;

class IdempotencyInterceptorTest {

    /** 테스트용 컨트롤러: @Idempotent 메서드와 일반 메서드. */
    static class Ctrl {
        @Idempotent
        public String create() {
            return "x";
        }

        public String plain() {
            return "y";
        }
    }

    private InMemoryIdempotencyStore store;
    private HandlerMethod idempotentHandler;
    private HandlerMethod plainHandler;

    @BeforeEach
    void setUp() throws Exception {
        store = new InMemoryIdempotencyStore();
        Ctrl ctrl = new Ctrl();
        Method create = Ctrl.class.getMethod("create");
        Method plain = Ctrl.class.getMethod("plain");
        idempotentHandler = new HandlerMethod(ctrl, create);
        plainHandler = new HandlerMethod(ctrl, plain);
    }

    private IdempotencyProperties props(boolean replay) {
        IdempotencyProperties p = new IdempotencyProperties();
        p.setEnabled(true);
        p.getReplay().setEnabled(replay);
        return p;
    }

    private MockHttpServletRequest req(String key) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/transfers");
        if (key != null) {
            r.addHeader("Idempotency-Key", key);
        }
        return r;
    }

    @Test
    @DisplayName("@Idempotent 아닌 핸들러는 그대로 통과")
    void nonIdempotentPassesThrough() throws Exception {
        IdempotencyInterceptor it = new IdempotencyInterceptor(store, props(false));
        assertThat(it.preHandle(req("k"), new MockHttpServletResponse(), plainHandler))
                .isTrue();
    }

    @Test
    @DisplayName("키 헤더 없으면 400")
    void missingHeaderIs400() throws Exception {
        IdempotencyInterceptor it = new IdempotencyInterceptor(store, props(false));
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(it.preHandle(req(null), res, idempotentHandler)).isFalse();
        assertThat(res.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("레거시 모드: 최초 통과, 동일 키 재요청은 409")
    void legacyDuplicateIs409() throws Exception {
        IdempotencyInterceptor it = new IdempotencyInterceptor(store, props(false));
        assertThat(it.preHandle(req("k1"), new MockHttpServletResponse(), idempotentHandler))
                .isTrue();
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        assertThat(it.preHandle(req("k1"), res2, idempotentHandler)).isFalse();
        assertThat(res2.getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("재생 모드: 최초 요청은 선점하고 통과(캡처 표시)")
    void replayFirstClaimsAndPasses() throws Exception {
        IdempotencyInterceptor it = new IdempotencyInterceptor(store, props(true));
        MockHttpServletRequest req = req("k2");
        assertThat(it.preHandle(req, new MockHttpServletResponse(), idempotentHandler))
                .isTrue();
        // 캡처 표시 속성이 설정되어 afterCompletion 이 동작하도록 함(내부 키지만 존재 여부만 확인)
        assertThat(req.getAttributeNames().hasMoreElements()).isTrue();
    }

    @Test
    @DisplayName("재생 모드: 완료된 동일 요청은 저장된 응답을 재생")
    void replayReturnsStoredResponse() throws Exception {
        IdempotencyInterceptor it = new IdempotencyInterceptor(store, props(true));
        String snapshot =
                new ResponseSnapshot(201, "application/json", "{\"id\":7}".getBytes(StandardCharsets.UTF_8)).encode();
        store.saveResult("k3", snapshot, Duration.ofMinutes(10));

        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(it.preHandle(req("k3"), res, idempotentHandler)).isFalse();
        assertThat(res.getStatus()).isEqualTo(201);
        assertThat(res.getContentType()).isEqualTo("application/json");
        assertThat(res.getContentAsString()).isEqualTo("{\"id\":7}");
    }

    @Test
    @DisplayName("재생 모드: 처리중(선점됐으나 결과 없음)은 409")
    void replayInProgressIs409() throws Exception {
        store.putIfAbsent("k4", Duration.ofMinutes(10)); // 다른 요청이 선점, 아직 미완료
        IdempotencyInterceptor it = new IdempotencyInterceptor(store, props(true));
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(it.preHandle(req("k4"), res, idempotentHandler)).isFalse();
        assertThat(res.getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("재생 모드: 성공(2xx) 응답은 캡처되어 다음 요청에 재생된다")
    void capturesSuccessfulResponse() throws Exception {
        IdempotencyInterceptor it = new IdempotencyInterceptor(store, props(true));
        MockHttpServletRequest req = req("k5");
        it.preHandle(req, new MockHttpServletResponse(), idempotentHandler); // 선점

        // 핸들러가 본문을 쓴 것으로 시뮬레이션(ContentCachingResponseWrapper 가 버퍼링).
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(new MockHttpServletResponse());
        wrapper.setStatus(200);
        wrapper.setContentType("application/json");
        wrapper.getOutputStream().write("{\"ok\":1}".getBytes(StandardCharsets.UTF_8));

        it.afterCompletion(req, wrapper, idempotentHandler, null);

        assertThat(store.findResult("k5")).isPresent();
        ResponseSnapshot saved = ResponseSnapshot.decode(store.findResult("k5").orElseThrow());
        assertThat(saved.status()).isEqualTo(200);
        assertThat(new String(saved.body(), StandardCharsets.UTF_8)).isEqualTo("{\"ok\":1}");
    }

    @Test
    @DisplayName("재생 모드: 5xx 는 캐시하지 않고 선점 해제(재시도 가능)")
    void releasesOnServerError() throws Exception {
        IdempotencyInterceptor it = new IdempotencyInterceptor(store, props(true));
        MockHttpServletRequest req = req("k6");
        it.preHandle(req, new MockHttpServletResponse(), idempotentHandler); // 선점

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(new MockHttpServletResponse());
        wrapper.setStatus(500);
        it.afterCompletion(req, wrapper, idempotentHandler, null);

        assertThat(store.findResult("k6")).isEmpty();
        // 해제됐으므로 다시 선점 가능
        assertThat(store.putIfAbsent("k6", Duration.ofMinutes(10))).isTrue();
    }
}
