package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.CoachProfileResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.dto.RejectRequest;
import com.ykskocluk.demo.enums.CoachProfileStatus;
import com.ykskocluk.demo.service.CoachProfileService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
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
@RequestMapping("/api/v1/admin/coaches")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCoachController {

    private final CoachProfileService coachProfileService;

    public AdminCoachController(CoachProfileService coachProfileService) {
        this.coachProfileService = coachProfileService;
    }

    @GetMapping
    public PageResponse<CoachProfileResponse> list(
            @RequestParam(defaultValue = "PENDING") CoachProfileStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return coachProfileService.listByStatus(status, pageable);
    }

    @PostMapping("/{id}/approve")
    public CoachProfileResponse approve(@PathVariable Long id) {
        return coachProfileService.approve(id);
    }

    @PostMapping("/{id}/reject")
    public CoachProfileResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest request) {
        return coachProfileService.reject(id, request.reason());
    }
}
