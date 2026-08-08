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
import org.springframework.web.bind.annotation.RequestParam;
import com.ykskocluk.demo.enums.PaymentStatus;
import com.ykskocluk.demo.enums.PaymentType;
import java.time.Instant;

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
            @RequestParam(required = false) PaymentType type,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long coachId,
            @RequestParam(required = false) Long packageId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (type == null && status == null && studentId == null && coachId == null && packageId == null
                && from == null && to == null) {
            return subscriptionService.listPayments(pageable);
        }
        return subscriptionService.listPayments(type, status, studentId, coachId, packageId, from, to, pageable);
    }

    @PostMapping("/{paymentId}/refund")
    public RefundResponse refund(
            @PathVariable Long paymentId,
            @Valid @RequestBody RefundRequest request) {
        return subscriptionService.refund(paymentId, request.amount(), request.reason());
    }
}
