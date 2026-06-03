package com.company.framework.oauthclient.core;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.oauthclient.config.OAuthClientProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * 외부 IdP 와의 HTTP 통신. (1) 인가코드 → 액세스토큰 교환, (2) 액세스토큰 → userinfo 조회.
 *
 * <p>요청/응답 본문은 모두 <b>문자열로 주고받고 Jackson 3 로 직접 파싱</b>한다(메시지 컨버터 주입 취약성 회피 —
 * 레포의 Jackson 직접 제어 원칙과 동일). {@code com.fasterxml.jackson.*} 는 사용하지 않는다(tools.jackson 전용).
 */
public class OAuthClient {

    private final RestClient restClient;
    private final JsonMapper json = JsonMapper.builder().build();

    public OAuthClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** 인가코드를 액세스토큰으로 교환하고 access_token 문자열을 반환한다. */
    public String exchangeCodeForAccessToken(OAuthClientProperties.Provider p, String code, String redirectUri) {
        String form = form(Map.of(
                "grant_type",
                "authorization_code",
                "code",
                code,
                "redirect_uri",
                redirectUri,
                "client_id",
                p.getClientId(),
                "client_secret",
                p.getClientSecret() == null ? "" : p.getClientSecret()));
        String body;
        try {
            body = restClient
                    .post()
                    .uri(p.getTokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "토큰 교환에 실패했습니다: " + e.getMessage());
        }
        Map<String, Object> parsed = parse(body);
        Object accessToken = parsed.get("access_token");
        if (accessToken == null) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "토큰 응답에 access_token 이 없습니다.");
        }
        return String.valueOf(accessToken);
    }

    /** 액세스토큰으로 userinfo 를 조회해 원본 Map 으로 반환한다. */
    public Map<String, Object> fetchUserInfo(OAuthClientProperties.Provider p, String accessToken) {
        String body;
        try {
            body = restClient
                    .get()
                    .uri(p.getUserInfoUri())
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "사용자 정보 조회에 실패했습니다: " + e.getMessage());
        }
        return parse(body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String body) {
        if (body == null || body.isBlank()) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "소셜 응답 본문이 비어 있습니다.");
        }
        try {
            return json.readValue(body, Map.class);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "소셜 응답 파싱에 실패했습니다: " + e.getMessage());
        }
    }

    private static String form(Map<String, String> params) {
        Map<String, String> ordered = new LinkedHashMap<>(params);
        StringBuilder sb = new StringBuilder();
        ordered.forEach((k, v) -> {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(k)).append('=').append(enc(v));
        });
        return sb.toString();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
