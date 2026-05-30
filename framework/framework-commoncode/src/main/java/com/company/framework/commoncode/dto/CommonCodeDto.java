package com.company.framework.commoncode.dto;

/** 화면/외부 노출용 코드 DTO (감사필드/내부필드 제외). */
public record CommonCodeDto(
        String groupCode,
        String code,
        String codeName,
        String codeValue,
        int sortOrder,
        String attr1,
        String attr2
) {}
