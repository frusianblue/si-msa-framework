package com.company.framework.filebatch;

import com.company.framework.core.error.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * 일괄처리 단위 아이템(불변). 본문은 즉시 바이트가 아니라 여는 시점에 스트림을 제공하는
 * {@link BatchContent} 로 들고 있어 대용량도 파일별로 흘려보낼 수 있다.
 *
 * @param sourcePath 원본 파일 경로(없을 수 있음 — 본문이 {@code content} 로만 제공되는 경우).
 * @param name       현재 논리 파일명(필수). 작업은 새 이름을 가진 새 아이템을 반환한다.
 * @param content    본문 스트림 공급자(없으면 {@code sourcePath} 에서 연다).
 * @param index      입력 순서 인덱스(오케스트레이터가 0-기반으로 부여; 미부여 시 -1). 연번/정렬의 기준.
 * @param meta       부가 메타데이터(불변, never null).
 */
public record BatchItem(Path sourcePath, String name, BatchContent content, int index, Map<String, String> meta) {

    public BatchItem {
        if (name == null || name.isBlank()) {
            throw new BusinessException(FileBatchErrorCode.INVALID_INPUT, "아이템 이름이 비어 있습니다.");
        }
        meta = (meta == null) ? Map.of() : Map.copyOf(meta);
    }

    /** 파일 경로 기반 아이템(본문은 경로에서 스트리밍). */
    public static BatchItem of(Path path) {
        Objects.requireNonNull(path, "path");
        Path fn = path.getFileName();
        return new BatchItem(path, fn == null ? path.toString() : fn.toString(), null, -1, Map.of());
    }

    /** 바이트 본문 기반 아이템(경로 없음). */
    public static BatchItem of(String name, byte[] bytes) {
        byte[] copy = bytes == null ? new byte[0] : bytes.clone();
        return new BatchItem(null, name, () -> new ByteArrayInputStream(copy), -1, Map.of());
    }

    public BatchItem withName(String newName) {
        return new BatchItem(sourcePath, newName, content, index, meta);
    }

    public BatchItem withSourcePath(Path newPath) {
        return new BatchItem(newPath, name, content, index, meta);
    }

    public BatchItem withContent(BatchContent newContent) {
        return new BatchItem(sourcePath, name, newContent, index, meta);
    }

    public BatchItem withIndex(int newIndex) {
        return new BatchItem(sourcePath, name, content, newIndex, meta);
    }

    /** 본문 스트림을 연다({@code content} 우선, 없으면 {@code sourcePath}). 호출자가 닫는다. */
    public InputStream openContent() throws IOException {
        if (content != null) {
            return content.open();
        }
        if (sourcePath != null) {
            return Files.newInputStream(sourcePath);
        }
        throw new BusinessException(
                FileBatchErrorCode.INVALID_INPUT, "본문 소스가 없습니다(content/sourcePath 모두 null): " + name);
    }
}
