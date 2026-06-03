package com.company.framework.security.jwt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

/** prod 에서 약한 JWT 시크릿을 부팅 단계에서 차단하는지 검증. */
class JwtSecretSafetyGuardTest {

    private static final String STRONG = "X".repeat(40); // 40바이트, 미교체 흔적 없음

    private JwtSecretSafetyGuard guard(String secret, String... profiles) {
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles(profiles);
        return new JwtSecretSafetyGuard(new JwtProperties(secret, 1800, 1209600), env);
    }

    @Test
    void prod_기본_placeholder_시크릿이면_부팅실패() {
        assertThrows(IllegalStateException.class, () -> guard(JwtSecretSafetyGuard.DEFAULT_SECRET, "prod")
                .afterPropertiesSet());
    }

    @Test
    void prod_빈_시크릿이면_부팅실패() {
        assertThrows(IllegalStateException.class, () -> guard("   ", "prod").afterPropertiesSet());
    }

    @Test
    void prod_너무_짧은_시크릿이면_부팅실패() {
        assertThrows(
                IllegalStateException.class, () -> guard("short-key", "prod").afterPropertiesSet());
    }

    @Test
    void prod_change_me_흔적이_남아있으면_부팅실패() {
        assertThrows(
                IllegalStateException.class, () -> guard("user-project-aes-secret-change-me-padding-padding", "prod")
                        .afterPropertiesSet());
    }

    @Test
    void prod_강한_시크릿이면_통과() {
        assertDoesNotThrow(() -> guard(STRONG, "prod").afterPropertiesSet());
    }

    @Test
    void local_은_약한_시크릿이어도_경고만_통과() {
        assertDoesNotThrow(
                () -> guard(JwtSecretSafetyGuard.DEFAULT_SECRET, "local").afterPropertiesSet());
    }
}
