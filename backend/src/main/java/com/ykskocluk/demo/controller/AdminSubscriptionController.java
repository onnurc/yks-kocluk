package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.AdminSubscriptionResponse;
import com.ykskocluk.demo.dto.AdminSubscriptionTerminateRequest;
import com.ykskocluk.demo.dto.AdminSubscriptionTerminateResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.service.SubscriptionService;
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
import com.ykskocluk.demo.enums.SubscriptionStatus;
import jakarta.validation.Valid;
import java.time.Instant;

/**
 * Admin controls for subscriptions (Phase 9). ADMIN role restricted.
 */
@RestController
@RequestMapping("/api/v1/admin/subscriptions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminSubscriptionController {

    private final SubscriptionService subscriptionService;

    public AdminSubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping
    public PageResponse<AdminSubscriptionResponse> listSubscriptions(
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long coachId,
            @RequestParam(required = false) Long packageId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (status == null && studentId == null && coachId == null && packageId == null && from == null && to == null) {
            return subscriptionService.listSubscriptions(pageable);
        }
        return subscriptionService.listSubscriptions(status, studentId, coachId, packageId, from, to, pageable);
    }

    @PostMapping("/{id}/terminate")
    public AdminSubscriptionTerminateResponse terminate(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AdminSubscriptionTerminateRequest request) {
        String reason = (request != null) ? request.reason() : null;
        return subscriptionService.terminateSubscription(id, reason);
    }
}
