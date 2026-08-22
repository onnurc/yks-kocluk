package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.CoachApplicationResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.dto.RejectRequest;
import com.ykskocluk.demo.enums.CoachApplicationStatus;
import com.ykskocluk.demo.service.CoachApplicationService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/coach-applications")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCoachApplicationController {

    private final CoachApplicationService coachApplicationService;

    public AdminCoachApplicationController(CoachApplicationService coachApplicationService) {
        this.coachApplicationService = coachApplicationService;
    }

    @GetMapping
    public PageResponse<CoachApplicationResponse> list(
            @RequestParam(defaultValue = "PENDING") CoachApplicationStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return coachApplicationService.list(status, pageable);
    }

    @GetMapping("/{id}")
    public CoachApplicationResponse detail(@PathVariable Long id) {
        return coachApplicationService.detail(id);
    }

    /**
     * Creates the coach's User account (role=COACH) and emails them a password-setup link.
     * No password is ever generated for or shown to the admin — see CoachApplicationService.
     */
    @PostMapping("/{id}/approve")
    public CoachApplicationResponse approve(@PathVariable Long id) {
        return coachApplicationService.approve(id);
    }

    @PostMapping("/{id}/reject")
    public CoachApplicationResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return coachApplicationService.reject(id, request.reason());
    }
}
