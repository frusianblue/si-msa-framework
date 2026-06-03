package com.company.framework.file.storage;

import java.io.InputStream;

/**
 * 부분(Range) 읽기를 지원하는 저장소의 <b>선택(capability) 인터페이스</b>.
 *
 * <p>{@link FileStorage} 본체는 건드리지 않고, 부분 읽기가 가능한 백엔드만 추가로 구현한다
 * ({@code FileSystemFileStorage}, {@code S3FileStorage}). 컨트롤러는 {@code storage instanceof RangeReadableStorage}
 * 로 판단해 206 Partial Content 를 처리하고, 미구현 백엔드는 전체(200)로 폴백한다.
 *
 * <p><b>주의</b>: {@code EncryptingFileStorage}(AES-CBC at-rest)는 임의 오프셋 복호화가 불가능하므로 이 인터페이스를
 * 구현하지 않는다 → 암호화를 켜면 Range 가 자동으로 비활성(전체 다운로드)된다.
 */
public interface RangeReadableStorage {

    /**
     * 지정 구간의 바이트만 읽는 스트림을 반환한다.
     *
     * @param storedPath 저장 경로/키
     * @param start 시작 오프셋(포함)
     * @param endInclusive 끝 오프셋(포함)
     * @return 해당 구간 바이트 스트림(호출 측이 닫는다)
     */
    InputStream loadRange(String storedPath, long start, long endInclusive);

    /** 부분 읽기 길이 계산 등에 필요한 전체 바이트 길이. 알 수 없으면 음수. */
    long contentLength(String storedPath);
}
