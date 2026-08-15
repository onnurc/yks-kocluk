package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.CoachSummaryResponse;
import com.ykskocluk.demo.dto.PageResponse;
import com.ykskocluk.demo.enums.Track;
import com.ykskocluk.demo.service.CoachSearchService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, read-only catalogue of approved coaches with active accounts. */
@RestController
@RequestMapping("/api/v1/public/coaches")
public class PublicCoachController {

    private final CoachSearchService coachSearchService;

    public PublicCoachController(CoachSearchService coachSearchService) {
        this.coachSearchService = coachSearchService;
    }

    @GetMapping
    public PageResponse<CoachSummaryResponse> search(
            @RequestParam(required = false) Track track,
            @RequestParam(required = false) Long universityId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 9, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return coachSearchService.search(universityId, track, q, pageable);
    }
}
