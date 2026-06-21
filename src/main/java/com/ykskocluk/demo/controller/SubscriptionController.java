package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.SubscriptionCreateRequest;
import com.ykskocluk.demo.dto.SubscriptionResponse;
import com.ykskocluk.demo.service.SubscriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subscriptions")
@PreAuthorize("hasRole('STUDENT')")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
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
}
