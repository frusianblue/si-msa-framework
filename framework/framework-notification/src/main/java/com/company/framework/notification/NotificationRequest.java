package com.company.framework.notification;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 알림 발송 요청(채널 공통 모델).
 *
 * <ul>
 *   <li>{@code channel}, {@code to} — 필수.
 *   <li>{@code subject} — 메일 제목(메일에서만 사용).
 *   <li>{@code content} — 본문. 알림톡은 템플릿이 본문을 결정할 수 있어 선택.
 *   <li>{@code html} — 메일 HTML 여부(기본 false=텍스트).
 *   <li>{@code from} — 발신자 override(미지정 시 채널 기본값 사용).
 *   <li>{@code templateCode}, {@code variables} — 알림톡 템플릿 코드/치환변수.
 * </ul>
 *
 * <pre>{@code
 * NotificationRequest.mail("user@x.com", "가입 환영", "<b>환영합니다</b>").html(true).build();
 * NotificationRequest.sms("01012345678", "인증번호 123456").build();
 * NotificationRequest.alimtalk("01012345678", "WELCOME_01").variable("name","홍길동").build();
 * }</pre>
 */
public final class NotificationRequest {

    private final ChannelType channel;
    private final String to;
    private final String subject;
    private final String content;
    private final boolean html;
    private final String from;
    private final String templateCode;
    private final Map<String, String> variables;

    private NotificationRequest(Builder b) {
        if (b.channel == null) {
            throw new IllegalArgumentException("channel 은 필수입니다.");
        }
        if (b.to == null || b.to.isBlank()) {
            throw new IllegalArgumentException("to(수신자) 는 필수입니다.");
        }
        this.channel = b.channel;
        this.to = b.to;
        this.subject = b.subject;
        this.content = b.content;
        this.html = b.html;
        this.from = b.from;
        this.templateCode = b.templateCode;
        this.variables = Collections.unmodifiableMap(new LinkedHashMap<>(b.variables));
    }

    public static Builder builder(ChannelType channel, String to) {
        return new Builder(channel, to);
    }

    public static Builder mail(String to, String subject, String content) {
        return new Builder(ChannelType.MAIL, to).subject(subject).content(content);
    }

    public static Builder sms(String to, String content) {
        return new Builder(ChannelType.SMS, to).content(content);
    }

    public static Builder alimtalk(String to, String templateCode) {
        return new Builder(ChannelType.ALIMTALK, to).templateCode(templateCode);
    }

    public ChannelType channel() {
        return channel;
    }

    public String to() {
        return to;
    }

    public String subject() {
        return subject;
    }

    public String content() {
        return content;
    }

    public boolean html() {
        return html;
    }

    public String from() {
        return from;
    }

    public String templateCode() {
        return templateCode;
    }

    public Map<String, String> variables() {
        return variables;
    }

    public static final class Builder {
        private final ChannelType channel;
        private final String to;
        private String subject;
        private String content;
        private boolean html = false;
        private String from;
        private String templateCode;
        private final Map<String, String> variables = new LinkedHashMap<>();

        private Builder(ChannelType channel, String to) {
            this.channel = channel;
            this.to = to;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder html(boolean html) {
            this.html = html;
            return this;
        }

        public Builder from(String from) {
            this.from = from;
            return this;
        }

        public Builder templateCode(String templateCode) {
            this.templateCode = templateCode;
            return this;
        }

        public Builder variable(String key, String value) {
            this.variables.put(key, value);
            return this;
        }

        public Builder variables(Map<String, String> variables) {
            if (variables != null) {
                this.variables.putAll(variables);
            }
            return this;
        }

        public NotificationRequest build() {
            return new NotificationRequest(this);
        }
    }
}
