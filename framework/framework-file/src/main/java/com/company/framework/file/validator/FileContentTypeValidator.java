package com.company.framework.file.validator;

import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드 파일의 콘텐츠 타입 검증 전략.
 *
 * <p>구현은 업로드 본문을 검사해 위험/위장 파일을 차단하고, 신뢰 가능한 contentType 을 반환한다. 반환값은
 * 클라이언트가 보낸 {@code Content-Type} 헤더 대신 메타에 기록되어, 헤더 위조에 의존하지 않게 한다.
 */
public interface FileContentTypeValidator {

    /**
     * @param file 업로드 파일
     * @return 메타에 기록할 신뢰 가능한 contentType
     * @throws com.company.framework.core.error.BusinessException 위험/위장 파일인 경우
     */
    String resolveAndValidate(MultipartFile file);
}
