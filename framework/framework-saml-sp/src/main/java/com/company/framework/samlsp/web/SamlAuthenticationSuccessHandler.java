package com.company.framework.samlsp.web;

import com.company.framework.samlsp.config.SamlSpProperties;
import com.company.framework.samlsp.core.SamlAttributeMapper;
import com.company.framework.samlsp.core.SamlUserInfo;
import com.company.framework.samlsp.core.SamlUserResolver;
import com.company.framework.samlsp.token.SamlTokenIssuer;
import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * SAML ACS(assertion 수신) 성공 후, <b>서버 세션을 만들지 않고</b> 즉시 자체 JWT 를 발급해 응답한다(무상태 유지).
 *
 * <ol>
 *   <li>{@link Saml2AuthenticatedPrincipal} 에서 NameID + 속성 + registrationId 추출
 *   <li>{@link SamlAttributeMapper} 로 {@link SamlUserInfo} 정규화(설정 속성 키 우선)
 *   <li>앱이 등록한 {@link SamlUserResolver} 로 우리 사용자({@link AuthenticatedUser}) 매핑
 *   <li>{@link SamlTokenIssuer}(security 의 JwtProvider/TokenStore 재사용) 로 access/refresh 발급
 *   <li>{@link TokenResponse} 를 <b>수기 JSON</b> 으로 직접 기록(필터 레이어라 컨트롤러/GlobalExceptionHandler 밖 →
 *       Jackson 주입에 기대지 않고 고정 형태 JSON 을 직접 쓴다)
 * </ol>
 *
 * <p>continue 리다이렉트(토큰을 URL 에 싣지 않는 안전한 방식)는 앱 정책에 맡기는 확장 지점이며, 기본은 JSON 반환이다.
 */
public class SamlAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final SamlUserResolver userResolver;
    private final SamlTokenIssuer tokenIssuer;
    private final SamlSpProperties properties;

    public SamlAuthenticationSuccessHandler(
            SamlUserResolver userResolver, SamlTokenIssuer tokenIssuer, SamlSpProperties properties) {
        this.userResolver = userResolver;
        this.tokenIssuer = tokenIssuer;
        this.properties = properties;
    }

    // SS7 은 assertion 세부를 principal 에서 분리하면서 Saml2AuthenticatedPrincipal 을 deprecated 했다(SLO 지원 목적).
    //   정식 후속 = Saml2AssertionAuthentication.getRelyingPartyRegistrationId() + Saml2ResponseAssertionAccessor.
    //   다만 (a) deprecated API 는 SS 7.0.x 에서 완전 동작하고 제거는 빨라야 SS 8, (b) 새 접근자 메서드 배선은
    //   작성 환경 컴파일 미검증이라, 지금은 검증된 deprecated 경로를 유지하고 경고만 메서드 한정으로 억제한다.
    //   TODO(SS8 전 마이그레이션): authentication 을 Saml2AssertionAuthentication 으로 캐스팅해 registrationId 를 얻고,
    //   attributes 는 Saml2ResponseAssertionAccessor 에서 얻도록 교체(IDE 자동완성으로 접근자 메서드 확정 후).
    @SuppressWarnings("deprecation")
    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {

        Saml2AuthenticatedPrincipal principal = (Saml2AuthenticatedPrincipal) authentication.getPrincipal();
        String registrationId = principal.getRelyingPartyRegistrationId();
        Map<String, List<Object>> attributes = principal.getAttributes();

        SamlSpProperties.Registration reg =
                registrationId == null ? null : properties.getRegistrations().get(registrationId);
        SamlAttributeMapper mapper = new SamlAttributeMapper(
                reg == null ? null : reg.getEmailAttribute(), reg == null ? null : reg.getNameAttribute());
        SamlUserInfo userInfo = mapper.map(registrationId, principal.getName(), attributes);

        AuthenticatedUser user = userResolver.resolve(userInfo); // 앱이 매핑/가입/거부
        TokenResponse token = tokenIssuer.issue(user);

        writeJson(response, token);
    }

    /** TokenResponse 를 고정 형태 JSON 으로 직접 기록(Jackson 비의존). */
    private static void writeJson(HttpServletResponse response, TokenResponse token) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        StringBuilder sb = new StringBuilder(256);
        sb.append('{')
                .append("\"accessToken\":")
                .append(quote(token.accessToken()))
                .append(',')
                .append("\"refreshToken\":")
                .append(quote(token.refreshToken()))
                .append(',')
                .append("\"tokenType\":")
                .append(quote(token.tokenType()))
                .append(',')
                .append("\"expiresInSeconds\":")
                .append(token.expiresInSeconds())
                .append(',')
                .append("\"roles\":")
                .append(array(token.roles()))
                .append('}');
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private static String array(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(quote(values.get(i)));
        }
        sb.append(']');
        return sb.toString();
    }

    /** JSON 문자열 리터럴(필수 제어문자 이스케이프). null 은 JSON null. */
    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
