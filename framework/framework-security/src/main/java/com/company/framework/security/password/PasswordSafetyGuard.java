package com.company.framework.security.password;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

/**
 * 운영 안전장치(dev-auth 가드와 동일 패턴).
 * prod 프로파일에서 allow-noop=true 면 평문 비밀번호가 허용되는 상태이므로 경고 배너를 남긴다.
 * (부팅 실패까지 시키면 기존 {noop} 시드가 깔린 환경을 깰 수 있어 경고로만 처리)
 */
public class PasswordSafetyGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(PasswordSafetyGuard.class);

    private final PasswordProperties props;
    private final Environment env;

    public PasswordSafetyGuard(PasswordProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    @Override
    public void afterPropertiesSet() {
        boolean prod = Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));
        if (prod && props.isAllowNoop()) {
            log.warn("\n" + "============================================================\n"
                    + " ★★★  PASSWORD: allow-noop=true 상태입니다  ★★★\n"
                    + "   운영에서는 framework.security.password.allow-noop=false 로\n"
                    + "   설정해 평문({noop}) 비밀번호를 차단하고 BCrypt 를 강제하세요.\n"
                    + "============================================================");
        }
    }
}
