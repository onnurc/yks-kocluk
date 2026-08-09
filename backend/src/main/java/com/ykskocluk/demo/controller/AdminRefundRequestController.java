package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.enums.*;
import com.ykskocluk.demo.service.RefundRequestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin/refund-requests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundRequestController {
    private final RefundRequestService service;
    public AdminRefundRequestController(RefundRequestService service) { this.service = service; }

    @GetMapping
    public PageResponse<RefundRequestResponse> list(
            @RequestParam(required = false) RefundRequestStatus status,
            @RequestParam(required = false) RefundWindow eligibility,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long coachId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.adminList(status, eligibility, studentId, coachId, from, to, pageable);
    }

    @GetMapping("/{id}")
    public RefundRequestResponse detail(@PathVariable Long id) { return service.adminDetail(id); }

    @PostMapping("/{id}/approve")
    public RefundRequestResponse approve(@AuthenticationPrincipal Long adminId, @PathVariable Long id) {
        return service.approve(adminId, id);
    }

    @PostMapping("/{id}/reject")
    public RefundRequestResponse reject(@AuthenticationPrincipal Long adminId, @PathVariable Long id,
                                        @Valid @RequestBody RefundRequestRejectRequest request) {
        return service.reject(adminId, id, request);
    }
}
