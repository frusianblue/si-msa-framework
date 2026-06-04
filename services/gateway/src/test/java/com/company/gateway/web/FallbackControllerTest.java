package com.company.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 서킷브레이커 폴백 핸들러 단위 테스트(컨텍스트 기동 없이 직접 호출). 회로 개방 시 graceful 503 + 고정 JSON 형식,
 * 서비스명 보간/정화를 확인한다.
 */
class FallbackControllerTest {

    private final FallbackController controller = new FallbackController();

    @Test
    void returns_503_with_fail_shaped_json_and_service_name() {
        ResponseEntity<String> response = controller.fallback("user");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body)
                .contains("\"success\":false")
                .contains("\"code\":\"E0503\"")
                .contains("user 서비스가 일시적으로 응답하지 않습니다")
                .contains("\"timestamp\":\"");
    }

    @Test
    void sanitizes_service_name_to_prevent_json_injection() {
        // 따옴표/중괄호 등 위험 문자는 제거되어 본문 JSON 이 깨지지 않는다.
        ResponseEntity<String> response = controller.fallback("ev\"il}{");

        String body = response.getBody();
        assertThat(body).isNotNull();
        // 정화 결과(evil)만 남고, 주입 시도 문자는 사라진다.
        assertThat(body).contains("evil 서비스가").doesNotContain("ev\"il");
        // 본문은 여전히 유효한 fail 형식.
        assertThat(body).contains("\"success\":false").contains("\"code\":\"E0503\"");
    }

    @Test
    void blank_or_null_service_falls_back_to_upstream() {
        assertThat(controller.fallback("").getBody()).contains("upstream 서비스가");
        assertThat(controller.fallbackNoService().getBody()).contains("upstream 서비스가");
    }
}
