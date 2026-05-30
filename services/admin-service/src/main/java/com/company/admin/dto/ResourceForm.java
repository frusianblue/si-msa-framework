package com.company.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record ResourceForm(
        @NotBlank String urlPattern,
        String httpMethod,
        String descr,
        Integer sortOrder
) {}
