package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.TrialConsultationResponse;
import com.ykskocluk.demo.service.TrialConsultationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/coach/trial-consultations")
@PreAuthorize("hasRole('COACH')")
public class CoachTrialConsultationController {
    private final TrialConsultationService service;
    public CoachTrialConsultationController(TrialConsultationService service) { this.service = service; }
    @GetMapping public List<TrialConsultationResponse> list(@AuthenticationPrincipal Long id) { return service.coachTrials(id); }
    @PostMapping("/{id}/confirm") public TrialConsultationResponse confirm(@AuthenticationPrincipal Long user, @PathVariable Long id) { return service.confirm(user, id); }
    @PostMapping("/{id}/cancel") public TrialConsultationResponse cancel(@AuthenticationPrincipal Long user, @PathVariable Long id) { return service.cancelByCoach(user, id); }
    @PostMapping("/{id}/complete") public TrialConsultationResponse complete(@AuthenticationPrincipal Long user, @PathVariable Long id) { return service.complete(user, id); }
    @PostMapping("/{id}/no-show") public TrialConsultationResponse noShow(@AuthenticationPrincipal Long user, @PathVariable Long id) { return service.noShow(user, id); }
}
