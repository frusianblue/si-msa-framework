package com.company.framework.archive;

import com.company.framework.core.error.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 아카이브에 담을 단일 엔트리. 본문은 즉시 바이트가 아니라 **여는 시점에 스트림을 제공**하는
 * {@link ContentSupplier} 로 들고 있어, 대용량도 메모리에 적재하지 않고 흘려보낼 수 있다.
 *
 * @param name    엔트리 경로명(아카이브 내 상대경로). 생성 시 {@link ArchiveSafety#sanitizeEntryName(String)} 로 검증된다.
 * @param content 본문 스트림 공급자(열 때마다 새 스트림을 반환해야 함).
 */
public record ArchiveEntry(String name, ContentSupplier content) {

    /** 본문 스트림 공급자. 호출될 때마다 처음부터 읽을 수 있는 새 스트림을 연다. */
    @FunctionalInterface
    public interface ContentSupplier {
        InputStream open() throws IOException;
    }

    public ArchiveEntry {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ArchiveErrorCode.EMPTY_INPUT, "엔트리 이름이 비어 있습니다.");
        }
        if (content == null) {
            throw new BusinessException(ArchiveErrorCode.EMPTY_INPUT, "엔트리 본문 공급자가 null 입니다.");
        }
    }

    /** 바이트 배열을 본문으로 하는 엔트리. */
    public static ArchiveEntry of(String name, byte[] bytes) {
        byte[] copy = bytes == null ? new byte[0] : bytes.clone();
        return new ArchiveEntry(name, () -> new ByteArrayInputStream(copy));
    }

    /** 파일 경로를 본문으로 하는 엔트리(스트리밍). */
    public static ArchiveEntry of(String name, Path path) {
        return new ArchiveEntry(name, () -> Files.newInputStream(path));
    }
}
