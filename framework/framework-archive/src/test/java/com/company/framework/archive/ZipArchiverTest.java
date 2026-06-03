package com.company.framework.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.framework.core.error.BusinessException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ZIP/GZIP 아카이버 단위 테스트(순수 JDK, Spring 무관). */
class ZipArchiverTest {

    private final ZipArchiver archiver = new ZipArchiver(1000, 10L * 1024 * 1024, 100L * 1024 * 1024);

    private byte[] zipOf(ArchiveEntry... entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        archiver.zip(List.of(entries), out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("zip → unzip 라운드트립: 이름/본문이 보존된다")
    void roundTrip() {
        byte[] zip = zipOf(
                ArchiveEntry.of("a.txt", "hello".getBytes(StandardCharsets.UTF_8)),
                ArchiveEntry.of("dir/b.txt", "한글내용".getBytes(StandardCharsets.UTF_8)));

        Map<String, String> got = new HashMap<>();
        archiver.unzip(new ByteArrayInputStream(zip), (name, content) -> {
            got.put(name, new String(content.readAllBytes(), StandardCharsets.UTF_8));
        });

        assertThat(got).containsOnly(Map.entry("a.txt", "hello"), Map.entry("dir/b.txt", "한글내용"));
    }

    @Test
    @DisplayName("unzipToDirectory: baseDir 아래로 파일을 풀어 쓴다")
    void unzipToDir(@TempDir Path dir) throws Exception {
        byte[] zip = zipOf(ArchiveEntry.of("sub/x.txt", "data".getBytes(StandardCharsets.UTF_8)));
        int n = archiver.unzipToDirectory(new ByteArrayInputStream(zip), dir);
        assertThat(n).isEqualTo(1);
        assertThat(Files.readString(dir.resolve("sub/x.txt"))).isEqualTo("data");
    }

    @Test
    @DisplayName("zip-slip: 악성 엔트리(../)는 해제 시 거부된다")
    void zipSlipBlocked() {
        // ZipOutputStream 으로 ../ 가 든 악성 zip 을 직접 만든다(우리 archiver 를 우회).
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(raw)) {
            zos.putNextEntry(new ZipEntry("../evil.txt"));
            zos.write("pwn".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        assertThatThrownBy(() -> archiver.unzip(
                        new ByteArrayInputStream(raw.toByteArray()), (name, content) -> content.readAllBytes()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ArchiveErrorCode.UNSAFE_ENTRY_PATH);
    }

    @Test
    @DisplayName("엔트리 수 상한 초과 → TOO_MANY_ENTRIES")
    void tooManyEntries() {
        ZipArchiver tiny = new ZipArchiver(1, 1024, 1024);
        byte[] zip = zipOf(ArchiveEntry.of("a.txt", "1".getBytes()), ArchiveEntry.of("b.txt", "2".getBytes()));
        assertThatThrownBy(() -> tiny.unzip(new ByteArrayInputStream(zip), (n, c) -> c.readAllBytes()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ArchiveErrorCode.TOO_MANY_ENTRIES);
    }

    @Test
    @DisplayName("단일 엔트리 해제 크기 상한 초과 → ENTRY_TOO_LARGE")
    void entryTooLarge() {
        ZipArchiver tiny = new ZipArchiver(10, 4, 1_000_000);
        byte[] zip = zipOf(ArchiveEntry.of("big.txt", "0123456789".getBytes()));
        assertThatThrownBy(() -> tiny.unzip(new ByteArrayInputStream(zip), (n, c) -> c.readAllBytes()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ArchiveErrorCode.ENTRY_TOO_LARGE);
    }

    @Test
    @DisplayName("gzip → gunzip 라운드트립")
    void gzipRoundTrip() {
        byte[] plain = "압축 대상 데이터 streaming payload 12345".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        archiver.gzip(new ByteArrayInputStream(plain), gz);

        assertThat(gz.toByteArray()).isNotEqualTo(plain); // 실제 압축됨

        ByteArrayOutputStream back = new ByteArrayOutputStream();
        archiver.gunzip(new ByteArrayInputStream(gz.toByteArray()), back);
        assertThat(back.toByteArray()).isEqualTo(plain);
    }

    @Test
    @DisplayName("빈 엔트리 목록으로 zip → EMPTY_INPUT")
    void emptyZipRejected() {
        assertThatThrownBy(() -> archiver.zip(List.of(), new ByteArrayOutputStream()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ArchiveErrorCode.EMPTY_INPUT);
    }

    @Test
    @DisplayName("zip 출력 스트림은 닫지 않는다(호출자 소유 보존)")
    void doesNotCloseCallerStream() {
        boolean[] closed = {false};
        ByteArrayOutputStream tracking = new ByteArrayOutputStream() {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        archiver.zip(List.of(ArchiveEntry.of("a.txt", "x".getBytes())), tracking);
        assertThat(closed[0]).isFalse();
        assertThat(tracking.size()).isGreaterThan(0);
    }
}
