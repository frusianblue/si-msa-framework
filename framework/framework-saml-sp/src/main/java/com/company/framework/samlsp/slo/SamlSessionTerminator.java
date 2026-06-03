package com.company.framework.samlsp.slo;

/**
 * 우리 사용자 id 의 전 세션(토큰)을 무효화하는 좁은 계약. SAML SLO 오케스트레이션({@link SamlSloService})이
 * framework-security 의 {@code LoginService} 에 직접 결합되지 않도록 분리한다(단위테스트에서 fake 주입 용이,
 * SAML 전용 앱이 자체 무효화 구현을 끼울 수 있는 확장점).
 *
 * <p>기본 구현은 오토컨피그에서 {@code LoginService.logoutAllByUserId(userId, null, reason)} 로 위임한다
 * ({@code LoginService} 가 있을 때만 — 비밀번호 로그인 {@code Authenticator} 가 없는 SAML 전용 앱은 이 빈을 직접 등록).
 */
@FunctionalInterface
public interface SamlSessionTerminator {

    /**
     * @param userId 무효화 대상 사용자 id
     * @param reason 감사 사유(예: {@code "saml-slo"})
     * @return 무효화한 세션 수(레지스트리 미사용 등으로 열거 불가 시 0)
     */
    int terminateAll(String userId, String reason);
}
