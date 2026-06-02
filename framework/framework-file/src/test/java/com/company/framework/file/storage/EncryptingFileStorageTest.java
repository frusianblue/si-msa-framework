package com.company.framework.file.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.core.crypto.AesCryptoService;
import com.company.framework.core.crypto.CryptoHolder;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 저장소 at-rest 암호화 데코레이터 단위 테스트. */
class EncryptingFileStorageTest {

    @BeforeEach
    void setUp() {
        CryptoHolder.set(new AesCryptoService("file-encrypt-test-secret"));
    }

    @Test
    @DisplayName("store 는 암호문을 위임에 저장하고(평문 미노출), load 는 복호화하여 원본을 돌려준다")
    void encryptsAtRestAndDecryptsOnLoad() throws Exception {
        InMemoryStorage backend = new InMemoryStorage();
        EncryptingFileStorage enc = new EncryptingFileStorage(backend);

        byte[] plain = "주민번호 등 민감정보가 담긴 파일".getBytes(StandardCharsets.UTF_8);
        StoredFile sf = enc.store(new ByteArrayInputStream(plain), "secret.txt", "text/plain", plain.length);

        byte[] onDisk = backend.data.get(sf.storedPath());
        assertThat(onDisk).isNotEqualTo(plain); // 저장소에는 평문이 남지 않음
        assertThat((long) onDisk.length).isEqualTo(AesCryptoService.cbcEncryptedLength(plain.length));

        try (InputStream in = enc.load(sf.storedPath())) {
            assertThat(in.readAllBytes()).isEqualTo(plain); // 복호화 복원
        }
        assertThat(enc.type()).isEqualTo("local");
    }

    /** 저장 바이트를 그대로 보관/반환하는 테스트용 백엔드. */
    private static final class InMemoryStorage implements FileStorage {
        private final Map<String, byte[]> data = new HashMap<>();
        private int seq = 0;

        @Override
        public StoredFile store(InputStream content, String originalName, String contentType, long size) {
            try {
                String path = "stored/" + (seq++) + "-" + originalName;
                data.put(path, content.readAllBytes());
                return new StoredFile(path, originalName, contentType, size);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public InputStream load(String storedPath) {
            return new ByteArrayInputStream(data.get(storedPath));
        }

        @Override
        public void delete(String storedPath) {
            data.remove(storedPath);
        }

        @Override
        public String type() {
            return "local";
        }
    }
}
