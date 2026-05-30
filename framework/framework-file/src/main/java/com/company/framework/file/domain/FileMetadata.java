package com.company.framework.file.domain;

import com.company.framework.mybatis.handler.BaseEntity;

public class FileMetadata extends BaseEntity {
    private Long id;
    private String originalName;
    private String storedPath;
    private String contentType;
    private long size;
    private String storageType; // local/nas/s3

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String v) {
        this.originalName = v;
    }

    public String getStoredPath() {
        return storedPath;
    }

    public void setStoredPath(String v) {
        this.storedPath = v;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String v) {
        this.contentType = v;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String v) {
        this.storageType = v;
    }
}
