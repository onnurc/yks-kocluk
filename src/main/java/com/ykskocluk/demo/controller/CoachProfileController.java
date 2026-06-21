package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.CoachProfileCreateRequest;
import com.ykskocluk.demo.dto.CoachProfileResponse;
import com.ykskocluk.demo.dto.CoachProfileUpdateRequest;
import com.ykskocluk.demo.service.CoachProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/coach/profile")
@PreAuthorize("hasRole('COACH')")
public class CoachProfileController {

    private final CoachProfileService coachProfileService;

    public CoachProfileController(CoachProfileService coachProfileService) {
        this.coachProfileService = coachProfileService;
    }

    @PostMapping
    public ResponseEntity<CoachProfileResponse> create(@AuthenticationPrincipal Long userId,
                                                       @Valid @RequestBody CoachProfileCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(coachProfileService.createOwn(userId, request));
    }

    @GetMapping("/me")
    public CoachProfileResponse getOwn(@AuthenticationPrincipal Long userId) {
        return coachProfileService.getOwn(userId);
    }

    @PutMapping("/me")
    public CoachProfileResponse updateOwn(@AuthenticationPrincipal Long userId,
                                          @Valid @RequestBody CoachProfileUpdateRequest request) {
        return coachProfileService.updateOwn(userId, request);
    }
}
