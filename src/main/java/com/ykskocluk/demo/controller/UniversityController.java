package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.UniversityCreateRequest;
import com.ykskocluk.demo.dto.UniversityResponse;
import com.ykskocluk.demo.service.UniversityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * University reference data: any authenticated user can list (for dropdowns);
 * only ADMIN can add new entries.
 */
@RestController
public class UniversityController {

    private final UniversityService universityService;

    public UniversityController(UniversityService universityService) {
        this.universityService = universityService;
    }

    @GetMapping("/api/v1/universities")
    @PreAuthorize("isAuthenticated()")
    public List<UniversityResponse> list() {
        return universityService.list();
    }

    @PostMapping("/api/v1/admin/universities")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UniversityResponse> create(@Valid @RequestBody UniversityCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(universityService.create(request));
    }
}
