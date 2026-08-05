package com.ykskocluk.demo.dto;

import jakarta.validation.constraints.NotNull;

public record SubscriptionCheckoutRequest(
        @NotNull(message = "Koç seçilmeli") Long coachId,
        @NotNull(message = "Paket seçilmeli") Long packageId,
        Long preInformationDocumentId,
        Long distanceSalesDocumentId,
        Long refundCancellationPolicyDocumentId,
        boolean legalDocumentsAccepted
) {
    public SubscriptionCreateRequest subscriptionRequest() {
        return new SubscriptionCreateRequest(coachId, packageId);
    }
}
