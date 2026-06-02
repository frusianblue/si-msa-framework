package com.company.framework.file.storage;

import com.company.framework.core.crypto.AesCryptoService;
import com.company.framework.core.crypto.CryptoHolder;
import java.io.InputStream;

/**
 * 임의의 {@link FileStorage}(local/nas/s3) 를 감싸 본문을 AES 로 at-rest 암호화하는 데코레이터.
 *  - store: 평문 입력 스트림을 암호화 스트림(IV||ciphertext)으로 위임에 전달. S3 등 content-length 요구 백엔드를
 *    위해 암호화 후 정확한 길이({@link AesCryptoService#cbcEncryptedLength(long)})를 넘긴다.
 *  - load: 위임이 돌려준 암호화 스트림을 복호화 스트림으로 감싸 반환(대용량 스트리밍 유지).
 *
 * <p>AES 서비스는 {@link CryptoHolder} 정적 접근으로 사용하므로(타입핸들러와 동일 패턴) {@code framework.crypto.enabled}
 * 가 켜져 있어야 한다(기본 ON). 저장명·경로 생성은 위임 구현이 그대로 담당한다.
 */
public class EncryptingFileStorage implements FileStorage {

    private final FileStorage delegate;

    public EncryptingFileStorage(FileStorage delegate) {
        this.delegate = delegate;
    }

    /** 테스트/조회용: 감싸고 있는 실제 저장소. */
    public FileStorage delegate() {
        return delegate;
    }

    @Override
    public StoredFile store(InputStream content, String originalName, String contentType, long size) {
        AesCryptoService aes = CryptoHolder.aes();
        return delegate.store(
                aes.encryptingInputStream(content),
                originalName,
                contentType,
                AesCryptoService.cbcEncryptedLength(size));
    }

    @Override
    public InputStream load(String storedPath) {
        return CryptoHolder.aes().decryptingInputStream(delegate.load(storedPath));
    }

    @Override
    public void delete(String storedPath) {
        delegate.delete(storedPath);
    }

    @Override
    public String type() {
        return delegate.type();
    }
}
