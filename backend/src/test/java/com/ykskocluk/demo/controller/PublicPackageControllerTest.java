package com.ykskocluk.demo.controller;

import com.ykskocluk.demo.dto.PublicPackageResponse;
import com.ykskocluk.demo.security.JwtAuthenticationFilter;
import com.ykskocluk.demo.service.PackageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicPackageController.class)
@AutoConfigureMockMvc(addFilters = false)
class PublicPackageControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean PackageService packageService;
    @MockitoBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void exposesOnlyThePublicMarketingProjection() throws Exception {
        given(packageService.listPublicActive()).willReturn(List.of(
                new PublicPackageResponse(7L, "3 Aylık", 1, 90, new BigDecimal("7990.00"))));

        mockMvc.perform(get("/api/v1/public/packages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].name").value("3 Aylık"))
                .andExpect(jsonPath("$[0].weeklySessions").value(1))
                .andExpect(jsonPath("$[0].durationDays").value(90))
                .andExpect(jsonPath("$[0].price").value(7990.00))
                .andExpect(jsonPath("$[0].active").doesNotExist())
                .andExpect(jsonPath("$[0].providerProductId").doesNotExist());
    }
}
