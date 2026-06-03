package com.company.framework.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 요청 단위 컨텍스트 값(불변). 테넌트/사용자/로케일 등 횡단 식별정보를 담는다.
 *
 * <p>Spring/Servlet 무의존(순수 JDK)이라 단위 테스트와 스레드 간 전달이 가볍다. 보관/전파는
 * {@link ContextHolder}, 비동기 전파는 {@code ContextTaskDecorator}, 아웃바운드 전파는
 * {@code ContextPropagationInterceptor} 가 담당한다.
 *
 * <p>고정 필드 외 임의 식별정보는 {@link #attributes()} 에 담아 코드 변경 없이 확장한다(예: channelId, deviceId).
 */
public final class RequestContext {

    /** 비어 있는 컨텍스트(미바인딩 시 {@link ContextHolder#get()} 가 반환). */
    public static final RequestContext EMPTY = builder().build();

    private final String tenantId;
    private final String userId;
    private final Locale locale;
    private final Map<String, String> attributes;

    private RequestContext(Builder b) {
        this.tenantId = blankToNull(b.tenantId);
        this.userId = blankToNull(b.userId);
        this.locale = b.locale;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(b.attributes));
    }

    public String tenantId() {
        return tenantId;
    }

    public String userId() {
        return userId;
    }

    public Locale locale() {
        return locale;
    }

    /** 읽기 전용 부가 속성 맵(키→값). */
    public Map<String, String> attributes() {
        return attributes;
    }

    /** 부가 속성 단건 조회(없으면 null). */
    public String attribute(String key) {
        return attributes.get(key);
    }

    public boolean hasTenant() {
        return tenantId != null;
    }

    public boolean hasUser() {
        return userId != null;
    }

    /** 현재 값을 복사해 일부만 바꾼 새 컨텍스트를 만들기 위한 빌더. */
    public Builder toBuilder() {
        return new Builder().tenantId(tenantId).userId(userId).locale(locale).attributes(attributes);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RequestContext that)) {
            return false;
        }
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(userId, that.userId)
                && Objects.equals(locale, that.locale)
                && attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, userId, locale, attributes);
    }

    @Override
    public String toString() {
        return "RequestContext{tenantId=" + tenantId + ", userId=" + userId + ", locale=" + locale + ", attributes="
                + attributes + '}';
    }

    /** {@link RequestContext} 빌더. 모든 setter 는 자기 자신을 반환. */
    public static final class Builder {
        private String tenantId;
        private String userId;
        private Locale locale;
        private final Map<String, String> attributes = new LinkedHashMap<>();

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder locale(Locale locale) {
            this.locale = locale;
            return this;
        }

        public Builder attribute(String key, String value) {
            if (key != null && value != null) {
                this.attributes.put(key, value);
            }
            return this;
        }

        public Builder attributes(Map<String, String> values) {
            if (values != null) {
                values.forEach(this::attribute);
            }
            return this;
        }

        public RequestContext build() {
            return new RequestContext(this);
        }
    }
}
