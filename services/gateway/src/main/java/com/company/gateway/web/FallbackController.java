package com.company.gateway.web;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 서킷브레이커 폴백 종착지. {@code application.yml} 의 라우트가 회로 개방 시 {@code fallbackUri: forward:/fallback/{service}}
 * 로 내부 포워딩하면 이 핸들러가 받아 <b>graceful 503</b>(ApiResponse.fail 형식 JSON)을 돌려준다.
 *
 * <p>이 핸들러가 없으면 회로 개방 시 포워딩 대상이 없어 <b>404</b> 가 나간다(다운스트림 장애가 "없는 경로"로 둔갑).
 * {@code /fallback/**} 는 게이트웨이 엣지 인증의 permit-all 패턴에 포함되어 토큰 없이 통과한다.
 *
 * <p>게이트웨이는 {@code framework-core} 에 의존하지 않으므로(WebFlux/servlet 충돌 회피) {@code ApiResponse} 를 직접
 * 쓰지 못한다. 대신 {@link com.company.gateway.auth.GatewayAuthGlobalFilter} 의 401 응답과 <b>동일한 고정 JSON
 * 형식</b>을 손으로 맞춘다(서비스명은 영숫자/{@code -_} 로 정화해 이스케이프 불필요).
 */
@RestController
public class FallbackController {

    /** 회로 개방 시(모든 HTTP 메서드) — 원 요청 메서드를 보존한 채 포워딩되므로 메서드 제한을 두지 않는다. */
    @RequestMapping(value = "/fallback/{service}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> fallback(@PathVariable("service") String service) {
        String safe = sanitize(service);
        String body = "{\"success\":false,\"code\":\"E0503\",\"message\":\"" + safe
                + " 서비스가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해 주세요.\",\"timestamp\":\"" + Instant.now()
                + "\"}";
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    /** {@code /fallback} 자체(서비스명 미지정)로 들어와도 안전하게 받는다. */
    @RequestMapping(value = "/fallback", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> fallbackNoService() {
        return fallback("upstream");
    }

    /** JSON 본문 주입 안전을 위해 서비스명을 영숫자/{@code -_} 로 제한(그 외 문자는 제거). 비면 upstream. */
    private static String sanitize(String service) {
        if (service == null) {
            return "upstream";
        }
        String cleaned = service.replaceAll("[^A-Za-z0-9_-]", "");
        return cleaned.isBlank() ? "upstream" : cleaned;
    }
}
