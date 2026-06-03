package com.company.gateway.auth;

import reactor.core.publisher.Mono;

/**
 * 블랙리스트 검사 비활성({@code gateway.auth.blacklist-check.enabled=false}, 기본) 시 사용.
 * 항상 통과시켜 기존 동작(서명+만료+typ 만 검증)을 그대로 유지한다 — Redis 의존 없음.
 */
public class NoOpGatewayTokenBlacklist implements GatewayTokenBlacklist {

    @Override
    public Mono<Boolean> isBlacklisted(String jti) {
        return Mono.just(false);
    }
}
