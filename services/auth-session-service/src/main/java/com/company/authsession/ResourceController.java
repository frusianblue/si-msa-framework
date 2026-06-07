package com.company.authsession;

import com.company.framework.core.response.ApiResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보호 리소스 — 카탈로그(examples/auth-types)와 동일한 자물쇠({@code GET /api/resource}).
 * 세션으로 인증된 주체를 응답으로 비춰준다(미인증 401).
 */
@RestController
@RequestMapping("/api")
public class ResourceController {

    @GetMapping("/resource")
    public ApiResponse<Map<String, Object>> resource(Authentication authentication) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "auth-session-service");
        body.put("message", "보호된 리소스 접근 성공(세션)");
        if (authentication != null) {
            body.put("principal", authentication.getName());
            List<String> authorities = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();
            body.put("authorities", authorities);
        }
        return ApiResponse.ok(body, "ok");
    }
}
