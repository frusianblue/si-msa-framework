package com.company.framework.file.dto;

public record FileMetaDto(Long id, String originalName, String contentType, long size, String storageType) {}
