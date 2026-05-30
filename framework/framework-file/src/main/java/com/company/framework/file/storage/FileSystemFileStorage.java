package com.company.framework.file.storage;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 로컬 디스크 / NAS(마운트 경로) 공용 파일 저장소.
 * 저장 파일명은 UUID 로 생성(원본명은 메타에만 보존) → 경로조작/충돌 방지.
 * 날짜 디렉터리(yyyy/MM/dd)로 분산 저장.
 */
public class FileSystemFileStorage implements FileStorage {

    private final Path basePath;
    private final String type;

    public FileSystemFileStorage(String basePath, String type) {
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
        this.type = type;
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장 기본경로 생성 실패: " + this.basePath, e);
        }
    }

    @Override
    public StoredFile store(InputStream content, String originalName, String contentType, long size) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String ext = extOf(originalName);
        String storedName = UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);
        String relative = datePath + "/" + storedName;

        Path target = basePath.resolve(relative).normalize();
        if (!target.startsWith(basePath)) {                 // 경로조작 방어
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "잘못된 저장 경로입니다.");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("파일 저장 실패", e);
        }
        return new StoredFile(relative, originalName, contentType, size);
    }

    @Override
    public InputStream load(String storedPath) {
        Path target = basePath.resolve(storedPath).normalize();
        if (!target.startsWith(basePath) || !Files.exists(target)) {
            throw new BusinessException(ErrorCode.Common.NOT_FOUND, "파일을 찾을 수 없습니다.");
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new IllegalStateException("파일 읽기 실패", e);
        }
    }

    @Override
    public void delete(String storedPath) {
        Path target = basePath.resolve(storedPath).normalize();
        if (target.startsWith(basePath)) {
            try { Files.deleteIfExists(target); } catch (IOException ignored) { }
        }
    }

    @Override
    public String type() { return type; }

    private String extOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return (dot < 0) ? "" : name.substring(dot + 1).toLowerCase();
    }
}
