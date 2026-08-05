package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotNull;

public record LegalOnboardingRequest(
        @NotNull(message = "Kullanım Koşulları dokümanı seçilmeli") Long termsDocumentId,
        @NotNull(message = "Açık Rıza dokümanı seçilmeli") Long explicitConsentDocumentId,
        Boolean marketingEmailOptIn,
        Boolean marketingSmsOptIn
) { }
