package com.company.gateway.auth;

import reactor.core.publisher.Mono;

/**
 * 엣지에서 access token 의 jti 가 <b>로그아웃(무효화)</b> 되었는지 논블로킹으로 조회한다.
 *
 * <p>중앙 로그아웃(SSO)의 핵심: 한 서비스가 {@code LoginService.logout} 으로 jti 를 공유 저장소에 블랙리스트
 * 처리하면, 게이트웨이가 그 jti 를 즉시 차단해 <b>전 서비스에서</b> 토큰이 무효가 된다(서명/만료만으로는
 * 차단 불가 — 토큰 자체는 아직 유효 기간 내이기 때문).
 *
 * <p>게이트웨이는 WebFlux 라 블로킹 IO 가 금지된다. 따라서 구현은 reactive(예: ReactiveStringRedisTemplate)
 * 여야 한다. 블랙리스트 검사 비활성 시에는 {@link NoOpGatewayTokenBlacklist}(항상 통과)를 사용한다.
 */
public interface GatewayTokenBlacklist {

    /**
     * @param jti access token 의 jti 클레임(null/blank 가능 — 그 경우 개별 무효화 대상이 아니므로 false)
     * @return 무효화(로그아웃) 되었으면 {@code true}
     */
    Mono<Boolean> isBlacklisted(String jti);
}
