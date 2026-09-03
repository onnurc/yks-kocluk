package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.SubscriptionCreateRequest;
import com.ykskocluk.demo.dto.SubscriptionCheckoutResponse;
import com.ykskocluk.demo.dto.SubscriptionCheckoutRequest;
import com.ykskocluk.demo.dto.SubscriptionResponse;
import com.ykskocluk.demo.dto.CancellationCalculationResponse;
import com.ykskocluk.demo.integration.MailClient;
import com.ykskocluk.demo.service.CancelResult;
import com.ykskocluk.demo.service.SubscriptionBillingService;
import com.ykskocluk.demo.service.SubscriptionService;
import com.ykskocluk.demo.service.CancellationCalculationService;
import com.ykskocluk.demo.security.ratelimit.AuthenticatedActionRateLimitService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/subscriptions")
@PreAuthorize("hasRole('STUDENT')")
public class SubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    private final SubscriptionService subscriptionService;
    private final SubscriptionBillingService billingService;
    private final MailClient mailClient;
    private final AuthenticatedActionRateLimitService actionRateLimit;
    private final CancellationCalculationService cancellationCalculationService;

    public SubscriptionController(SubscriptionService subscriptionService,
                                  SubscriptionBillingService billingService,
                                  MailClient mailClient,
                                  AuthenticatedActionRateLimitService actionRateLimit,
                                  CancellationCalculationService cancellationCalculationService) {
        this.subscriptionService = subscriptionService;
        this.billingService = billingService;
        this.mailClient = mailClient;
        this.actionRateLimit = actionRateLimit;
        this.cancellationCalculationService = cancellationCalculationService;
    }

    @PostMapping
    public ResponseEntity<SubscriptionResponse> subscribe(@AuthenticationPrincipal Long studentUserId,
                                                          @Valid @RequestBody SubscriptionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriptionService.subscribe(studentUserId, request));
    }

    @GetMapping("/me")
    public List<SubscriptionResponse> mySubscriptions(@AuthenticationPrincipal Long studentUserId) {
        return subscriptionService.mySubscriptions(studentUserId);
    }

    @PostMapping("/checkout")
    public ResponseEntity<SubscriptionCheckoutResponse> checkout(@AuthenticationPrincipal Long studentUserId,
                                                                 @Valid @RequestBody SubscriptionCheckoutRequest request) {
        actionRateLimit.checkCheckoutCreate(studentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(subscriptionService.checkout(studentUserId, request));
    }

    /**
     * Turns auto-renew off; access continues until {@code endAt} (no refund, no immediate cutoff).
     * Idempotent — re-cancelling returns the current state and fires no second email. The
     * confirmation email is dispatched AFTER the cancel transaction has committed (best-effort).
     */
    @PostMapping("/{id}/cancel")
    public SubscriptionResponse cancel(@AuthenticationPrincipal Long studentUserId, @PathVariable Long id) {
        CancelResult result = billingService.cancel(id, studentUserId);
        if (result.newlyCancelled()) {
            try {
                mailClient.sendCancellationConfirmed(result.studentEmail(),
                        result.subscription().coachName(), result.subscription().endAt());
            } catch (Exception e) {
                // Best-effort: cancellation is already committed; never let a mail failure surface.
                log.error("Cancellation email failed for subscription {}: {}", id, e.getMessage(), e);
            }
        }
        return result.subscription();
    }

    @GetMapping("/{id}/cancellation-calculation")
    public CancellationCalculationResponse cancellationCalculation(@AuthenticationPrincipal Long studentUserId,
                                                                    @PathVariable Long id) {
        return cancellationCalculationService.calculateForStudent(id, studentUserId, Instant.now());
    }
}
