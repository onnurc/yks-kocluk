package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.PublicPackageResponse;
import com.ykskocluk.demo.service.PackageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/packages")
public class PublicPackageController {

    private final PackageService packageService;

    public PublicPackageController(PackageService packageService) {
        this.packageService = packageService;
    }

    @GetMapping
    public List<PublicPackageResponse> list() {
        return packageService.listPublicActive();
    }
}
