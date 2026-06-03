package com.company.framework.oauthclient.config;

import com.company.framework.oauthclient.core.OAuthClient;
import com.company.framework.oauthclient.core.OAuthUserResolver;
import com.company.framework.oauthclient.core.ProviderRegistry;
import com.company.framework.oauthclient.oidc.IdTokenVerifier;
import com.company.framework.oauthclient.oidc.JwksKeyResolver;
import com.company.framework.oauthclient.oidc.OidcDiscoveryClient;
import com.company.framework.oauthclient.oidc.OidcMetadataResolver;
import com.company.framework.oauthclient.store.InMemoryOAuthStateStore;
import com.company.framework.oauthclient.store.OAuthStateStore;
import com.company.framework.oauthclient.store.RedisOAuthStateStore;
import com.company.framework.oauthclient.token.DirectOAuthTokenIssuer;
import com.company.framework.oauthclient.token.OAuthTokenIssuer;
import com.company.framework.oauthclient.web.OAuthController;
import com.company.framework.oauthclient.web.OAuthLoginService;
import com.company.framework.security.jwt.JwtProvider;
import com.company.framework.security.token.TokenStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

/**
 * 소셜 로그인(OAuth 2.0 / OIDC) 오토컨피그.
 *
 * <ul>
 *   <li>1단(모듈): {@code @ConditionalOnClass(OAuthLoginService, RestClient)} — 이 모듈 + web 이 있어야 활성.
 *   <li>2단(기능): {@code framework.oauth-client.enabled=true}.
 *   <li>3단(구현): state 저장소 {@code state.store.type=memory|redis}.
 * </ul>
 *
 * <p>추가 가드: {@code @ConditionalOnBean(OAuthUserResolver.class)} — 외부 신원을 우리 사용자로 매핑하는
 * 리졸버를 프로젝트가 등록해야만 활성화된다(자체 로그인의 {@code Authenticator} 패턴과 대칭). 미등록이면
 * 소셜 로그인은 조용히 비활성.
 *
 * <p>토큰 발급은 {@link DirectOAuthTokenIssuer}(security 의 JwtProvider/TokenStore 직접 사용)가 기본이며,
 * 동시 로그인 제어/감사까지 통합하려면 프로젝트가 LoginService 위임 {@link OAuthTokenIssuer} 빈으로 교체할 수 있다.
 */
@AutoConfiguration
@ConditionalOnClass({OAuthLoginService.class, RestClient.class})
@ConditionalOnProperty(prefix = "framework.oauth-client", name = "enabled", havingValue = "true")
@ConditionalOnBean(OAuthUserResolver.class)
@EnableConfigurationProperties(OAuthClientProperties.class)
public class OAuthClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ProviderRegistry oauthProviderRegistry(OAuthClientProperties properties) {
        properties.applyPresets(); // google/kakao/naver 표준 엔드포인트·속성 보충(1회)
        return new ProviderRegistry(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuthClient oauthClient() {
        return new OAuthClient(RestClient.create());
    }

    // ===================== OIDC 강화: discovery / JWKS / id_token 검증 =====================

    @Bean
    @ConditionalOnMissingBean
    public OidcDiscoveryClient oidcDiscoveryClient() {
        return new OidcDiscoveryClient(RestClient.create());
    }

    @Bean
    @ConditionalOnMissingBean
    public OidcMetadataResolver oidcMetadataResolver(OidcDiscoveryClient discoveryClient) {
        return new OidcMetadataResolver(discoveryClient);
    }

    @Bean
    @ConditionalOnMissingBean
    public JwksKeyResolver jwksKeyResolver() {
        return new JwksKeyResolver(RestClient.create(), null); // 캐시 TTL 기본 1시간(회전 시 미발견 kid 재조회)
    }

    @Bean
    @ConditionalOnMissingBean
    public IdTokenVerifier idTokenVerifier(JwksKeyResolver jwksKeyResolver) {
        return new IdTokenVerifier(jwksKeyResolver);
    }

    // ===================== state 저장소: memory | redis =====================

    @Bean
    @ConditionalOnMissingBean(OAuthStateStore.class)
    @ConditionalOnProperty(
            prefix = "framework.oauth-client.state.store",
            name = "type",
            havingValue = "memory",
            matchIfMissing = true)
    public OAuthStateStore inMemoryOAuthStateStore() {
        return new InMemoryOAuthStateStore();
    }

    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnMissingBean(OAuthStateStore.class)
    @ConditionalOnProperty(prefix = "framework.oauth-client.state.store", name = "type", havingValue = "redis")
    public OAuthStateStore redisOAuthStateStore(StringRedisTemplate redisTemplate, OAuthClientProperties properties) {
        return new RedisOAuthStateStore(
                redisTemplate, properties.getState().getStore().getKeyPrefix());
    }

    // ===================== 토큰 발급기(기본: 직접 발급) =====================

    @Bean
    @ConditionalOnMissingBean(OAuthTokenIssuer.class)
    public OAuthTokenIssuer directOAuthTokenIssuer(JwtProvider jwtProvider, TokenStore tokenStore) {
        return new DirectOAuthTokenIssuer(jwtProvider, tokenStore);
    }

    // ===================== 서비스 / 컨트롤러 =====================

    @Bean
    @ConditionalOnMissingBean
    public OAuthLoginService oauthLoginService(
            ProviderRegistry registry,
            OAuthClient client,
            OAuthStateStore stateStore,
            OAuthUserResolver userResolver,
            OAuthTokenIssuer tokenIssuer,
            OidcMetadataResolver metadataResolver,
            IdTokenVerifier idTokenVerifier,
            OAuthClientProperties properties) {
        return new OAuthLoginService(
                registry, client, stateStore, userResolver, tokenIssuer, metadataResolver, idTokenVerifier, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public OAuthController frameworkOAuthController(OAuthLoginService oauthLoginService) {
        return new OAuthController(oauthLoginService);
    }
}
