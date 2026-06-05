package com.company.authserver.user;

/**
 * auth-server 자체 사용자(authdb {@code app_user}). DbAuthenticator 가 로그인 검증에 사용한다.
 * MyBatis 자동매핑(컬럼 {@code login_id AS loginId} alias) — auth-server 는 map-underscore 미설정이라 SQL 에서 alias.
 */
public class AppUser {

    private Long id;
    private String loginId;
    private String password;
    private String name;
    private String role;
    private boolean enabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
