package com.company.framework.core.crypto;

import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

/**
 * 원본 {@link EnumerablePropertySource} 를 감싸, 값을 읽는 시점에 {@code ENC(...)} 토큰만 지연 복호화하는 래퍼.
 *
 * <p>이른 시점 일괄 치환 대신 "값을 읽을 때 복호화"하는 지연 방식이라, 프로파일별 yaml 이 나중에 들어와도
 * 누락되지 않는다. {@code ENC(...)} 가 아닌 값은 그대로 위임한다.
 *
 * <p>바인딩이 동작하려면 {@link #getPropertyNames()} 도 원본에 위임해야 한다(누락 시 Binder 가 ENC 값을
 * 발견하지 못함). 원본의 이름 해석 규칙(예: {@code SystemEnvironmentPropertySource} 의 완화된 매칭)도
 * {@code getProperty}/{@code getPropertyNames} 를 그대로 위임하므로 보존된다.
 *
 * <p>복호화 실패는 {@link AesCryptoService} 가 던지는 {@link IllegalStateException} 으로 전파되어 기동을
 * 멈춘다(GCM 인증 — 깨진/조작된 값이 조용히 통과하지 않음). 복호화된 평문은 로그로 남기지 않는다.
 */
final class DecryptingPropertySource extends EnumerablePropertySource<EnumerablePropertySource<?>> {

    static final String PREFIX = "ENC(";
    static final String SUFFIX = ")";

    private final AesCryptoService aes;

    DecryptingPropertySource(EnumerablePropertySource<?> delegate, AesCryptoService aes) {
        super(delegate.getName(), delegate);
        this.aes = aes;
    }

    @Override
    public String[] getPropertyNames() {
        return getSource().getPropertyNames();
    }

    @Override
    public Object getProperty(String name) {
        Object value = getSource().getProperty(name);
        if (isEncrypted(value)) {
            String token = ((String) value);
            String cipher = token.substring(PREFIX.length(), token.length() - SUFFIX.length());
            return aes.decrypt(cipher); // 실패 시 IllegalStateException → 기동 실패(의도)
        }
        return value;
    }

    /** {@code ENC(...)} 형태의 문자열인지 판정(빈 토큰 {@code ENC()} 은 복호화 단계에서 실패시킨다). */
    static boolean isEncrypted(Object value) {
        return value instanceof String s && s.startsWith(PREFIX) && s.endsWith(SUFFIX) && s.length() > PREFIX.length();
    }

    /** 이미 감싼 소스를 다시 감싸지 않도록 식별용. */
    static boolean isWrapped(PropertySource<?> source) {
        return source instanceof DecryptingPropertySource;
    }
}
