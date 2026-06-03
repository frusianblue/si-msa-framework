package com.company.framework.oauthclient.core;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import com.company.framework.oauthclient.config.OAuthClientProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 공급자 설정 조회/검증 + 인가 URL·redirect_uri 조립 + userinfo 정규화. 순수 로직(외부 의존성 없음).
 */
public class ProviderRegistry {

    private final OAuthClientProperties properties;

    public ProviderRegistry(OAuthClientProperties properties) {
        this.properties = properties;
    }

    /** 공급자 설정을 가져오되, 없거나 필수값 누락이면 명확히 거부한다. */
    public OAuthClientProperties.Provider require(String providerId) {
        OAuthClientProperties.Provider p = properties.getProviders().get(providerId);
        if (p == null) {
            throw new BusinessException(ErrorCode.Common.UNAUTHORIZED, "지원하지 않는 소셜 로그인 공급자입니다: " + providerId);
        }
        if (isBlank(p.getClientId())
                || isBlank(p.getAuthorizationUri())
                || isBlank(p.getTokenUri())
                || isBlank(p.getUserInfoUri())
                || isBlank(p.getUserNameAttribute())) {
            throw new BusinessException(
                    ErrorCode.Common.INTERNAL_ERROR,
                    "소셜 로그인 공급자 설정이 불완전합니다(uri/client-id/user-name-attribute): " + providerId);
        }
        return p;
    }

    /** IdP 인가 엔드포인트로 보낼 전체 URL(Authorization Code Flow). */
    public String authorizationUrl(String providerId, OAuthClientProperties.Provider p, String state) {
        String scope = p.getScope() == null ? "" : String.join(" ", p.getScope());
        StringBuilder sb = new StringBuilder(p.getAuthorizationUri());
        sb.append(p.getAuthorizationUri().contains("?") ? "&" : "?");
        sb.append("response_type=code");
        sb.append("&client_id=").append(enc(p.getClientId()));
        sb.append("&redirect_uri=").append(enc(redirectUri(providerId, p)));
        sb.append("&state=").append(enc(state));
        if (!scope.isBlank()) sb.append("&scope=").append(enc(scope));
        return sb.toString();
    }

    /** 토큰 교환·인가 양쪽에 동일하게 쓰는 redirect_uri(IdP 콘솔 등록값과 일치해야 함). */
    public String redirectUri(String providerId, OAuthClientProperties.Provider p) {
        if (!isBlank(p.getRedirectUri())) return p.getRedirectUri();
        String base = properties.getBaseRedirectUri();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/api/v1/auth/oauth/" + providerId + "/callback";
    }

    /** userinfo 원본(Map)을 표준 모델로 정규화. providerId 가 비면 신원확인 실패로 본다. */
    public OAuthUserInfo toUserInfo(String providerId, OAuthClientProperties.Provider p, Map<String, Object> raw) {
        String providerUserId = Attributes.getString(raw, p.getUserNameAttribute());
        if (isBlank(providerUserId)) {
            throw new BusinessException(
                    ErrorCode.Common.UNAUTHORIZED, "소셜 응답에서 사용자 식별자를 찾지 못했습니다(user-name-attribute 확인): " + providerId);
        }
        String email = Attributes.getString(raw, p.getEmailAttribute());
        String name = Attributes.getString(raw, p.getNameAttribute());
        return new OAuthUserInfo(providerId, providerUserId, email, name, raw);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
