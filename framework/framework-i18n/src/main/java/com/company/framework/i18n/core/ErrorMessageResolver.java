package com.company.framework.i18n.core;

import com.company.framework.core.error.ErrorCode;

/**
 * ErrorCode 를 로케일별 메시지로 해석. 번들 키 규칙: error.&lt;code&gt; (예: error.E0001).
 * 키가 없으면 fallback(호출자 detail) -> ErrorCode.message() 순으로 폴백.
 */
public class ErrorMessageResolver {

    private final MessageResolver messageResolver;

    public ErrorMessageResolver(MessageResolver messageResolver) {
        this.messageResolver = messageResolver;
    }

    public String resolve(ErrorCode errorCode, String fallback) {
        String def = (fallback == null || fallback.isBlank()) ? errorCode.message() : fallback;
        return messageResolver.getOrDefault("error." + errorCode.code(), def);
    }
}
