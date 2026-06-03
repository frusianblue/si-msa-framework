package com.company.framework.file.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemRangeTest {

    private byte[] sample(int n) {
        byte[] data = new byte[n];
        for (int i = 0; i < n; i++) data[i] = (byte) (i % 256);
        return data;
    }

    @Test
    @DisplayName("contentLength 와 부분 읽기 오프셋/길이 정확")
    void rangeReads(@TempDir Path base) throws Exception {
        FileSystemFileStorage storage = new FileSystemFileStorage(base.toString(), "local");
        byte[] data = sample(1000);
        StoredFile sf = storage.store(new ByteArrayInputStream(data), "x.bin", "application/octet-stream", data.length);

        assertThat(storage.contentLength(sf.storedPath())).isEqualTo(1000);

        try (InputStream in = storage.loadRange(sf.storedPath(), 100, 199)) {
            byte[] got = in.readAllBytes();
            assertThat(got).hasSize(100);
            for (int i = 0; i < 100; i++) {
                assertThat(got[i]).isEqualTo((byte) ((100 + i) % 256));
            }
        }
    }

    @Test
    @DisplayName("꼬리 구간 읽기")
    void tailRange(@TempDir Path base) throws Exception {
        FileSystemFileStorage storage = new FileSystemFileStorage(base.toString(), "local");
        byte[] data = sample(500);
        StoredFile sf = storage.store(new ByteArrayInputStream(data), "y.bin", "application/octet-stream", data.length);
        try (InputStream in = storage.loadRange(sf.storedPath(), 490, 499)) {
            assertThat(in.readAllBytes()).hasSize(10);
        }
    }

    @Test
    @DisplayName("FileSystemFileStorage 는 RangeReadableStorage capability 를 노출")
    void exposesCapability(@TempDir Path base) {
        FileStorage storage = new FileSystemFileStorage(base.toString(), "local");
        assertThat(storage).isInstanceOf(RangeReadableStorage.class);
    }
}
