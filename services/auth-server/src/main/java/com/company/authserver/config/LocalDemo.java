package com.company.authserver.config;

import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.Authenticator;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

/**
 * 로컬 전용(@Profile("local")) 데모 자산. ⚠️ 운영 미사용.
 *
 * <ul>
 *   <li>데모 {@link Authenticator}: demo/demo 로그인 1건(실서비스는 DB/LDAP 구현 빈 주입).
 *   <li>데모 클라이언트 2종: public(authorization_code+PKCE) · confidential(client_credentials). 코드로 등록해 settings JSON 형식을 보장.
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
public class LocalDemo {

    /** 데모 인증기 — demo/demo. 실서비스는 이 빈을 프로젝트 구현으로 교체. */
    @Bean
    Authenticator demoAuthenticator() {
        return command -> {
            if ("demo".equals(command.loginId()) && "demo".equals(command.password())) {
                return new AuthenticatedUser("demo", "데모 사용자", List.of("USER"));
            }
            throw new IllegalArgumentException("로그인 실패(demo/demo 만 허용).");
        };
    }

    /** 데모 클라이언트 등록(없으면 1회). */
    @Bean
    ApplicationRunner seedDemoClients(RegisteredClientRepository repo, PasswordEncoder encoder) {
        return args -> {
            if (repo.findByClientId("demo-web") == null) {
                RegisteredClient web = RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId("demo-web")
                        .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) // public + PKCE
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                        .redirectUri("http://127.0.0.1:8081/login/oauth2/code/demo-web")
                        .scope(OidcScopes.OPENID)
                        .scope(OidcScopes.PROFILE)
                        .clientSettings(ClientSettings.builder()
                                .requireProofKey(true) // PKCE 필수
                                .build())
                        .build();
                repo.save(web);
            }
            if (repo.findByClientId("demo-service") == null) {
                RegisteredClient svc = RegisteredClient.withId(UUID.randomUUID().toString())
                        .clientId("demo-service")
                        .clientSecret(encoder.encode("demo-secret"))
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .scope("api.read")
                        .build();
                repo.save(svc);
            }
        };
    }
}
