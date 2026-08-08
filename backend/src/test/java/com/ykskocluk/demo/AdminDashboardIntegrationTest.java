package com.ykskocluk.demo;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AdminDashboardIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void adminReadModelsLoadAgainstPostgresAndNonAdminsAreDenied() throws Exception {
        String admin = login("admin@yks.local", "admin1234");
        String student = register("admin-dashboard-auth-student@example.com", "STUDENT");
        String coach = register("admin-dashboard-auth-coach@example.com", "COACH");

        mvc.perform(get("/api/v1/admin/dashboard/summary").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalStudentCount").isNumber())
                .andExpect(jsonPath("$.grossRevenueThisMonth").isNumber());
        mvc.perform(get("/api/v1/admin/users?role=STUDENT&search=admin-dashboard-auth")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/admin/finance/summary").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.netCollectedAmount").isNumber());
        mvc.perform(get("/api/v1/admin/sessions?type=ALL").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());

        mvc.perform(get("/api/v1/admin/dashboard/summary").header("Authorization", "Bearer " + student))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/dashboard/summary").header("Authorization", "Bearer " + coach))
                .andExpect(status().isForbidden());
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String register(String email, String role) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","fullName":"Admin Dashboard Test","role":"%s","dateOfBirth":"2005-01-01","acceptedTermsDocumentId":3,"acceptedExplicitConsentDocumentId":2}
                                """.formatted(email, role)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
