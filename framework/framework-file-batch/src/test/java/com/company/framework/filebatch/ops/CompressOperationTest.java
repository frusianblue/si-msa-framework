package com.company.framework.filebatch.ops;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.archive.ZipArchiver;
import com.company.framework.filebatch.BatchItem;
import com.company.framework.filebatch.BatchOptions;
import com.company.framework.filebatch.BatchResult;
import com.company.framework.filebatch.FileBatchProcessor;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 압축 위임 작업 검증 — 실제 {@link ZipArchiver}(순수 JDK) 로 파일별 GZIP 라운드트립.
 * 경로 기반(스트리밍 기록)과 본문 기반(바이트 반환) 양쪽, 그리고 드라이런(IO 없음)을 본다.
 */
class CompressOperationTest {

    private final FileBatchProcessor processor = new FileBatchProcessor();
    private final ZipArchiver archiver = new ZipArchiver(1000, 10L * 1024 * 1024, 100L * 1024 * 1024);

    private static byte[] gunzip(byte[] gz) throws Exception {
        try (var in = new GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
            return in.readAllBytes();
        }
    }

    @Test
    @DisplayName("경로 기반: 같은 디렉토리에 <name>.gz 를 스트리밍 기록하고 원본으로 라운드트립된다")
    void compressesPathToSiblingGz(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("data.txt");
        byte[] payload = "the quick brown fox ".repeat(200).getBytes();
        Files.write(file, payload);

        CompressOperation op = new BatchArchiveOps(archiver).gzip();
        BatchResult r = processor.run(List.of(BatchItem.of(file)), op);

        assertThat(r.succeeded()).isEqualTo(1);
        assertThat(r.hasFailures()).isFalse();
        Path gz = dir.resolve("data.txt.gz");
        assertThat(Files.exists(gz)).isTrue();
        assertThat(r.outcomes().get(0).result().name()).isEqualTo("data.txt.gz");
        assertThat(gunzip(Files.readAllBytes(gz))).isEqualTo(payload);
    }

    @Test
    @DisplayName("본문 기반(경로 없음): 결과 바이트를 본문으로 반환하고 라운드트립된다")
    void compressesContentToBytes() throws Exception {
        byte[] payload = "in-memory payload ".repeat(50).getBytes();
        CompressOperation op = new BatchArchiveOps(archiver).gzip();

        BatchResult r = processor.run(List.of(BatchItem.of("mem.txt", payload)), op);

        BatchItem out = r.outcomes().get(0).result();
        assertThat(out.name()).isEqualTo("mem.txt.gz");
        assertThat(out.sourcePath()).isNull();
        try (InputStream in = out.openContent()) {
            assertThat(gunzip(in.readAllBytes())).isEqualTo(payload);
        }
    }

    @Test
    @DisplayName("드라이런: 실제 .gz 를 만들지 않고 계획만 산출한다")
    void dryRunWritesNothing(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("keep.txt");
        Files.writeString(file, "x");
        CompressOperation op = new BatchArchiveOps(archiver).gzip();

        BatchResult r = processor.run(
                List.of(BatchItem.of(file)), op, BatchOptions.defaults().withDryRun(true));

        assertThat(r.outcomes().get(0).message()).contains("keep.txt.gz");
        assertThat(Files.exists(dir.resolve("keep.txt.gz"))).isFalse();
    }

    @Test
    @DisplayName("여러 파일을 한 배치로 각각 gzip — 부분 결과가 입력 순서로 모인다")
    void compressesManyInOrder(@TempDir Path dir) throws Exception {
        var items = new java.util.ArrayList<BatchItem>();
        for (int i = 0; i < 6; i++) {
            Path f = dir.resolve("f" + i + ".txt");
            Files.write(f, ("content-" + i).repeat(20).getBytes());
            items.add(BatchItem.of(f));
        }
        CompressOperation op = new BatchArchiveOps(archiver).gzip();

        BatchResult r = processor.run(items, op);

        assertThat(r.succeeded()).isEqualTo(6);
        assertThat(r.outcomes().stream().map(o -> o.result().name()).toList())
                .containsExactly("f0.txt.gz", "f1.txt.gz", "f2.txt.gz", "f3.txt.gz", "f4.txt.gz", "f5.txt.gz");
        for (int i = 0; i < 6; i++) {
            byte[] expected = ("content-" + i).repeat(20).getBytes();
            assertThat(gunzip(Files.readAllBytes(dir.resolve("f" + i + ".txt.gz"))))
                    .isEqualTo(expected);
        }
    }
}
