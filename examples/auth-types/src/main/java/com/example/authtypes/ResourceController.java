package com.example.authtypes;

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
 * 공통 보호 리소스 — 인증 방식이 무엇이든 동일한 자물쇠.
 *
 * <p>{@code GET /api/resource} 는 시큐리티 체인이 보호한다(미인증 401). 인증을 통과한 주체가 누구로 식별되는지를
 * 그대로 비춰주어, 트랙(세션/JWT/...)마다 "어떻게 인증됐는지"를 응답으로 체감하게 한다.
 */
@RestController
@RequestMapping("/api")
public class ResourceController {

    @GetMapping("/resource")
    public ApiResponse<Map<String, Object>> resource(Authentication authentication) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "보호된 리소스 접근 성공");
        if (authentication != null) {
            body.put("principal", authentication.getName());
            List<String> authorities = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();
            body.put("authorities", authorities);
            body.put("authType", authentication.getClass().getSimpleName());
        }
        return ApiResponse.ok(body, "ok");
    }
}
