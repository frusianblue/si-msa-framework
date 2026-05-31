package com.company.framework.secureweb.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 웹 보안 필터 모듈 토글. (XSS 본문 이스케이프는 framework-core 담당)
 * framework:
 *   secure-web:
 *     enabled: false                 # 2단 토글(선택형 → 명시적 on 필요)
 *     headers:                       # 보안 응답 헤더
 *       enabled: true                #   모듈 on 시 기본 동작
 *       frame-options: DENY          #   X-Frame-Options: DENY|SAMEORIGIN (빈 값이면 미설정)
 *       content-type-options: true   #   X-Content-Type-Options: nosniff
 *       referrer-policy: no-referrer #   Referrer-Policy (빈 값이면 미설정)
 *       content-security-policy: ""  #   CSP (빈 값이면 미설정)
 *       permissions-policy: ""       #   Permissions-Policy (빈 값이면 미설정)
 *       hsts:
 *         enabled: false             #   HTTPS 종단 전제 → 기본 off
 *         max-age-seconds: 31536000
 *         include-sub-domains: true
 *         preload: false
 *     path-traversal:                # 경로 조작(../, 인코딩 우회, 널바이트) 차단
 *       enabled: true
 *     injection:                     # 인젝션 스크리닝(SQLi 등) — 오탐 가능 → 기본 off, 파라미터화 쿼리가 본 방어
 *       enabled: false
 *       mode: block                  #   block(차단) | log-only(탐지만 기록)
 *       exclude-paths: []            #   스킵할 경로(prefix)
 *       additional-patterns: []      #   내장 패턴에 더할 정규식
 *     csrf:                          # CSRF 더블서브밋 쿠키 — stateless JWT면 보통 불필요 → 기본 off
 *       enabled: false
 *       cookie-name: XSRF-TOKEN
 *       header-name: X-XSRF-TOKEN
 *       protect-paths: []            #   보호 경로(prefix). 비어 있으면 모든 비안전 메서드 보호
 *       exclude-paths: []            #   예외 경로(prefix)
 *       cookie-secure: true
 *       cookie-same-site: Lax        #   Strict | Lax | None
 */
@ConfigurationProperties(prefix = "framework.secure-web")
public class SecureWebProperties {

    private boolean enabled = false;
    private final Headers headers = new Headers();
    private final PathTraversal pathTraversal = new PathTraversal();
    private final Injection injection = new Injection();
    private final Csrf csrf = new Csrf();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Headers getHeaders() {
        return headers;
    }

    public PathTraversal getPathTraversal() {
        return pathTraversal;
    }

    public Injection getInjection() {
        return injection;
    }

    public Csrf getCsrf() {
        return csrf;
    }

    // ===== 보안 응답 헤더 =====
    public static class Headers {
        private boolean enabled = true;
        private String frameOptions = "DENY";
        private boolean contentTypeOptions = true;
        private String referrerPolicy = "no-referrer";
        private String contentSecurityPolicy = "";
        private String permissionsPolicy = "";
        private final Hsts hsts = new Hsts();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFrameOptions() {
            return frameOptions;
        }

        public void setFrameOptions(String frameOptions) {
            this.frameOptions = frameOptions;
        }

        public boolean isContentTypeOptions() {
            return contentTypeOptions;
        }

        public void setContentTypeOptions(boolean contentTypeOptions) {
            this.contentTypeOptions = contentTypeOptions;
        }

        public String getReferrerPolicy() {
            return referrerPolicy;
        }

        public void setReferrerPolicy(String referrerPolicy) {
            this.referrerPolicy = referrerPolicy;
        }

        public String getContentSecurityPolicy() {
            return contentSecurityPolicy;
        }

        public void setContentSecurityPolicy(String contentSecurityPolicy) {
            this.contentSecurityPolicy = contentSecurityPolicy;
        }

        public String getPermissionsPolicy() {
            return permissionsPolicy;
        }

        public void setPermissionsPolicy(String permissionsPolicy) {
            this.permissionsPolicy = permissionsPolicy;
        }

        public Hsts getHsts() {
            return hsts;
        }

        public static class Hsts {
            private boolean enabled = false;
            private long maxAgeSeconds = 31_536_000L;
            private boolean includeSubDomains = true;
            private boolean preload = false;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public long getMaxAgeSeconds() {
                return maxAgeSeconds;
            }

            public void setMaxAgeSeconds(long maxAgeSeconds) {
                this.maxAgeSeconds = maxAgeSeconds;
            }

            public boolean isIncludeSubDomains() {
                return includeSubDomains;
            }

            public void setIncludeSubDomains(boolean includeSubDomains) {
                this.includeSubDomains = includeSubDomains;
            }

            public boolean isPreload() {
                return preload;
            }

            public void setPreload(boolean preload) {
                this.preload = preload;
            }
        }
    }

    // ===== 경로 조작 차단 =====
    public static class PathTraversal {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    // ===== 인젝션 스크리닝 =====
    public static class Injection {
        /** 차단(BLOCK) 또는 탐지만 기록(LOG_ONLY). */
        public enum Mode {
            BLOCK,
            LOG_ONLY
        }

        private boolean enabled = false;
        private Mode mode = Mode.BLOCK;
        private List<String> excludePaths = new ArrayList<>();
        private List<String> additionalPatterns = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public List<String> getExcludePaths() {
            return excludePaths;
        }

        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = excludePaths;
        }

        public List<String> getAdditionalPatterns() {
            return additionalPatterns;
        }

        public void setAdditionalPatterns(List<String> additionalPatterns) {
            this.additionalPatterns = additionalPatterns;
        }
    }

    // ===== CSRF 더블서브밋 쿠키 =====
    public static class Csrf {
        private boolean enabled = false;
        private String cookieName = "XSRF-TOKEN";
        private String headerName = "X-XSRF-TOKEN";
        private List<String> protectPaths = new ArrayList<>();
        private List<String> excludePaths = new ArrayList<>();
        private boolean cookieSecure = true;
        private String cookieSameSite = "Lax";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCookieName() {
            return cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public List<String> getProtectPaths() {
            return protectPaths;
        }

        public void setProtectPaths(List<String> protectPaths) {
            this.protectPaths = protectPaths;
        }

        public List<String> getExcludePaths() {
            return excludePaths;
        }

        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = excludePaths;
        }

        public boolean isCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }

        public String getCookieSameSite() {
            return cookieSameSite;
        }

        public void setCookieSameSite(String cookieSameSite) {
            this.cookieSameSite = cookieSameSite;
        }
    }
}
