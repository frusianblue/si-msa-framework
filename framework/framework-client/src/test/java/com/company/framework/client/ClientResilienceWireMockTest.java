package com.company.framework.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.company.framework.client.config.ClientAutoConfiguration;
import com.company.framework.client.config.ClientProperties;
import com.company.framework.client.core.CircuitOpenException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

/**
 * 서비스간 연동(외부 API 호출) 표준 동작 검증 — WireMock 으로 가짜 업스트림을 띄우고
 * framework-client 의 재시도/서킷브레이커 인터셉터가 실제 HTTP 흐름에서 의도대로 동작하는지 확인한다.
 *
 * <p>인터셉터 합성 순서(바깥→안): Trace → CircuitBreaker → Retry → Logging → 실제 호출.
 * 즉 서킷브레이커가 재시도를 감싸므로, 재시도 내부에서 소비된 중간 5xx 는 서킷에 집계되지 않고
 * 재시도 루프의 최종 결과만 서킷이 본다. 서킷 단독 동작을 보려면 재시도를 끈다.
 *
 * <p>작성 환경에서는 gradle 실행 불가 → 받는 쪽에서
 * {@code ./gradlew :framework:framework-client:test} 로 그린 확인.
 */
class ClientResilienceWireMockTest {

    private WireMockServer wm;

    @BeforeEach
    void startServer() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
    }

    @AfterEach
    void stopServer() {
        if (wm != null) {
            wm.stop();
        }
    }

    /** enabled=true 면 표준 RestClient.Builder 빈이 뜨고, 끄면 백오프(빈 없음). */
    @Test
    void autoConfigLoadsBuilderOnlyWhenEnabled() {
        ApplicationContextRunner runner =
                new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ClientAutoConfiguration.class));

        runner.withPropertyValues("framework.client.enabled=true")
                .run(ctx -> assertThat(ctx).hasBean("frameworkRestClientBuilder"));

        runner.run(ctx -> assertThat(ctx).doesNotHaveBean("frameworkRestClientBuilder"));
    }

    /** GET 503 → 멱등 메서드라 재시도 → 200 "ok". 업스트림은 정확히 2번 호출. */
    @Test
    void getRetriesOnRetryableStatusThenSucceeds() {
        wm.stubFor(get(urlEqualTo("/r"))
                .inScenario("retry")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        wm.stubFor(get(urlEqualTo("/r"))
                .inScenario("retry")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        ClientProperties props = baseProps();
        props.getRetry().setEnabled(true);
        props.getRetry().setMaxAttempts(2);
        props.getRetry().setBackoff(Duration.ofMillis(1));
        props.getCircuitBreaker().setEnabled(false);

        String body = clientFor(props).get().uri("/r").retrieve().body(String.class);

        assertThat(body).isEqualTo("ok");
        wm.verify(2, getRequestedFor(urlEqualTo("/r")));
    }

    /** 재시도 OFF + 연속 실패 임계치 2 → 3번째 호출은 서킷 OPEN 으로 업스트림 도달 전 차단. */
    @Test
    void circuitOpensAfterConsecutiveFailures() {
        wm.stubFor(get(urlEqualTo("/cb")).willReturn(aResponse().withStatus(503)));

        ClientProperties props = baseProps();
        props.getRetry().setEnabled(false);
        props.getCircuitBreaker().setEnabled(true);
        props.getCircuitBreaker().setFailureThreshold(2);
        props.getCircuitBreaker().setWaitDuration(Duration.ofSeconds(10)); // 테스트 동안 OPEN 유지

        RestClient client = clientFor(props); // 호스트 단위 브레이커 공유 위해 단일 인스턴스 재사용

        // 1회차/2회차: 업스트림 503 그대로 전달 → RestClient 가 5xx 예외. 2회차에서 임계치 도달 → OPEN.
        assertThatThrownBy(() -> client.get().uri("/cb").retrieve().body(String.class))
                .isInstanceOf(HttpServerErrorException.class);
        assertThatThrownBy(() -> client.get().uri("/cb").retrieve().body(String.class))
                .isInstanceOf(HttpServerErrorException.class);

        // 3회차: 서킷 OPEN → 업스트림 호출 없이 CircuitOpenException 계열로 차단.
        Throwable third =
                catchThrowable(() -> client.get().uri("/cb").retrieve().body(String.class));
        assertThat(third).isNotNull();
        assertThat(hasInChain(third, CircuitOpenException.class)).isTrue();

        // 업스트림은 1·2회차만 도달 → 정확히 2번.
        wm.verify(2, getRequestedFor(urlEqualTo("/cb")));
    }

    /** POST 는 기본 비멱등 → 재시도 대상 아님(부작용 방지). 503 이어도 업스트림 1번만. */
    @Test
    void postIsNotRetried() {
        wm.stubFor(post(urlEqualTo("/p")).willReturn(aResponse().withStatus(503)));

        ClientProperties props = baseProps();
        props.getRetry().setEnabled(true); // 켜져 있어도 기본 methods 에 POST 없음
        props.getCircuitBreaker().setEnabled(false);

        assertThatThrownBy(() -> clientFor(props).post().uri("/p").retrieve().body(String.class))
                .isInstanceOf(HttpServerErrorException.class);

        wm.verify(1, postRequestedFor(urlEqualTo("/p")));
    }

    // --- helpers ---

    /** 격리: 로깅/트레이스 인터셉터는 꺼서 재시도/서킷 동작만 검증. */
    private static ClientProperties baseProps() {
        ClientProperties props = new ClientProperties();
        props.getLogging().setEnabled(false);
        props.getTrace().setEnabled(false);
        return props;
    }

    private RestClient clientFor(ClientProperties props) {
        return new ClientAutoConfiguration()
                .frameworkRestClientBuilder(props)
                .baseUrl(wm.baseUrl())
                .build();
    }

    private static boolean hasInChain(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }
}
