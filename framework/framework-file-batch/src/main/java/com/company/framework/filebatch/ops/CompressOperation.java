package com.company.framework.filebatch.ops;

import com.company.framework.archive.Archiver;
import com.company.framework.filebatch.BatchFileOperation;
import com.company.framework.filebatch.BatchItem;
import com.company.framework.filebatch.BatchSafety;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 파일별 GZIP 압축 작업 — 실제 압축은 {@code framework-archive} 의 {@link Archiver} 에 위임(스트리밍). framework-archive
 * 가 클래스패스에 없으면 이 작업 빈은 등록되지 않는다({@code @ConditionalOnClass} 백오프).
 *
 * <p>각 아이템을 {@code <name>.gz} 로 압축한다(파일별 단일 스트림). 경로가 있으면 같은 디렉토리에 스트리밍 기록(메모리 비적재),
 * 없으면 결과 바이트를 본문으로 반환한다. "여러 파일 → 1개의 zip" 같은 <b>집계</b> 압축은 일괄처리가 아니라
 * {@code Archiver.zip(entries, out)} 단일 호출로 충분하다.
 */
public final class CompressOperation implements BatchFileOperation {

    private final Archiver archiver;

    public CompressOperation(Archiver archiver) {
        this.archiver = Objects.requireNonNull(archiver, "archiver");
    }

    @Override
    public String plan(BatchItem item) {
        return item.name() + " -> " + item.name() + ".gz";
    }

    @Override
    public BatchItem apply(BatchItem item) throws IOException {
        String target = BatchSafety.requireSimpleName(item.name() + ".gz");
        if (item.sourcePath() != null) {
            Path dst = item.sourcePath().resolveSibling(target);
            try (InputStream in = Files.newInputStream(item.sourcePath());
                    OutputStream out = Files.newOutputStream(dst)) {
                archiver.gzip(in, out); // archiver 는 스트림을 닫지 않음 → try-with-resources 가 정리
            }
            return item.withSourcePath(dst).withName(target);
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream in = item.openContent()) {
            archiver.gzip(in, buffer);
        }
        byte[] gz = buffer.toByteArray();
        return item.withName(target).withContent(() -> new ByteArrayInputStream(gz));
    }
}
