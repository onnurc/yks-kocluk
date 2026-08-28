package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.SubscriptionResponse;
import com.ykskocluk.demo.service.SubscriptionService;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Profile({"local", "stub", "test"})
@ConditionalOnProperty(name = "payments.iyzico.enabled", havingValue = "false", matchIfMissing = true)
@PreAuthorize("hasRole('STUDENT')")
public class StubPaymentController {

    private final SubscriptionService subscriptionService;

    public StubPaymentController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/{paymentId}/stub/succeed")
    public SubscriptionResponse succeedPayment(@AuthenticationPrincipal Long studentUserId,
                                                @PathVariable Long paymentId) {
        return subscriptionService.succeedPayment(paymentId, studentUserId);
    }
}
