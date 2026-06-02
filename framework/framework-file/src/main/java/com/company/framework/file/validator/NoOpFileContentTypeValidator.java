package com.company.framework.file.validator;

import org.springframework.web.multipart.MultipartFile;

/**
 * 기본 구현: 콘텐츠 검사를 하지 않고 클라이언트가 보낸 contentType 을 그대로 사용한다(기존 동작 유지).
 * 콘텐츠 기반 검증을 켜려면 {@code framework.file.validation.content-type-detection=true} + tika-core 의존을 추가한다.
 */
public class NoOpFileContentTypeValidator implements FileContentTypeValidator {

    @Override
    public String resolveAndValidate(MultipartFile file) {
        return file.getContentType();
    }
}
