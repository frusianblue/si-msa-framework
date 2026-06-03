package com.company.framework.core.crypto;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

/**
 * {@code application*.yml}/env/시스템 프로퍼티 안의 {@code ENC(...)} 암호문을 기동 시 자동 복호화하는
 * {@link EnvironmentPostProcessor}.
 *
 * <p><b>왜 빈이 아니라 EPP 인가</b>: 프로퍼티는 {@code ApplicationContext} 생성 이전에 바인딩되므로
 * 빈으로는 늦다. EPP 는 {@code ConfigData}(application.yml) 적재 이후 실행되어, 모든 소스가 존재할 때
 * 복호화 래퍼를 씌울 수 있다. EPP 단계에선 빈을 쓸 수 없으므로 {@link AesCryptoService} 를 직접 생성한다.
 *
 * <p><b>동작</b>:
 * <ol>
 *   <li>토글 {@code framework.crypto.config-decryption.enabled}(기본 {@code true}) 가 꺼져 있으면 무동작.</li>
 *   <li>어떤 소스에도 {@code ENC(...)} 가 없으면 무동작(래퍼/마스터키 불필요).</li>
 *   <li>{@code ENC(...)} 가 있으면 마스터 키 {@code framework.crypto.aes-secret}(운영: {@code AES_SECRET})로
 *       {@link AesCryptoService} 를 만들어 각 {@link EnumerablePropertySource} 를
 *       {@link DecryptingPropertySource} 로 교체한다(지연 복호화).</li>
 * </ol>
 *
 * <p><b>실패 정책</b>: {@code ENC(...)} 가 있는데 마스터 키가 없거나 마스터 키 자체가 {@code ENC(...)}(닭-달걀)면
 * 명확한 예외로 기동을 멈춘다. 잘못된 키/조작된 암호문은 복호화 시점(GCM 인증)에 기동을 멈춘다.
 * 예외 메시지에 평문/키를 노출하지 않는다.
 *
 * <p><b>토글 기본값(true) 결정 근거</b>: 프레임워크의 일반 규약은 "토글 기본 off" 이지만, 본 기능은
 * {@code ENC(...)} 가 없으면 완전한 무동작이라 켜 둬도 부작용이 없고, 오히려 off 기본일 경우 토글을 잊으면
 * {@code ENC(...)} 리터럴이 그대로 다운스트림에 흘러 조용히 깨진다. 따라서 안전 측면에서 기본 {@code true}.
 *
 * <p>등록은 {@code META-INF/spring.factories} 의 {@code org.springframework.boot.EnvironmentPostProcessor}
 * 키로 한다(오토컨피그 {@code .imports} 가 아님 — 컨텍스트 이전에 동작). 키 한 글자만 틀려도 조용히 미등록된다.
 */
public class EncryptedPropertyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String TOGGLE_KEY = "framework.crypto.config-decryption.enabled";
    static final String MASTER_KEY = "framework.crypto.aes-secret";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 1) 토글 (기본 true)
        Boolean enabled = environment.getProperty(TOGGLE_KEY, Boolean.class, Boolean.TRUE);
        if (!Boolean.TRUE.equals(enabled)) {
            return;
        }

        MutablePropertySources sources = environment.getPropertySources();

        // 2) ENC() 사전 스캔 — 없으면 래퍼/마스터키 모두 불필요(완전 무동작)
        if (!containsEncrypted(sources)) {
            return;
        }

        // 3) 마스터 키 해석 (placeholder ${AES_SECRET:...} 는 이 시점에 해석됨)
        String masterKey = environment.getProperty(MASTER_KEY);
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("[crypto] 암호화된 설정값(ENC(...))이 존재하지만 마스터 키가 없습니다 — " + MASTER_KEY
                    + " (운영 환경변수 AES_SECRET) 에 마스터 키를 주입하세요.");
        }
        if (DecryptingPropertySource.isEncrypted(masterKey)) {
            // 닭-달걀: 마스터 키 자신은 ENC() 로 둘 수 없다(평문 주입 필수).
            throw new IllegalStateException(
                    "[crypto] 마스터 키(" + MASTER_KEY + ")는 ENC(...) 로 둘 수 없습니다 — 평문(env/시크릿)으로 주입하세요.");
        }

        AesCryptoService aes = new AesCryptoService(masterKey);

        // 4) 각 enumerable 소스를 복호화 래퍼로 교체 (순회 중 구조변경 회피 위해 먼저 수집)
        List<EnumerablePropertySource<?>> targets = new ArrayList<>();
        for (PropertySource<?> source : sources) {
            if (source instanceof EnumerablePropertySource<?> eps && !DecryptingPropertySource.isWrapped(source)) {
                targets.add(eps);
            }
        }
        for (EnumerablePropertySource<?> eps : targets) {
            sources.replace(eps.getName(), new DecryptingPropertySource(eps, aes));
        }
    }

    /** 어느 enumerable 소스든 {@code ENC(...)} 값이 하나라도 있으면 true. */
    private static boolean containsEncrypted(MutablePropertySources sources) {
        for (PropertySource<?> source : sources) {
            if (source instanceof EnumerablePropertySource<?> eps && !DecryptingPropertySource.isWrapped(source)) {
                for (String name : eps.getPropertyNames()) {
                    if (DecryptingPropertySource.isEncrypted(eps.getProperty(name))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        // ConfigData(application.yml) 적재 이후에 실행되도록 최저 우선순위. 모든 소스가 존재할 때 래핑.
        return Ordered.LOWEST_PRECEDENCE;
    }
}
