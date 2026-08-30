package com.ykskocluk.demo.service;

import com.ykskocluk.demo.dto.AdminCoachCreateRequest;
import com.ykskocluk.demo.dto.AdminCoachCreateResponse;
import com.ykskocluk.demo.enums.AccountOrigin;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCoachCreationService {
    private final CoachAccountProvisioningService provisioningService;

    public AdminCoachCreationService(CoachAccountProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Transactional
    public AdminCoachCreateResponse create(AdminCoachCreateRequest request) {
        var provisioned = provisioningService.provision(
                request.fullName(), request.email(), AccountOrigin.ADMIN_MANUAL);
        return new AdminCoachCreateResponse(
                provisioned.user().getId(), provisioned.profile().getId(),
                provisioned.user().getFullName(), provisioned.user().getEmail(),
                provisioned.user().getStatus(), provisioned.profile().getStatus());
    }
}
