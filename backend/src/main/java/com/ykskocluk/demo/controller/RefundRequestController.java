package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.service.RefundRequestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/refund-requests")
@PreAuthorize("hasRole('STUDENT')")
public class RefundRequestController {
    private final RefundRequestService service;
    public RefundRequestController(RefundRequestService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RefundRequestResponse create(@AuthenticationPrincipal Long studentId,
                                        @Valid @RequestBody RefundRequestCreateRequest request) {
        return service.create(studentId, request);
    }

    @GetMapping("/me")
    public PageResponse<RefundRequestResponse> mine(@AuthenticationPrincipal Long studentId,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.mine(studentId, pageable);
    }
}
