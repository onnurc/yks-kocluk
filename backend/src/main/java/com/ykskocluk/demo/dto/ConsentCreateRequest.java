package com.ykskocluk.demo.dto;

import com.ykskocluk.demo.enums.ConsentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for recording a user's consent.
 */
public record ConsentCreateRequest(
        @NotNull(message = "Consent type cannot be null")
        ConsentType consentType,

        @NotBlank(message = "Document version cannot be blank")
        String documentVersion
) {
}
