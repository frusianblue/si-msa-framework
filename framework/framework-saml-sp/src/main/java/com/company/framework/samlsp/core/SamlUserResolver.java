package com.company.framework.samlsp.core;

import com.company.framework.security.auth.AuthenticatedUser;

/**
 * SAML 로그인에서 프로젝트가 구현하는 단 하나의 계약. 외부 IdP 가 검증해 보낸 신원({@link SamlUserInfo})을
 * 우리 시스템 사용자({@link AuthenticatedUser})로 매핑한다. (소셜 로그인의 {@code OAuthUserResolver},
 * 자체 비밀번호 로그인의 {@code Authenticator} 와 대칭.)
 *
 * <p>전형적 구현:
 *
 * <ul>
 *   <li>registrationId + nameId 로 연동 계정 조회 → 있으면 그 사용자 반환
 *   <li>없으면 정책에 따라 즉시 가입(JIT provisioning) 또는 email 로 기존 계정 연동 후 반환
 *   <li>가입/연동 불가면 {@code BusinessException(UNAUTHORIZED)} 등을 던진다(로그인 거부)
 * </ul>
 *
 * <p>이 빈을 등록해야 SAML SP 오토컨피그가 활성화된다(빈 미등록 = SAML 로그인 비활성).
 */
public interface SamlUserResolver {
    AuthenticatedUser resolve(SamlUserInfo userInfo);
}
