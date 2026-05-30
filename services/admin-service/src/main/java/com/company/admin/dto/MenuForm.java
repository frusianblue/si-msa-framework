package com.company.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record MenuForm(
        Long parentId,
        @NotBlank String name,
        String url,
        String icon,
        Integer sortOrder
) {}
