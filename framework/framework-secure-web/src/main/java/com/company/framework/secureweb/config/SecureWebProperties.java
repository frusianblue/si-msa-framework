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
 *     cors:                          # CORS — 단일 서비스 직접 노출 시 보조용(브라우저 진입점은 게이트웨이 globalcors 가 1차)
 *       enabled: false               #   기본 off. 게이트웨이 뒤에 있으면 켜지 말 것(중복/충돌 방지)
 *       path-pattern: /**            #   적용 경로
 *       allowed-origin-patterns: []  #   credentials=true 면 origins 대신 이쪽 사용("*" 금지 회피)
 *       allowed-origins: []          #   정확한 오리진 목록(와일드카드 불가 시)
 *       allowed-methods: [GET, POST, PUT, PATCH, DELETE, OPTIONS]
 *       allowed-headers: ["*"]
 *       exposed-headers: []          #   브라우저 JS 가 읽을 수 있게 노출할 응답 헤더
 *       allow-credentials: false     #   쿠키/인증정보 허용. true 면 allowed-origin-patterns 사용
 *       max-age-seconds: 3600        #   preflight 캐시 시간
 *     rate-limit:                    # 레이트리밋 — 인스턴스 로컬(파드 단위) 안전망. 전역 한도는 게이트웨이(Redis) 담당
 *       enabled: false               #   기본 off
 *       capacity: 20                 #   버스트 허용량(토큰버킷 용량)
 *       refill-per-second: 10        #   초당 토큰 보충량(평균 허용률)
 *       requested-tokens: 1          #   요청당 소비 토큰(가중치)
 *       key-strategy: PRINCIPAL_OR_IP #  IP | PRINCIPAL | PRINCIPAL_OR_IP(주체 없으면 IP 로 강등)
 *       trust-forwarded-for: false   #   X-Forwarded-For 신뢰(게이트웨이/프록시 뒤에서만 true)
 *       include-paths: []            #   적용 경로(Ant). 비어 있으면 전체
 *       exclude-paths: []            #   제외 경로(Ant) — actuator/health 등
 *       max-entries: 100000          #   키별 버킷 보관 상한(메모리 가드). 초과+유휴정리 후에도 넘치면 fail-open
 *       idle-eviction-seconds: 600   #   이 시간 이상 미사용 버킷은 정리 대상
 *       include-retry-after: true    #   429 시 Retry-After 헤더 부착
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
    private final Cors cors = new Cors();
    private final RateLimit rateLimit = new RateLimit();
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

    public Cors getCors() {
        return cors;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
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

    // ===== CORS (직접 노출 서비스 보조용) =====
    public static class Cors {
        private boolean enabled = false;
        private String pathPattern = "/**";
        private List<String> allowedOriginPatterns = new ArrayList<>();
        private List<String> allowedOrigins = new ArrayList<>();
        private List<String> allowedMethods =
                new ArrayList<>(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        private List<String> allowedHeaders = new ArrayList<>(List.of("*"));
        private List<String> exposedHeaders = new ArrayList<>();
        private boolean allowCredentials = false;
        private long maxAgeSeconds = 3600L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPathPattern() {
            return pathPattern;
        }

        public void setPathPattern(String pathPattern) {
            this.pathPattern = pathPattern;
        }

        public List<String> getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        public List<String> getExposedHeaders() {
            return exposedHeaders;
        }

        public void setExposedHeaders(List<String> exposedHeaders) {
            this.exposedHeaders = exposedHeaders;
        }

        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        public long getMaxAgeSeconds() {
            return maxAgeSeconds;
        }

        public void setMaxAgeSeconds(long maxAgeSeconds) {
            this.maxAgeSeconds = maxAgeSeconds;
        }
    }

    // ===== 레이트리밋(인스턴스 로컬 안전망) =====
    public static class RateLimit {
        /** 레이트리밋 키 산출 전략. */
        public enum KeyStrategy {
            /** 클라이언트 IP 기준. */
            IP,
            /** 인증 주체(Principal) 기준. 주체 없으면 무제한(키=anonymous 공통)이라 인증 구간 전용. */
            PRINCIPAL,
            /** 인증 주체 우선, 없으면 IP 로 강등(권장 기본값). */
            PRINCIPAL_OR_IP
        }

        private boolean enabled = false;
        private double capacity = 20;
        private double refillPerSecond = 10;
        private double requestedTokens = 1;
        private KeyStrategy keyStrategy = KeyStrategy.PRINCIPAL_OR_IP;
        private boolean trustForwardedFor = false;
        private List<String> includePaths = new ArrayList<>();
        private List<String> excludePaths = new ArrayList<>();
        private int maxEntries = 100_000;
        private long idleEvictionSeconds = 600L;
        private boolean includeRetryAfter = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getCapacity() {
            return capacity;
        }

        public void setCapacity(double capacity) {
            this.capacity = capacity;
        }

        public double getRefillPerSecond() {
            return refillPerSecond;
        }

        public void setRefillPerSecond(double refillPerSecond) {
            this.refillPerSecond = refillPerSecond;
        }

        public double getRequestedTokens() {
            return requestedTokens;
        }

        public void setRequestedTokens(double requestedTokens) {
            this.requestedTokens = requestedTokens;
        }

        public KeyStrategy getKeyStrategy() {
            return keyStrategy;
        }

        public void setKeyStrategy(KeyStrategy keyStrategy) {
            this.keyStrategy = keyStrategy;
        }

        public boolean isTrustForwardedFor() {
            return trustForwardedFor;
        }

        public void setTrustForwardedFor(boolean trustForwardedFor) {
            this.trustForwardedFor = trustForwardedFor;
        }

        public List<String> getIncludePaths() {
            return includePaths;
        }

        public void setIncludePaths(List<String> includePaths) {
            this.includePaths = includePaths;
        }

        public List<String> getExcludePaths() {
            return excludePaths;
        }

        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = excludePaths;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }

        public long getIdleEvictionSeconds() {
            return idleEvictionSeconds;
        }

        public void setIdleEvictionSeconds(long idleEvictionSeconds) {
            this.idleEvictionSeconds = idleEvictionSeconds;
        }

        public boolean isIncludeRetryAfter() {
            return includeRetryAfter;
        }

        public void setIncludeRetryAfter(boolean includeRetryAfter) {
            this.includeRetryAfter = includeRetryAfter;
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
