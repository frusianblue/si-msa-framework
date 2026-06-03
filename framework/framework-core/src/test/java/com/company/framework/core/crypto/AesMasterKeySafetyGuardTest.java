package com.company.framework.core.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

/** AES 마스터 키 prod 가드 단위 테스트 (JWT 가드와 동일 패턴). */
class AesMasterKeySafetyGuardTest {

    private static StandardEnvironment env(String... profiles) {
        StandardEnvironment env = new StandardEnvironment();
        if (profiles.length > 0) {
            env.setActiveProfiles(profiles);
        }
        return env;
    }

    private static CryptoProperties props(String secret) {
        CryptoProperties p = new CryptoProperties();
        p.setAesSecret(secret);
        return p;
    }

    @Test
    @DisplayName("prod + 기본 placeholder 키 → 부팅 실패")
    void prodWithDefaultKeyFails() {
        AesMasterKeySafetyGuard guard =
                new AesMasterKeySafetyGuard(props(AesMasterKeySafetyGuard.DEFAULT_SECRET), env("prod"));
        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("보안 차단");
    }

    @Test
    @DisplayName("prod + 빈 키 → 부팅 실패")
    void prodWithBlankKeyFails() {
        AesMasterKeySafetyGuard guard = new AesMasterKeySafetyGuard(props("  "), env("prod"));
        assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("prod + 너무 짧은 키 → 부팅 실패")
    void prodWithShortKeyFails() {
        AesMasterKeySafetyGuard guard = new AesMasterKeySafetyGuard(props("short"), env("prod"));
        assertThatThrownBy(guard::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("너무 짧");
    }

    @Test
    @DisplayName("prod + 강한 키 → 정상")
    void prodWithStrongKeyOk() {
        AesMasterKeySafetyGuard guard =
                new AesMasterKeySafetyGuard(props("a-strong-master-key-32bytes-long!!"), env("prod"));
        assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("local(비-prod) + 기본 키 → 경고만, 부팅은 통과")
    void nonProdWithDefaultKeyWarnsOnly() {
        AesMasterKeySafetyGuard guard =
                new AesMasterKeySafetyGuard(props(AesMasterKeySafetyGuard.DEFAULT_SECRET), env("local"));
        assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MIN_SECRET_BYTES 경계: 16바이트 키는 통과")
    void boundaryMinBytesOk() {
        String exactly16 = "0123456789abcdef"; // 16 bytes
        assertThat(exactly16.getBytes()).hasSize(AesMasterKeySafetyGuard.MIN_SECRET_BYTES);
        AesMasterKeySafetyGuard guard = new AesMasterKeySafetyGuard(props(exactly16), env("prod"));
        assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
    }
}
