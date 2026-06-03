package com.company.framework.logmask.logback;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * 포맷된 로그 메시지를 개인정보 마스킹하는 Logback 컨버터. 패턴에 {@code %mmsg} 로 등록해 표준 {@code %msg} 대신
 * 사용한다(등록은 {@code logback-masking.xml} 의 {@code <conversionRule>} 또는 앱 logback 설정).
 *
 * <p>기반 {@link MessageConverter#convert(ILoggingEvent)} 가 만든 최종 메시지 문자열을 {@link MaskingSupport} 에
 * 통과시킨다. 인자 치환({@code {}}) 이후의 "사람이 읽는 최종 텍스트"를 가리므로, 인자로 흘러든 PII 도 함께 마스킹된다.
 *
 * <p>이 클래스는 Logback 이 직접 인스턴스화하므로 Spring DI 를 받지 않는다. 실제 마스킹 규칙은 부팅 시 설치된
 * 마스커(없으면 기본 규칙)를 {@code MaskingSupport} 가 제공한다.
 */
public class MaskingMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return MaskingSupport.mask(super.convert(event));
    }
}
