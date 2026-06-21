package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.SessionCreateRequest;
import com.ykskocluk.demo.dto.SessionResponse;
import com.ykskocluk.demo.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Student booking. A session is created by reserving an open availability slot; the
 * double-booking + weekly-quota rules live in {@link SessionService}.
 */
@RestController
@RequestMapping("/api/v1/sessions")
@PreAuthorize("hasRole('STUDENT')")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<SessionResponse> book(@AuthenticationPrincipal Long studentUserId,
                                                @Valid @RequestBody SessionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.book(studentUserId, request));
    }

    @GetMapping("/me")
    public List<SessionResponse> mySessions(@AuthenticationPrincipal Long studentUserId) {
        return sessionService.myStudentSessions(studentUserId);
    }

    @PostMapping("/{id}/cancel")
    public SessionResponse cancel(@AuthenticationPrincipal Long studentUserId, @PathVariable Long id) {
        return sessionService.cancel(studentUserId, id);
    }
}
