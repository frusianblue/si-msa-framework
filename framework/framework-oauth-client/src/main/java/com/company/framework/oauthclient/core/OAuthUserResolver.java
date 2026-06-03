package com.company.framework.oauthclient.core;

import com.company.framework.security.auth.AuthenticatedUser;

/**
 * 소셜 로그인에서 프로젝트가 구현하는 단 하나의 계약. 외부 신원({@link OAuthUserInfo})을 우리 시스템의
 * 사용자({@link AuthenticatedUser})로 매핑한다. (자체 비밀번호 로그인의 {@code Authenticator} 와 대칭.)
 *
 * <p>전형적 구현:
 *
 * <ul>
 *   <li>provider + providerId 로 연동 계정 조회 → 있으면 그 사용자 반환
 *   <li>없으면 정책에 따라 즉시 가입(JIT provisioning) 또는 이메일로 기존 계정 연동 후 반환
 *   <li>가입/연동이 불가하면 {@code BusinessException(UNAUTHORIZED)} 등을 던진다(로그인 거부)
 * </ul>
 *
 * <p>이 빈을 등록해야 OAuth 오토컨피그가 활성화된다(빈 미등록 = 소셜 로그인 비활성).
 */
public interface OAuthUserResolver {
    AuthenticatedUser resolve(OAuthUserInfo userInfo);
}
