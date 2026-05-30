package com.company.framework.mybatis.handler;

import java.time.LocalDateTime;

/**
 * 공통 감사 컬럼을 담는 베이스 엔티티. (created_at, created_by, updated_at, updated_by)
 * MyBatis 환경에서는 INSERT/UPDATE SQL 에서 이 필드를 채워 넣는다.
 */
public abstract class BaseEntity {
    protected LocalDateTime createdAt;
    protected String createdBy;
    protected LocalDateTime updatedAt;
    protected String updatedBy;

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
