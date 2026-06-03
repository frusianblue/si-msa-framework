package com.company.framework.samlsp.web;

import com.company.framework.samlsp.core.SamlLogoutInfo;
import com.company.framework.samlsp.slo.SamlSloService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;

/**
 * Spring Security 의 SAML SLO 필터({@code Saml2LogoutRequestFilter})가 {@code <LogoutRequest>} 를 <b>검증한 뒤</b>
 * 호출하는 {@link LogoutHandler}. 검증된 신원(registrationId + NameID)을 {@link SamlSloService} 로 넘겨 해당 사용자의
 * 자체 JWT 를 블랙리스트한다. 이 핸들러는 OpenSAML/XML 에 직접 의존하지 않는다(서명 검증·파싱은 SS 필터가 끝낸 뒤 호출).
 *
 * <p><b>신원 출처</b>: registrationId 는 SLO 엔드포인트 경로({@code /logout/saml2/slo/{registrationId}})에서, NameID 는
 * SS 가 해소한 {@link Authentication#getName()} 에서 얻는다. SessionIndex 는 핸들러 단계에서 접근 보장이 안 되므로 비운다.
 *
 * <p><b>무상태 한계</b>: 서버 세션 없는 순수 Bearer 배포에서 IdP-initiated LogoutRequest 의 NameID 를 XML 에서 직접
 * 얻으려면 커스텀 디코더(OpenSAML)가 필요하다(문서의 확장점). 본 핸들러는 SS 가 신원을 해소해 준 경우의 <b>토큰 무효화
 * 브리지</b>이며, authentication 이 없으면 조용히 no-op 한다(흐름을 깨지 않는다).
 */
public class SamlSloLogoutHandler implements LogoutHandler {

    private final SamlSloService sloService;

    public SamlSloLogoutHandler(SamlSloService sloService) {
        this.sloService = sloService;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication == null) {
            return; // 해소된 신원 없음 → 무상태 디코더 확장점(문서). 흐름은 그대로 진행.
        }
        String nameId = authentication.getName();
        if (nameId == null || nameId.isBlank()) {
            return;
        }
        String registrationId = extractRegistrationId(request);
        sloService.onLogoutRequest(new SamlLogoutInfo(registrationId, nameId, List.of()));
    }

    /**
     * {@code /logout/saml2/slo/{registrationId}} 경로의 마지막 세그먼트에서 registrationId 를 추출한다.
     * {@code .../slo} 로 끝나면(등록 id 미포함) {@code null}.
     */
    static String extractRegistrationId(HttpServletRequest request) {
        return registrationIdFromUri(request.getRequestURI());
    }

    /** 순수 경로 파싱(servlet 무의존 → JDK 단독 검증 가능). 끝 슬래시 무시, {@code slo}/빈 세그먼트면 null. */
    static String registrationIdFromUri(String uri) {
        if (uri == null) {
            return null;
        }
        int end = uri.length();
        while (end > 0 && uri.charAt(end - 1) == '/') {
            end--;
        }
        if (end == 0) {
            return null;
        }
        int slash = uri.lastIndexOf('/', end - 1);
        if (slash < 0) {
            return null;
        }
        String last = uri.substring(slash + 1, end);
        return ("slo".equals(last) || last.isEmpty()) ? null : last;
    }
}
