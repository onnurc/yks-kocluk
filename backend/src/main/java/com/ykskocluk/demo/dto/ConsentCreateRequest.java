package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.ConsentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for recording a user's consent.
 */
public record ConsentCreateRequest(
        @NotNull(message = "Consent type cannot be null")
        ConsentType consentType,

        @NotBlank(message = "Document version cannot be blank")
        @Size(max = 50, message = "Document version cannot exceed 50 characters")
        String documentVersion
) {
}
