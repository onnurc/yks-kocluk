package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.AdminSubscriptionTerminateRequest;
import com.ykskocluk.demo.dto.AdminSubscriptionTerminateResponse;
import com.ykskocluk.demo.service.SubscriptionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/{id}/terminate")
    public AdminSubscriptionTerminateResponse terminate(
            @PathVariable Long id,
            @RequestBody(required = false) AdminSubscriptionTerminateRequest request) {
        String reason = (request != null) ? request.reason() : null;
        return subscriptionService.terminateSubscription(id, reason);
    }
}
