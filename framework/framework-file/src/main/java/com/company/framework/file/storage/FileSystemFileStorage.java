package com.company.framework.file.storage;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 로컬 디스크 / NAS(마운트 경로) 공용 파일 저장소.
 * 저장 파일명은 UUID 로 생성(원본명은 메타에만 보존) → 경로조작/충돌 방지.
 * 날짜 디렉터리(yyyy/MM/dd)로 분산 저장.
 *
 * <p>{@link RangeReadableStorage} 를 구현해 HTTP Range(부분) 다운로드를 지원한다.
 */
public class FileSystemFileStorage implements FileStorage, RangeReadableStorage {

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
        if (!target.startsWith(basePath)) { // 경로조작 방어
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
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public long contentLength(String storedPath) {
        Path target = resolveExisting(storedPath);
        try {
            return Files.size(target);
        } catch (IOException e) {
            return -1;
        }
    }

    @Override
    public InputStream loadRange(String storedPath, long start, long endInclusive) {
        Path target = resolveExisting(storedPath);
        try {
            SeekableByteChannel channel = Files.newByteChannel(target, StandardOpenOption.READ);
            channel.position(start);
            long length = endInclusive - start + 1;
            return new BoundedInputStream(Channels.newInputStream(channel), length);
        } catch (IOException e) {
            throw new IllegalStateException("파일 부분 읽기 실패", e);
        }
    }

    /** 경로조작 방어 + 존재 확인을 묶은 공용 해석. */
    private Path resolveExisting(String storedPath) {
        Path target = basePath.resolve(storedPath).normalize();
        if (!target.startsWith(basePath) || !Files.exists(target)) {
            throw new BusinessException(ErrorCode.Common.NOT_FOUND, "파일을 찾을 수 없습니다.");
        }
        return target;
    }

    private String extOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return (dot < 0) ? "" : name.substring(dot + 1).toLowerCase();
    }

    /** 위임 스트림에서 정해진 바이트 수만 노출하는 래퍼(범위 끝 제한). */
    static final class BoundedInputStream extends InputStream {
        private final InputStream in;
        private long remaining;

        BoundedInputStream(InputStream in, long limit) {
            this.in = in;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) return -1;
            int b = in.read();
            if (b >= 0) remaining--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) return -1;
            int toRead = (int) Math.min(len, remaining);
            int read = in.read(b, off, toRead);
            if (read > 0) remaining -= read;
            return read;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
