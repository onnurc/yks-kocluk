package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.AdminPackageCatalogResponse;
import com.ykskocluk.demo.repository.UserRepository;
import com.ykskocluk.demo.security.JwtService;
import com.ykskocluk.demo.service.AdminPackageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPackageController.class)
@Import(AdminPackageControllerTest.MethodSecurity.class)
class AdminPackageControllerTest {
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}
    @Autowired MockMvc mvc;
    @MockitoBean AdminPackageService service;
    @MockitoBean JwtService jwtService;
    @MockitoBean UserRepository userRepository;

    @Test @WithMockUser(roles = "ADMIN")
    void adminCanReadAndUpdatePackagePrice() throws Exception {
        when(service.getCatalog()).thenReturn(new AdminPackageCatalogResponse(null, null, false, null, List.of()));
        mvc.perform(get("/api/v1/admin/packages")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/admin/packages/ONE_MONTH").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":\"3200.00\",\"active\":true}"))
                .andExpect(status().isOk());
        verify(service).updatePackage(org.mockito.ArgumentMatchers.eq(com.ykskocluk.demo.enums.PackageType.ONE_MONTH),
                org.mockito.ArgumentMatchers.any());
    }

    @Test @WithMockUser(roles = "STUDENT")
    void nonAdminCannotManagePackages() throws Exception {
        mvc.perform(get("/api/v1/admin/packages")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/packages/ONE_MONTH").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"price\":\"3200.00\",\"active\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/admin/packages/exam-settings").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"examYear\":2027,\"examDate\":\"2027-06-20\",\"active\":true}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test @WithMockUser(roles = "ADMIN")
    void adminCanSaveExamYearDateAndActiveStatus() throws Exception {
        mvc.perform(put("/api/v1/admin/packages/exam-settings").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"examYear\":2027,\"examDate\":\"2027-06-20\",\"active\":true}"))
                .andExpect(status().isOk());
        verify(service).setExamSettings(2027, java.time.LocalDate.of(2027, 6, 20), true);
    }
}
