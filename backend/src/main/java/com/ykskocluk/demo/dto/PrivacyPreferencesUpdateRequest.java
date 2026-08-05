package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotNull;

public record PrivacyPreferencesUpdateRequest(
        Boolean necessaryAllowed,
        Boolean analyticsAllowed,
        Boolean marketingAllowed,
        @NotNull(message = "Çerez politikası dokümanı seçilmeli") Long cookiePolicyDocumentId
) { }
