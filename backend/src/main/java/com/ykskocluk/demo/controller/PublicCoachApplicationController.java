package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.CoachApplicationRequest;
import com.ykskocluk.demo.security.ratelimit.AuthRateLimitService;
import com.ykskocluk.demo.service.CoachApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public "apply to become a coach" intake — no auth, no OAuth, a plain form. */
@RestController
@RequestMapping("/api/v1/public/coach-applications")
public class PublicCoachApplicationController {

    private final CoachApplicationService coachApplicationService;
    private final AuthRateLimitService rateLimitService;
    private final HttpServletRequest httpServletRequest;

    public PublicCoachApplicationController(CoachApplicationService coachApplicationService,
                                            AuthRateLimitService rateLimitService,
                                            HttpServletRequest httpServletRequest) {
        this.coachApplicationService = coachApplicationService;
        this.rateLimitService = rateLimitService;
        this.httpServletRequest = httpServletRequest;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void submit(@Valid @RequestBody CoachApplicationRequest request) {
        rateLimitService.checkCoachApplication(request.email(), httpServletRequest);
        coachApplicationService.submit(request);
    }
}
