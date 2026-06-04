package com.company.framework.file.sftp.cred;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 자격증명 홀더 불변성·헬퍼. */
class SftpCredentialsTest {

    private static KeyPair rsa() throws Exception {
        return KeyPairGenerator.getInstance("RSA").generateKeyPair();
    }

    @Test
    @DisplayName("password() 헬퍼 + blank/null 비밀번호 미인정")
    void passwordHelper() {
        assertThat(SftpCredentials.password("pw").hasPassword()).isTrue();
        assertThat(SftpCredentials.password("pw").hasKeys()).isFalse();
        assertThat(SftpCredentials.password("   ").hasPassword()).isFalse();
        assertThat(SftpCredentials.password(null).keyPairs()).isEmpty();
    }

    @Test
    @DisplayName("keys() 헬퍼 + 방어적 복사(원본 변형 무영향)")
    void keysHelperDefensiveCopy() throws Exception {
        List<KeyPair> src = new ArrayList<>();
        src.add(rsa());
        SftpCredentials c = SftpCredentials.keys(src);
        assertThat(c.hasKeys()).isTrue();
        assertThat(c.keyPairs()).hasSize(1);
        src.clear();
        assertThat(c.keyPairs()).hasSize(1);
    }

    @Test
    @DisplayName("null keyPairs 는 빈 리스트로 정규화된다")
    void nullKeysNormalized() {
        assertThat(new SftpCredentials("pw", null).keyPairs()).isEmpty();
    }
}
