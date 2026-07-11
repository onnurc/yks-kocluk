package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.PackageResponse;
import com.ykskocluk.demo.service.PackageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/packages")
public class PackageController {

    private final PackageService packageService;

    public PackageController(PackageService packageService) {
        this.packageService = packageService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<PackageResponse> list() {
        return packageService.listActive();
    }
}
