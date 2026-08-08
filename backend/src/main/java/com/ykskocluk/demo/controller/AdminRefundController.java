package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.enums.*;
import com.ykskocluk.demo.service.SubscriptionService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin/refunds")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundController {
    private final SubscriptionService subscriptions;
    public AdminRefundController(SubscriptionService subscriptions) { this.subscriptions = subscriptions; }

    @GetMapping
    public PageResponse<AdminPaymentResponse> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long coachId,
            @RequestParam(required = false) Long packageId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return subscriptions.listPayments(PaymentType.REFUND, status, studentId, coachId, packageId, from, to, pageable);
    }
}
