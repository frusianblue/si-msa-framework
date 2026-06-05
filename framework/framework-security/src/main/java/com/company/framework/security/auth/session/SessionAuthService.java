package com.company.framework.security.auth.session;

import com.company.framework.security.auth.AuthenticatedUser;
import com.company.framework.security.auth.Authenticator;
import com.company.framework.security.auth.LoginAuditEvent;
import com.company.framework.security.auth.LoginCommand;
import com.company.framework.security.loginattempt.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * 서버 세션 기반 로그인의 핵심 흐름({@code framework.security.session.mode=session} 일 때만 활성).
 *
 * <p>JWT 경로의 {@code LoginService} 와 동일하게 프로젝트의 단일 인증 계약 {@link Authenticator} 를 재사용하되,
 * 토큰을 발급하는 대신 {@link SecurityContext} 를 {@code HttpSession} 에 저장해 서버 세션을 수립한다. 권한은 JWT
 * 경로와 동일한 {@code ROLE_*} 형태로 매핑하므로 RBAC(동적 인가)·{@code @PreAuthorize} 가 두 모드에서 동일하게 동작한다.
 *
 * <p>로그인 잠금({@link LoginAttemptService}, ISMS-P)도 동일하게 적용한다. 세션 고정(fixation) 공격 방지를 위해
 * 인증 성공 시 기존 세션ID 를 회전({@code changeSessionId})한다 — 인증 필터가 아닌 컨트롤러에서 로그인하므로
 * 시큐리티의 자동 세션 고정 보호가 발동하지 않기 때문에 명시적으로 처리한다.
 */
public class SessionAuthService {

    private final Authenticator authenticator;
    private final LoginAttemptService loginAttempts;
    private final SecurityContextRepository securityContextRepository;
    private final ApplicationEventPublisher events; // nullable — framework-audit 있으면 적재
    private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

    public SessionAuthService(
            Authenticator authenticator,
            LoginAttemptService loginAttempts,
            SecurityContextRepository securityContextRepository,
            ApplicationEventPublisher events) {
        this.authenticator = authenticator;
        this.loginAttempts = loginAttempts;
        this.securityContextRepository = securityContextRepository;
        this.events = events;
    }

    /**
     * 1차 인증 후 서버 세션을 수립한다. 실패는 {@link Authenticator} 가 던지는 예외(BusinessException 등)로 전파된다.
     *
     * @param lockKey 잠금 카운터 키(계정 또는 계정+IP). null 이면 잠금 미적용.
     * @param clientIp 접속 감사(LoginAuditEvent)용 IP. null 허용.
     * @return 수립된 사용자(응답 바디용; 토큰은 없음)
     */
    public AuthenticatedUser login(
            LoginCommand command,
            String lockKey,
            String clientIp,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (lockKey != null) {
            loginAttempts.assertNotLocked(lockKey);
        }
        AuthenticatedUser user;
        try {
            user = authenticator.authenticate(command);
        } catch (RuntimeException ex) {
            if (lockKey != null) {
                loginAttempts.recordFailure(lockKey);
            }
            publish(LoginAuditEvent.Type.LOGIN_FAILURE, command.loginId(), clientIp, ex.getMessage());
            throw ex;
        }
        if (lockKey != null) {
            loginAttempts.reset(lockKey);
        }

        // 세션 고정 방지: 기존(익명) 세션이 있으면 세션ID 회전.
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            request.changeSessionId();
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(toPrincipal(user), "N/A", toAuthorities(user.roles()));
        SecurityContext context = holderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        holderStrategy.setContext(context);
        // HttpSessionSecurityContextRepository 가 HttpSession(없으면 생성)에 컨텍스트를 저장 → 세션 쿠키 발급.
        securityContextRepository.saveContext(context, request, response);
        publish(LoginAuditEvent.Type.LOGIN_SUCCESS, command.loginId(), clientIp, null);
        return user;
    }

    /** 현재 서버 세션을 무효화하고 보안 컨텍스트를 비운다. */
    public void logout(String clientIp, HttpServletRequest request) {
        String loginId = currentLoginId();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        holderStrategy.clearContext();
        publish(LoginAuditEvent.Type.LOGOUT, loginId, clientIp, null);
    }

    private String currentLoginId() {
        var ctx = holderStrategy.getContext();
        if (ctx != null && ctx.getAuthentication() != null) {
            return ctx.getAuthentication().getName();
        }
        return null;
    }

    private void publish(LoginAuditEvent.Type type, String loginId, String clientIp, String detail) {
        if (events != null) {
            events.publishEvent(LoginAuditEvent.of(type, loginId, clientIp, detail));
        }
    }

    private User toPrincipal(AuthenticatedUser user) {
        return new User(user.userId(), "", toAuthorities(user.roles()));
    }

    /** JWT 경로(JwtProvider/게이트웨이 헤더)와 동일하게 ROLE_ 접두어를 보장한다. */
    private List<GrantedAuthority> toAuthorities(List<String> roles) {
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .filter(r -> r != null && !r.isBlank())
                .map(String::trim)
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                .map(SimpleGrantedAuthority::new)
                .map(a -> (GrantedAuthority) a)
                .toList();
    }
}
