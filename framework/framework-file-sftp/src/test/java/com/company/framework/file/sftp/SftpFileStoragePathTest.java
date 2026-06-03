package com.company.framework.file.sftp;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.framework.file.sftp.SftpFileStorage.BoundedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** SFTP 경로 결합/조상 디렉토리/Range(skip+bounded) 등 순수 로직 단위 테스트(MINA 서버 불필요). */
class SftpFileStoragePathTest {

    @Nested
    @DisplayName("경로 결합/분해")
    class Paths {
        @Test
        @DisplayName("join 은 슬래시 1개로 결합하고 중복 슬래시를 제거한다")
        void join() {
            assertThat(SftpFileStorage.join("/home/app/upload", "2026/06/x.txt"))
                    .isEqualTo("/home/app/upload/2026/06/x.txt");
            assertThat(SftpFileStorage.join("/up/", "/k")).isEqualTo("/up/k");
            assertThat(SftpFileStorage.join("/up///", "///k")).isEqualTo("/up/k");
        }

        @Test
        @DisplayName("baseDir 가 비면 키만(서버 홈 상대)")
        void joinEmptyBase() {
            assertThat(SftpFileStorage.join("", "2026/x.txt")).isEqualTo("2026/x.txt");
            assertThat(SftpFileStorage.join(null, "k")).isEqualTo("k");
        }

        @Test
        @DisplayName("parentOf — 절대/루트직속/상대/무슬래시")
        void parentOf() {
            assertThat(SftpFileStorage.parentOf("/up/2026/x.txt")).isEqualTo("/up/2026");
            assertThat(SftpFileStorage.parentOf("/x.txt")).isEqualTo("/");
            assertThat(SftpFileStorage.parentOf("2026/x.txt")).isEqualTo("2026");
            assertThat(SftpFileStorage.parentOf("x.txt")).isEmpty();
        }

        @Test
        @DisplayName("ancestorDirs — mkdir -p 대용(얕은→깊은, 파일 제외)")
        void ancestorDirs() {
            assertThat(SftpFileStorage.ancestorDirs("/up/2026/06/03/x.txt"))
                    .containsExactly("/up", "/up/2026", "/up/2026/06", "/up/2026/06/03");
            assertThat(SftpFileStorage.ancestorDirs("2026/06/x.txt")).containsExactly("2026", "2026/06");
            assertThat(SftpFileStorage.ancestorDirs("/x.txt")).isEmpty();
            assertThat(SftpFileStorage.ancestorDirs("x.txt")).isEmpty();
        }

        @Test
        @DisplayName("extOf — 소문자 확장자, 없음/도트파일/null")
        void extOf() {
            assertThat(SftpFileStorage.extOf("Photo.JPG")).isEqualTo("jpg");
            assertThat(SftpFileStorage.extOf("README")).isEmpty();
            assertThat(SftpFileStorage.extOf(null)).isEmpty();
        }

        @Test
        @DisplayName("newKey — yyyy/MM/dd/{uuid}.{ext} 형태")
        void newKey() {
            String k = SftpFileStorage.newKey("a.png");
            assertThat(k).matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}\\.png");
            assertThat(SftpFileStorage.newKey("noext")).matches("\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}");
        }
    }

    @Nested
    @DisplayName("Range 스트림(skip + bounded)")
    class Range {
        private byte[] seq(int n) {
            byte[] d = new byte[n];
            for (int i = 0; i < n; i++) {
                d[i] = (byte) i;
            }
            return d;
        }

        @Test
        @DisplayName("[10..19] 구간은 정확히 10바이트")
        void midRange() throws IOException {
            InputStream raw = new ByteArrayInputStream(seq(100));
            SftpFileStorage.skipFully(raw, 10);
            byte[] got = new BoundedInputStream(raw, 19 - 10 + 1).readAllBytes();
            assertThat(got).hasSize(10);
            for (int i = 0; i < 10; i++) {
                assertThat(got[i]).isEqualTo((byte) (10 + i));
            }
        }

        @Test
        @DisplayName("끝을 넘는 요청은 EOF 에서 잘린다")
        void clampAtEof() throws IOException {
            InputStream raw = new ByteArrayInputStream(seq(100));
            SftpFileStorage.skipFully(raw, 95);
            byte[] got = new BoundedInputStream(raw, 120 - 95 + 1).readAllBytes();
            assertThat(got).hasSize(5);
        }

        @Test
        @DisplayName("skip()이 0을 반환해도 read 폴백으로 정확히 건너뛴다")
        void skipFullyHandlesNoSkip() throws IOException {
            InputStream noSkip = new FilterInputStream(new ByteArrayInputStream(seq(100))) {
                @Override
                public long skip(long n) {
                    return 0; // 절대 skip 안 함
                }
            };
            SftpFileStorage.skipFully(noSkip, 50);
            assertThat(noSkip.read()).isEqualTo(50);
        }

        @Test
        @DisplayName("BoundedInputStream 은 limit 이후 -1")
        void boundedStops() throws IOException {
            BoundedInputStream b = new BoundedInputStream(new ByteArrayInputStream(new byte[] {7, 8, 9}), 2);
            assertThat(b.read()).isEqualTo(7);
            assertThat(b.read()).isEqualTo(8);
            assertThat(b.read()).isEqualTo(-1);
        }
    }
}
