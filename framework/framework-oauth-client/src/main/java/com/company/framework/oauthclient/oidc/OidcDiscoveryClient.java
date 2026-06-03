package com.company.framework.oauthclient.oidc;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * OIDC Discovery({@code /.well-known/openid-configuration}) 문서를 조회해 표준 엔드포인트를 추출한다.
 * 응답은 문자열로 받아 Jackson 3 로 직접 파싱한다(레포의 Jackson 직접 제어 원칙 — {@code tools.jackson.*} 전용).
 */
public class OidcDiscoveryClient {

    /** discovery 문서에서 우리가 쓰는 항목만 추린 메타데이터. */
    public record Metadata(
            String issuer,
            String authorizationEndpoint,
            String tokenEndpoint,
            String userInfoEndpoint,
            String jwksUri) {}

    private final RestClient restClient;
    private final JsonMapper json = JsonMapper.builder().build();

    public OidcDiscoveryClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /** discovery URL 을 조회해 메타데이터를 반환한다(필수 항목 누락 시 명확히 실패). */
    @SuppressWarnings("unchecked")
    public Metadata fetch(String discoveryUri) {
        String body;
        try {
            body = restClient
                    .get()
                    .uri(discoveryUri)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            throw new BusinessException(
                    ErrorCode.Common.INTERNAL_ERROR,
                    "OIDC discovery 조회에 실패했습니다(" + discoveryUri + "): " + e.getMessage());
        }
        if (body == null || body.isBlank()) {
            throw new BusinessException(ErrorCode.Common.INTERNAL_ERROR, "OIDC discovery 응답이 비어 있습니다: " + discoveryUri);
        }
        Map<String, Object> doc;
        try {
            doc = json.readValue(body, Map.class);
        } catch (RuntimeException e) {
            throw new BusinessException(
                    ErrorCode.Common.INTERNAL_ERROR,
                    "OIDC discovery 파싱에 실패했습니다(" + discoveryUri + "): " + e.getMessage());
        }
        String issuer = str(doc.get("issuer"));
        String authz = str(doc.get("authorization_endpoint"));
        String token = str(doc.get("token_endpoint"));
        String userInfo = str(doc.get("userinfo_endpoint"));
        String jwks = str(doc.get("jwks_uri"));
        if (isBlank(authz) || isBlank(token) || isBlank(jwks)) {
            throw new BusinessException(
                    ErrorCode.Common.INTERNAL_ERROR,
                    "OIDC discovery 문서에 필수 엔드포인트가 없습니다(authorization/token/jwks): " + discoveryUri);
        }
        return new Metadata(issuer, authz, token, userInfo, jwks);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
