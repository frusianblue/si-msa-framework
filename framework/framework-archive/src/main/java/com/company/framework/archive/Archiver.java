package com.company.framework.archive;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * 아카이빙/압축 SPI. 기본 구현은 {@link ZipArchiver}(순수 JDK {@code java.util.zip}).
 *
 * <p>모든 메서드는 **스트리밍** — 입력/출력 스트림으로 흐르게 해 대용량도 메모리에 적재하지 않는다.
 * 스트림은 호출자가 닫는다(메서드는 전달받은 스트림을 닫지 않음). 안전 위반·한도 초과는
 * {@link ArchiveErrorCode} 로 {@code BusinessException} 을 던진다.
 */
public interface Archiver {

    /** 여러 엔트리를 ZIP 으로 묶어 {@code out} 으로 스트리밍한다. */
    void zip(List<ArchiveEntry> entries, OutputStream out);

    /**
     * ZIP 입력을 엔트리 단위로 해제하며, 각 엔트리를 {@link EntryConsumer} 로 넘긴다(메모리 안전).
     * 콜백에 전달되는 스트림은 **압축폭탄 가드**가 걸려 있어 한도 초과 시 읽는 도중 예외가 난다.
     * 엔트리 이름은 zip-slip 검증된 상대경로다.
     */
    void unzip(InputStream zipIn, EntryConsumer consumer);

    /**
     * ZIP 을 {@code targetDir} 아래로 안전하게(zip-slip 방지) 풀어 쓴다. 디렉토리 엔트리는 생성, 파일은 스트리밍 기록.
     *
     * @return 기록한 파일 엔트리 수
     */
    int unzipToDirectory(InputStream zipIn, Path targetDir);

    /** 단일 스트림을 GZIP 으로 압축해 {@code out} 으로 흘린다. */
    void gzip(InputStream in, OutputStream out);

    /** GZIP 스트림을 해제해 {@code out} 으로 흘린다(총 해제 바이트 폭탄 가드 적용). */
    void gunzip(InputStream in, OutputStream out);

    /** 해제된 엔트리 1건을 소비하는 콜백. {@code content} 는 가드가 걸린 스트림이며 닫지 않아도 된다. */
    @FunctionalInterface
    interface EntryConsumer {
        void accept(String name, InputStream content) throws IOException;
    }
}
