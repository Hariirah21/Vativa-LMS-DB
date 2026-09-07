package com.example.lms;

import com.example.lms.repository.PackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PackageControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PackageRepository packageRepository;

    @BeforeEach
    void clearPackages() {
        packageRepository.deleteAll();
    }

    @Test
    void superAdminCreatesAndReadsPackageWithAvailability() throws Exception {
        mockMvc.perform(post("/api/packages")
                        .with(user("superadmin@example.com").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("AVAILABLE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Professional"))
                .andExpect(jsonPath("$.availablePackage").value("AVAILABLE"));

        mockMvc.perform(get("/api/packages")
                        .with(user("superadmin@example.com").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availablePackage").value("AVAILABLE"));
    }

    @Test
    void unsupportedAvailabilityIsRejected() throws Exception {
        mockMvc.perform(post("/api/packages")
                        .with(user("superadmin@example.com").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("SOMETIMES")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.availablePackage")
                        .value("Package availability must be AVAILABLE or UNAVAILABLE"));
    }

    private String requestBody(String availability) {
        return """
                {
                  "name": "Professional",
                  "availablePackage": "%s",
                  "description": "Professional package",
                  "price": 499.0,
                  "billingCycle": "Monthly",
                  "userLimit": 25,
                  "storageLimit": 100,
                  "permissions": []
                }
                """.formatted(availability);
    }
}
