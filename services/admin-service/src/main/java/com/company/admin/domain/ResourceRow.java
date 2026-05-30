package com.company.admin.domain;

import com.company.framework.mybatis.handler.BaseEntity;

/** resources 테이블 행 (감사필드 자동주입 대상) */
public class ResourceRow extends BaseEntity {
    private Long id;
    private String urlPattern;
    private String httpMethod;
    private String descr;
    private int sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUrlPattern() { return urlPattern; }
    public void setUrlPattern(String urlPattern) { this.urlPattern = urlPattern; }
    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }
    public String getDescr() { return descr; }
    public void setDescr(String descr) { this.descr = descr; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
}
