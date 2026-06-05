package com.company.framework.session.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

/**
 * 운영 명료성 가드(JwtSecretSafetyGuard 와 동일 사상). {@code framework-session}(클러스터 세션 저장소)이 적재됐다는 것은
 * "다중 인스턴스 간 HttpSession 공유"를 의도한 것이다. 그런데 보안 상태관리 모드가 여전히 {@code stateless}(JWT) 라면
 * 이 모듈은 사실상 무의미하게 매달려 있는 것이므로(흔한 오설정), 부팅 시 경고로 알린다.
 *
 * <p>반대 방향(=mode=session 인데 이 모듈이 없어 톰캣 로컬 세션 → 파드 재시작/로드밸런싱 시 세션 유실)은 클래스패스에
 * 이 모듈이 없으므로 여기서 잡을 수 없다. 그 경고는 문서/PITFALLS 로 안내한다.
 */
public class SessionStoreSafetyGuard {

    private static final Logger log = LoggerFactory.getLogger(SessionStoreSafetyGuard.class);

    public SessionStoreSafetyGuard(FrameworkSessionProperties props, Environment env) {
        if (!props.isWarnIfModeStateless()) {
            return;
        }
        String mode = env.getProperty("framework.security.session.mode", "stateless");
        if (!"session".equalsIgnoreCase(mode)) {
            log.warn(
                    "[framework-session] 클러스터 세션 저장소(Spring Session Redis)가 적재됐지만 "
                            + "framework.security.session.mode='{}' 입니다(세션 모드 아님). 서버 세션 공유를 의도했다면 "
                            + "framework.security.session.mode=session 으로 설정하세요. (이 경고는 framework.session.warn-if-mode-stateless=false 로 끌 수 있습니다)",
                    mode);
        } else {
            log.info("[framework-session] 서버 세션을 Redis 로 외부화 — 다중 인스턴스 간 HttpSession 공유 활성.");
        }
    }
}
