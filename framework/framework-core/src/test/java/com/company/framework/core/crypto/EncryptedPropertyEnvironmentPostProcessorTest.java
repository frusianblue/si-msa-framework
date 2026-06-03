package com.company.framework.core.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** ENC(...) 설정값 복호화 EnvironmentPostProcessor 단위 테스트. */
class EncryptedPropertyEnvironmentPostProcessorTest {

    private static final String MASTER = "unit-test-master-key-32bytes-strong!";

    private final EncryptedPropertyEnvironmentPostProcessor epp = new EncryptedPropertyEnvironmentPostProcessor();

    private static String enc(String key, String plain) {
        return "ENC(" + new AesCryptoService(key).encrypt(plain) + ")";
    }

    private StandardEnvironment envWith(Map<String, Object> props) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", new LinkedHashMap<>(props)));
        return env;
    }

    @Test
    @DisplayName("ENC(...) 값은 복호화되고, 평문 값은 그대로 유지된다")
    void decryptsEncAndKeepsPlain() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(EncryptedPropertyEnvironmentPostProcessor.MASTER_KEY, MASTER);
        props.put("spring.datasource.password", enc(MASTER, "sipass"));
        props.put("plain.value", "literal");
        StandardEnvironment env = envWith(props);

        epp.postProcessEnvironment(env, null);

        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("sipass");
        assertThat(env.getProperty("plain.value")).isEqualTo("literal");
    }

    @Test
    @DisplayName("바인딩을 위해 래퍼가 getPropertyNames 를 보존한다")
    void preservesPropertyNames() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(EncryptedPropertyEnvironmentPostProcessor.MASTER_KEY, MASTER);
        props.put("a.secret", enc(MASTER, "x"));
        StandardEnvironment env = envWith(props);

        epp.postProcessEnvironment(env, null);

        assertThat(env.getPropertySources().get("test")).isInstanceOf(DecryptingPropertySource.class);
        DecryptingPropertySource wrapped =
                (DecryptingPropertySource) env.getPropertySources().get("test");
        assertThat(wrapped.getPropertyNames())
                .contains("a.secret", EncryptedPropertyEnvironmentPostProcessor.MASTER_KEY);
    }

    @Test
    @DisplayName("ENC 가 하나도 없으면 무동작(래퍼 미적용, 마스터키 불필요)")
    void noEncMeansNoOp() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("plain.value", "literal"); // 마스터 키조차 없음
        StandardEnvironment env = envWith(props);

        epp.postProcessEnvironment(env, null);

        assertThat(env.getPropertySources().get("test")).isNotInstanceOf(DecryptingPropertySource.class);
        assertThat(env.getProperty("plain.value")).isEqualTo("literal");
    }

    @Test
    @DisplayName("ENC 가 있는데 마스터 키가 없으면 명확히 기동 실패")
    void encWithoutMasterKeyFails() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.datasource.password", enc(MASTER, "sipass")); // aes-secret 미주입
        StandardEnvironment env = envWith(props);

        assertThatThrownBy(() -> epp.postProcessEnvironment(env, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("마스터 키가 없습니다");
    }

    @Test
    @DisplayName("마스터 키 자신이 ENC(...) 이면(닭-달걀) 기동 실패")
    void masterKeyAsEncFails() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(EncryptedPropertyEnvironmentPostProcessor.MASTER_KEY, "ENC(should-not-be-allowed)");
        StandardEnvironment env = envWith(props);

        assertThatThrownBy(() -> epp.postProcessEnvironment(env, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("평문");
    }

    @Test
    @DisplayName("잘못된 키로 암호화된 값은 읽는 시점에 복호화 실패(조용히 통과하지 않음)")
    void wrongKeyFailsOnRead() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(EncryptedPropertyEnvironmentPostProcessor.MASTER_KEY, "the-wrong-master-key-aaaaaaaaaa");
        props.put("spring.datasource.password", enc(MASTER, "sipass")); // 다른 키로 암호화됨
        StandardEnvironment env = envWith(props);

        epp.postProcessEnvironment(env, null); // 래핑은 성공(사전 스캔은 복호화 안 함)

        assertThatThrownBy(() -> env.getProperty("spring.datasource.password"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("복호화 실패");
    }

    @Test
    @DisplayName("토글 off 면 ENC 값을 복호화하지 않고 리터럴 그대로 둔다")
    void toggleOffKeepsLiteral() {
        String token = enc(MASTER, "sipass");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put(EncryptedPropertyEnvironmentPostProcessor.TOGGLE_KEY, "false");
        props.put(EncryptedPropertyEnvironmentPostProcessor.MASTER_KEY, MASTER);
        props.put("spring.datasource.password", token);
        StandardEnvironment env = envWith(props);

        epp.postProcessEnvironment(env, null);

        assertThat(env.getProperty("spring.datasource.password")).isEqualTo(token);
        assertThat(env.getPropertySources().get("test")).isNotInstanceOf(DecryptingPropertySource.class);
    }
}
