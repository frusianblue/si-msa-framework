package com.company.framework.notification.channel.alimtalk;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 기본 알림톡 어댑터(실발송 없음): 로그만 남긴다. 운영에서는 서비스가 실제 벤더 {@link AlimtalkClient} 빈으로 대체한다.
 */
public class LoggingAlimtalkClient implements AlimtalkClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingAlimtalkClient.class);

    @Override
    public void send(String to, String templateCode, String content, Map<String, String> variables, String senderKey) {
        log.info(
                "[notification][alimtalk:logging] to={} template={} senderKey={} vars={} content={}",
                to,
                templateCode,
                senderKey,
                variables,
                content);
    }
}
