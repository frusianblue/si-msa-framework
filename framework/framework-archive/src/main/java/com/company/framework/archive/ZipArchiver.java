package com.company.framework.archive;

import com.company.framework.core.error.BusinessException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 기본 아카이버 — 순수 JDK {@code java.util.zip}(ZIP/GZIP), 스트리밍 + 안전 가드.
 *
 * <p><b>스트리밍</b>: zip 생성은 엔트리 본문을 {@code transferTo} 로 흘려보내고, 해제는
 * {@link ZipInputStream} 을 엔트리 단위로 콜백에 넘겨 호출자가 디스크/스토리지로 바로 흘릴 수 있다(전체 버퍼링 없음).
 *
 * <p><b>안전</b>: ① zip-slip — 엔트리 이름은 {@link ArchiveSafety} 로 검증. ② 압축폭탄 —
 * 엔트리 수({@code maxEntries})·단일 엔트리 해제 크기({@code maxEntrySize})·총 해제 바이트({@code maxTotalBytes})
 * 상한을 두고, 해제 스트림을 읽는 도중 초과하면 즉시 예외.
 */
public class ZipArchiver implements Archiver {

    private final int maxEntries;
    private final long maxEntrySize;
    private final long maxTotalBytes;

    public ZipArchiver(int maxEntries, long maxEntrySize, long maxTotalBytes) {
        this.maxEntries = maxEntries;
        this.maxEntrySize = maxEntrySize;
        this.maxTotalBytes = maxTotalBytes;
    }

    @Override
    public void zip(List<ArchiveEntry> entries, OutputStream out) {
        if (entries == null || entries.isEmpty()) {
            throw new BusinessException(ArchiveErrorCode.EMPTY_INPUT, "압축할 엔트리가 없습니다.");
        }
        // ZipOutputStream 은 호출자 out 을 닫게 되어 있으나, 여기선 우리가 만든 래퍼만 닫고 out 은 호출자 소유로 둔다.
        ZipOutputStream zos = new ZipOutputStream(new NonClosingOutputStream(out));
        try (zos) {
            for (ArchiveEntry entry : entries) {
                String safe = ArchiveSafety.sanitizeEntryName(entry.name());
                zos.putNextEntry(new ZipEntry(safe));
                try (InputStream in = entry.content().open()) {
                    in.transferTo(zos);
                }
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new BusinessException(ArchiveErrorCode.WRITE_FAILED, "ZIP 생성 실패: " + e.getMessage());
        }
    }

    @Override
    public void unzip(InputStream zipIn, EntryConsumer consumer) {
        long[] total = {0L};
        int count = 0;
        // try-with-resources 로 내부 Inflater 를 정리하되, 래퍼로 감싸 호출자 zipIn 은 닫지 않는다.
        try (ZipInputStream zis = new ZipInputStream(new NonClosingInputStream(zipIn))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                if (++count > maxEntries) {
                    throw new BusinessException(ArchiveErrorCode.TOO_MANY_ENTRIES, "엔트리 수 상한(" + maxEntries + ") 초과");
                }
                String safe = ArchiveSafety.sanitizeEntryName(e.getName());
                InputStream guarded = new BombGuardInputStream(zis, maxEntrySize, total, maxTotalBytes);
                consumer.accept(safe, guarded);
                zis.closeEntry();
            }
        } catch (BusinessException be) {
            throw be;
        } catch (IOException e) {
            throw new BusinessException(ArchiveErrorCode.READ_FAILED, "ZIP 해제 실패: " + e.getMessage());
        }
    }

    @Override
    public int unzipToDirectory(InputStream zipIn, Path targetDir) {
        int[] written = {0};
        unzip(zipIn, (name, content) -> {
            Path target = ArchiveSafety.resolveSafely(targetDir, name);
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream os = Files.newOutputStream(target)) {
                content.transferTo(os);
            }
            written[0]++;
        });
        return written[0];
    }

    @Override
    public void gzip(InputStream in, OutputStream out) {
        if (in == null) {
            throw new BusinessException(ArchiveErrorCode.EMPTY_INPUT, "압축할 스트림이 null 입니다.");
        }
        try (GZIPOutputStream gz = new GZIPOutputStream(new NonClosingOutputStream(out))) {
            in.transferTo(gz);
        } catch (IOException e) {
            throw new BusinessException(ArchiveErrorCode.WRITE_FAILED, "GZIP 압축 실패: " + e.getMessage());
        }
    }

    @Override
    public void gunzip(InputStream in, OutputStream out) {
        if (in == null) {
            throw new BusinessException(ArchiveErrorCode.EMPTY_INPUT, "해제할 스트림이 null 입니다.");
        }
        long[] total = {0L};
        try (GZIPInputStream gz = new GZIPInputStream(new NonClosingInputStream(in))) {
            // 총 해제 바이트 폭탄 가드: maxTotalBytes 를 단일 스트림 상한으로 재사용.
            new BombGuardInputStream(gz, maxTotalBytes, total, maxTotalBytes).transferTo(out);
        } catch (BusinessException be) {
            throw be;
        } catch (IOException e) {
            throw new BusinessException(ArchiveErrorCode.READ_FAILED, "GZIP 해제 실패: " + e.getMessage());
        }
    }

    /**
     * 해제 바이트를 세며 단일 엔트리 상한과 공유 총량 상한을 강제하는 가드 스트림. 초과 시 {@link BusinessException}.
     * 하위 스트림(ZipInputStream/GZIPInputStream)을 <b>닫지 않는다</b>(엔트리 순회를 이어가야 하므로).
     */
    static final class BombGuardInputStream extends FilterInputStream {
        private final long maxEntrySize;
        private final long[] total; // 공유 누적 카운터(아카이브 전체)
        private final long maxTotalBytes;
        private long entryBytes;

        BombGuardInputStream(InputStream delegate, long maxEntrySize, long[] total, long maxTotalBytes) {
            super(delegate);
            this.maxEntrySize = maxEntrySize;
            this.total = total;
            this.maxTotalBytes = maxTotalBytes;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(long n) {
            entryBytes += n;
            if (entryBytes > maxEntrySize) {
                throw new BusinessException(ArchiveErrorCode.ENTRY_TOO_LARGE, "엔트리 해제 크기 상한(" + maxEntrySize + "B) 초과");
            }
            total[0] += n;
            if (total[0] > maxTotalBytes) {
                throw new BusinessException(
                        ArchiveErrorCode.ARCHIVE_TOO_LARGE, "총 해제 크기 상한(" + maxTotalBytes + "B) 초과");
            }
        }

        @Override
        public void close() {
            // no-op: 하위 스트림은 순회 주체가 관리한다.
        }
    }

    /** 위임 OutputStream 을 닫지 않는 래퍼(ZIP/GZIP 종료는 하되 호출자 스트림 소유권은 보존). */
    private static final class NonClosingOutputStream extends java.io.FilterOutputStream {
        NonClosingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len); // FilterOutputStream 의 1바이트씩 위임 회피(성능)
        }

        @Override
        public void close() throws IOException {
            flush(); // 닫지 않고 flush 만
        }
    }

    /** 위임 InputStream 을 닫지 않는 래퍼(Zip/Gzip 의 Inflater 는 try-with-resources 로 정리하되 호출자 스트림 보존). */
    private static final class NonClosingInputStream extends FilterInputStream {
        NonClosingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() {
            // no-op: 호출자 소유 스트림은 닫지 않는다.
        }
    }
}
