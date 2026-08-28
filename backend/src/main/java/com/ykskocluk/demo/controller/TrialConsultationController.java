package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.*;
import com.ykskocluk.demo.service.TrialConsultationService;
import com.ykskocluk.demo.security.ratelimit.AuthenticatedActionRateLimitService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/trial-consultations")
@PreAuthorize("hasRole('STUDENT')")
public class TrialConsultationController {
    private final TrialConsultationService service;
    private final AuthenticatedActionRateLimitService actionRateLimit;
    public TrialConsultationController(TrialConsultationService service,
                                       AuthenticatedActionRateLimitService actionRateLimit) {
        this.service = service;
        this.actionRateLimit = actionRateLimit;
    }
    @PostMapping public ResponseEntity<TrialConsultationResponse> request(@AuthenticationPrincipal Long studentId,
            @Valid @RequestBody TrialConsultationCreateRequest request) {
        actionRateLimit.checkTrialCreate(studentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.request(studentId, request));
    }
    @GetMapping("/me") public List<TrialConsultationResponse> me(@AuthenticationPrincipal Long studentId) {
        return service.studentTrials(studentId);
    }
    @PostMapping("/{id}/cancel") public TrialConsultationResponse cancel(@AuthenticationPrincipal Long studentId,
            @PathVariable Long id) { return service.cancelByStudent(studentId, id); }
}
