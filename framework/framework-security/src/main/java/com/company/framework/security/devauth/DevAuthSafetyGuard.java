package com.company.framework.security.devauth;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;

/**
 * dev-auth 오용 방지 안전장치.
 *  - prod 프로파일에서 dev-auth 가 켜져 있으면 부팅을 실패시킨다(운영 사고 차단).
 *  - 활성화 시 눈에 띄는 경고 배너를 남긴다.
 */
public class DevAuthSafetyGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DevAuthSafetyGuard.class);

    private final DevAuthProperties props;
    private final Environment env;

    public DevAuthSafetyGuard(DevAuthProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    @Override
    public void afterPropertiesSet() {
        if (!props.isEnabled()) return;

        boolean prod = Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));
        if (prod) {
            throw new IllegalStateException(
                    "[보안 차단] prod 프로파일에서는 framework.security.dev-auth.enabled=true 를 사용할 수 없습니다. "
                            + "운영 배포 전 dev-auth 를 반드시 비활성화하세요.");
        }
        log.warn(
                "\n" + "============================================================\n"
                        + " ★★★  DEV-AUTH ENABLED — 인증이 우회되고 있습니다  ★★★\n"
                        + "   user-id = {}, roles = {}\n"
                        + "   ※ 로컬 개발 전용. 운영 배포 금지!\n"
                        + "============================================================",
                props.getUserId(),
                props.getRoles());
    }
}
