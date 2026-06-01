package com.company.framework.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 알림 모듈 토글/채널 설정.
 *
 * <pre>{@code
 * framework:
 *   notification:
 *     enabled: false                 # 선택형 → 명시적 on. 켜면 NotificationService 제공.
 *     channels:
 *       mail:
 *         enabled: false             # JavaMailSender(=spring.mail.host 설정) 필요
 *         from: noreply@company.com
 *       sms:
 *         enabled: false             # 기본 LoggingSmsClient(실발송X) → 벤더 SmsClient 빈으로 교체
 *         from: "0212345678"
 *       alimtalk:
 *         enabled: false             # 기본 LoggingAlimtalkClient → 벤더 AlimtalkClient 빈으로 교체
 *         sender-key: ${ALIMTALK_SENDER_KEY:}
 * }</pre>
 */
@ConfigurationProperties(prefix = "framework.notification")
public class NotificationProperties {

    /** 선택형 → 기본 off. */
    private boolean enabled = false;

    private final Channels channels = new Channels();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Channels getChannels() {
        return channels;
    }

    public static class Channels {
        private final Mail mail = new Mail();
        private final Sms sms = new Sms();
        private final Alimtalk alimtalk = new Alimtalk();

        public Mail getMail() {
            return mail;
        }

        public Sms getSms() {
            return sms;
        }

        public Alimtalk getAlimtalk() {
            return alimtalk;
        }
    }

    public static class Mail {
        private boolean enabled = false;
        private String from;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }
    }

    public static class Sms {
        private boolean enabled = false;
        private String from;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }
    }

    public static class Alimtalk {
        private boolean enabled = false;
        private String senderKey;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSenderKey() {
            return senderKey;
        }

        public void setSenderKey(String senderKey) {
            this.senderKey = senderKey;
        }
    }
}
