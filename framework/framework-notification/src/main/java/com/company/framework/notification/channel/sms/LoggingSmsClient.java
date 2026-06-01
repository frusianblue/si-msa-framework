package com.company.framework.notification.channel.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 기본 SMS 어댑터(실발송 없음): 로그만 남긴다. 로컬/개발 또는 벤더 미연동 단계용.
 * 운영에서는 서비스가 실제 벤더 {@link SmsClient} 빈을 등록해 이 기본 빈을 대체한다(@ConditionalOnMissingBean).
 */
public class LoggingSmsClient implements SmsClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsClient.class);

    @Override
    public void send(String to, String text, String from) {
        log.info("[notification][sms:logging] to={} from={} text={}", to, from, text);
    }
}
