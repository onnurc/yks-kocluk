package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.AvailabilityResponse;
import com.ykskocluk.demo.dto.CoachDetailResponse;
import com.ykskocluk.demo.dto.CoachSummaryResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.enums.Track;
import com.ykskocluk.demo.service.CoachAvailabilityService;
import com.ykskocluk.demo.service.CoachSearchService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only coach discovery for students (admins may also browse). Only APPROVED
 * coaches are returned. COACH role is intentionally excluded (coaches use /coach/profile/me).
 */
@RestController
@RequestMapping("/api/v1/coaches")
@PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
public class CoachSearchController {

    private final CoachSearchService coachSearchService;
    private final CoachAvailabilityService coachAvailabilityService;

    public CoachSearchController(CoachSearchService coachSearchService,
                                 CoachAvailabilityService coachAvailabilityService) {
        this.coachSearchService = coachSearchService;
        this.coachAvailabilityService = coachAvailabilityService;
    }

    @GetMapping
    public PageResponse<CoachSummaryResponse> search(
            @RequestParam(required = false) Track track,
            @RequestParam(required = false) Long universityId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return coachSearchService.search(universityId, track, q, pageable);
    }

    @GetMapping("/{id}")
    public CoachDetailResponse get(@PathVariable Long id) {
        return coachSearchService.getApprovedCoach(id);
    }

    /** Open (unbooked) future slots for an APPROVED coach. */
    @GetMapping("/{id}/availability")
    public List<AvailabilityResponse> availability(@PathVariable Long id) {
        return coachAvailabilityService.listOpenSlots(id);
    }
}
