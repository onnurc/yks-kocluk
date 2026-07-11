package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.StudentProfileCreateRequest;
import com.ykskocluk.demo.dto.StudentProfileResponse;
import com.ykskocluk.demo.dto.StudentProfileUpdateRequest;
import com.ykskocluk.demo.service.StudentProfileService;
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
@RequestMapping("/api/v1/student/profile")
@PreAuthorize("hasRole('STUDENT')")
public class StudentProfileController {

    private final StudentProfileService studentProfileService;

    public StudentProfileController(StudentProfileService studentProfileService) {
        this.studentProfileService = studentProfileService;
    }

    @PostMapping
    public ResponseEntity<StudentProfileResponse> create(@AuthenticationPrincipal Long userId,
                                                         @Valid @RequestBody StudentProfileCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentProfileService.createOwn(userId, request));
    }

    @GetMapping("/me")
    public StudentProfileResponse getOwn(@AuthenticationPrincipal Long userId) {
        return studentProfileService.getOwn(userId);
    }

    @PutMapping("/me")
    public StudentProfileResponse updateOwn(@AuthenticationPrincipal Long userId,
                                            @Valid @RequestBody StudentProfileUpdateRequest request) {
        return studentProfileService.updateOwn(userId, request);
    }
}
