package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.MediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MediaPresignRequest(
        @NotNull MediaType mediaType,
        @NotBlank String contentType,
        @Positive long sizeBytes,
        String originalFilename
) {}
