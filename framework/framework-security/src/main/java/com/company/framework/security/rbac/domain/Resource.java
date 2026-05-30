package com.company.framework.security.rbac.domain;

/**
 * 보호 대상 리소스(URL 패턴 + HTTP 메서드)와 접근 허용 역할.
 * 예) /api/v1/users/**, GET, ROLE_USER
 */
public class Resource {
    private Long id;
    private String urlPattern; // Ant 패턴
    private String httpMethod; // GET/POST/... or ALL
    private String roleName; // 접근 허용 역할 (ROLE_*)
    private int sortOrder;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUrlPattern() {
        return urlPattern;
    }

    public void setUrlPattern(String urlPattern) {
        this.urlPattern = urlPattern;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
