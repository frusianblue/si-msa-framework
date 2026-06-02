package com.company.framework.file.validator;

import com.company.framework.core.error.BusinessException;
import com.company.framework.core.error.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

/**
 * Apache Tika 로 업로드 본문의 매직넘버(시그니처)를 검출해 검증한다.
 *  - 클라이언트가 보낸 {@code Content-Type}/확장자를 신뢰하지 않고 실제 바이트로 MIME 을 판정.
 *  - 검출된 MIME 이 차단 목록(실행파일/스크립트/HTML 등)에 있으면 차단 → png 로 위장한 exe/jsp 등 폴리글랏 방어.
 *  - 통과 시 검출된 신뢰 MIME 을 반환하여 메타에 기록한다.
 *
 * <p>tika-core 만으로 매직넘버 검출이 동작한다(파서 모듈 불필요). 헤더 일부(최대 64KB)만 읽어 대용량에도 가볍다.
 */
public class TikaFileContentTypeValidator implements FileContentTypeValidator {

    private static final int MAX_HEADER_BYTES = 64 * 1024;

    private final Tika tika = new Tika();
    private final Set<String> blockedContentTypes;

    public TikaFileContentTypeValidator(Set<String> blockedContentTypes) {
        this.blockedContentTypes = blockedContentTypes;
    }

    @Override
    public String resolveAndValidate(MultipartFile file) {
        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(MAX_HEADER_BYTES);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "업로드 파일을 읽을 수 없습니다.");
        }

        String detected = tika.detect(header).toLowerCase(Locale.ROOT);
        if (blockedContentTypes.contains(detected)) {
            throw new BusinessException(ErrorCode.Common.INVALID_INPUT, "허용되지 않는 콘텐츠 형식입니다(검출: " + detected + ").");
        }
        return detected;
    }
}
