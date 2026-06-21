package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.AvailabilityCreateRequest;
import com.ykskocluk.demo.dto.AvailabilityResponse;
import com.ykskocluk.demo.service.CoachAvailabilityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A coach's own availability management. Students view open slots via
 * {@code GET /api/v1/coaches/{id}/availability} (see CoachSearchController).
 */
@RestController
@RequestMapping("/api/v1/coach/availability")
@PreAuthorize("hasRole('COACH')")
public class CoachAvailabilityController {

    private final CoachAvailabilityService availabilityService;

    public CoachAvailabilityController(CoachAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @PostMapping
    public ResponseEntity<AvailabilityResponse> create(@AuthenticationPrincipal Long coachUserId,
                                                       @Valid @RequestBody AvailabilityCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(availabilityService.createOwn(coachUserId, request));
    }

    @GetMapping
    public List<AvailabilityResponse> listOwn(@AuthenticationPrincipal Long coachUserId) {
        return availabilityService.listOwn(coachUserId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long coachUserId, @PathVariable Long id) {
        availabilityService.deleteOwn(coachUserId, id);
        return ResponseEntity.noContent().build();
    }
}
