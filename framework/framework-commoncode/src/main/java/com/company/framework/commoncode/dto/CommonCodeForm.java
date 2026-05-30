package com.company.framework.commoncode.dto;

import jakarta.validation.constraints.NotBlank;

public record CommonCodeForm(
        @NotBlank String groupCode,
        @NotBlank String code,
        @NotBlank String codeName,
        String codeValue,
        Integer sortOrder,
        Boolean useYn,
        String attr1,
        String attr2
) {}
