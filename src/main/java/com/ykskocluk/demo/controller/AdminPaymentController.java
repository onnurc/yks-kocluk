package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.AdminPaymentResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.dto.RefundRequest;
import com.ykskocluk.demo.dto.RefundResponse;
import com.ykskocluk.demo.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for payment administrative actions, restricted to ADMIN users.
 */
@RestController
@RequestMapping("/api/v1/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    private final SubscriptionService subscriptionService;

    public AdminPaymentController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public PageResponse<AdminPaymentResponse> listPayments(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return subscriptionService.listPayments(pageable);
    }

    @PostMapping("/{paymentId}/refund")
    public RefundResponse refund(
            @PathVariable Long paymentId,
            @Valid @RequestBody RefundRequest request) {
        return subscriptionService.refund(paymentId, request.amount(), request.reason());
    }
}
