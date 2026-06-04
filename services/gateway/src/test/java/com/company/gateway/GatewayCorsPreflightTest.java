package com.company.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 게이트웨이 CORS preflight 런타임 점검. 실제 게이트웨이를 띄워(WebTestClient) globalcors 설정이 엣지에서
 * 의도대로 동작하는지 확인한다.
 *
 * <ul>
 *   <li>허용 origin({@code https://*.yourdomain.com}) preflight → 요청 origin echo + credentials/methods/max-age.
 *   <li>미허용 origin → {@code Access-Control-Allow-Origin} 미부여(차단).
 *   <li>{@code add-to-simple-url-handler-mapping: true} → 라우트에 안 걸리는 경로의 OPTIONS preflight 도 처리.
 * </ul>
 *
 * <p>엣지 인증/이중 발급기/블랙리스트는 모두 기본 off 라 이 테스트에는 토큰/Redis 가 필요 없다(preflight 는
 * 라우팅/레이트리밋보다 앞에서 CORS 처리되어 다운스트림으로 가지 않는다). 포트 주입은 레포 e2e 관례
 * ({@code @Value("${local.server.port}")})를 따른다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayCorsPreflightTest {

    @Value("${local.server.port}")
    private int port;

    private WebTestClient client() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void preflight_from_allowed_origin_is_accepted_with_cors_headers() {
        client().options()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.ORIGIN, "https://app.yourdomain.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.yourdomain.com")
                .expectHeader()
                .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectHeader()
                .valueEquals(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");
    }

    @Test
    void preflight_advertises_configured_methods() {
        client().options()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.ORIGIN, "https://app.yourdomain.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .value(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, methods -> assertThat(methods)
                        .contains("GET")
                        .contains("POST")
                        .contains("DELETE"));
    }

    @Test
    void preflight_from_disallowed_origin_gets_no_allow_origin_header() {
        client().options()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.ORIGIN, "https://evil.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectHeader()
                .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void preflight_handled_for_non_routed_path_via_simple_url_handler_mapping() {
        // add-to-simple-url-handler-mapping: true → 라우트 predicate 에 안 걸리는 경로도 preflight 처리.
        client().options()
                .uri("/no-such-route")
                .header(HttpHeaders.ORIGIN, "https://app.yourdomain.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.yourdomain.com");
    }
}
