package com.company.framework.i18n.core;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 어디서나 현재 요청 로케일로 메시지를 꺼내는 얇은 파사드.
 * 컨트롤러/서비스에서 주입받아 messageResolver.get("user.created") 처럼 사용.
 */
public class MessageResolver {

    private final MessageSource messageSource;

    public MessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /** 코드가 없으면 MissingResourceException 대신 코드 자체를 반환(NoSuchMessage 방지). */
    public String get(String code, Object... args) {
        return messageSource.getMessage(code, args, code, LocaleContextHolder.getLocale());
    }

    /** 코드가 없으면 지정한 기본 문구를 반환. */
    public String getOrDefault(String code, String defaultMessage, Object... args) {
        return messageSource.getMessage(code, args, defaultMessage, LocaleContextHolder.getLocale());
    }
}
