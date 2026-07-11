package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.SessionResponse;
import com.ykskocluk.demo.service.SessionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** A coach's view of the sessions booked against them. */
@RestController
@RequestMapping("/api/v1/coach/sessions")
@PreAuthorize("hasRole('COACH')")
public class CoachSessionController {

    private final SessionService sessionService;

    public CoachSessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public List<SessionResponse> mySessions(@AuthenticationPrincipal Long coachUserId) {
        return sessionService.myCoachSessions(coachUserId);
    }

    @PostMapping("/{id}/complete")
    public SessionResponse complete(@AuthenticationPrincipal Long coachUserId, @PathVariable Long id) {
        return sessionService.markCompleted(coachUserId, id);
    }

    @PostMapping("/{id}/no-show")
    public SessionResponse noShow(@AuthenticationPrincipal Long coachUserId, @PathVariable Long id) {
        return sessionService.markNoShow(coachUserId, id);
    }
}
