package com.company.authserver.config;

import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

/**
 * prod-안전 스모크/데모 클라이언트 시더 — <b>옵트인</b>(기본 off).
 *
 * <p><b>왜 분리하나</b>: {@link LocalDemo} 의 클라이언트 시드는 {@code @Profile("local")} 이라 dev/prod/K8s 에선 비활성이다.
 * 그래서 prod 프로파일로 뜨는 kind/운영 배포의 {@code oauth2_registered_client} 는 0건이고, {@code /oauth2/authorize} 가
 * {@code invalid_client} 로 막혀 로그인 폼까지 못 간다(=설계: 운영 클라이언트 등록은 프로젝트 책임). 이 시더는 그
 * <b>마지막 한 칸</b>(authorization_code+PKCE 토큰 발급 = {@code DbAuthenticator} 운영 인증 경로)을 닫기 위한
 * <b>프로파일 비의존 옵트인 경로</b>다.
 *
 * <p><b>삼단 토글 정신</b>: {@code framework.auth.seed-smoke-client=true} 일 때만 등록(기본 false). 프로파일을 건드리지
 * 않으므로 {@code ProdAuthenticatorConfig}({@code @Profile("!local")}) 의 {@link
 * org.springframework.security.crypto.password.PasswordEncoder} 검증 {@code DbAuthenticator} 배선은 <b>그대로</b>다.
 *
 * <p>⚠️ <b>SQL INSERT 수동 등록 금지</b>: {@code oauth2_registered_client} 의 {@code client_settings}/{@code
 * token_settings} 는 SAS 전용 Jackson 모듈이 {@code @class} 타입 메타데이터를 박아 직렬화한 JSON 이라, 손으로 만들면
 * {@code JdbcRegisteredClientRepository} 의 row mapper 가 역직렬화에서 깨진다. 반드시 {@link
 * RegisteredClientRepository#save(RegisteredClient)}(앱 코드)로 등록해 올바른 JSON 을 생성한다. (PITFALLS §9)
 *
 * <p><b>등록 클라이언트</b>(LocalDemo 와 동일 식별자·설정 → local 과 prod 의 스모크 자산 일치, 멱등):
 *
 * <ul>
 *   <li>{@code demo-web}: public + PKCE, {@code authorization_code}+{@code refresh_token}, scope {@code openid}/{@code profile}.
 *       redirect_uri 는 RP 없이 curl 로 code 만 수령·토큰교환하는 kind 검증용 값(LocalDemo 와 동일 패턴).
 *   <li>{@code demo-service}: {@code client_secret_basic} + {@code client_credentials}(서버-서버 토큰 1줄 확인용 —
 *       단 이건 클라이언트 인증이지 {@code DbAuthenticator}(사용자 인증) 경로가 아니다).
 * </ul>
 *
 * <p>로드맵 {@code demo-rp}(confidential, {@code client_secret_post}) 전체 콜백 흐름은 이 시더를 출발점으로 확장한다
 * (NEXT_RP_IDTOKEN_LINK §B).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "framework.auth.seed-smoke-client", havingValue = "true")
public class SmokeClientSeeder {

    /** 스모크 클라이언트 등록(없으면 1회). 멱등 — 이미 있으면 건너뛴다(local 과 동시 활성/재기동 안전). */
    @Bean
    ApplicationRunner seedSmokeClients(RegisteredClientRepository repo, PasswordEncoder encoder) {
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
