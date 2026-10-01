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
    void superAdminCreatesAndReadsPackageWithFrontendContract() throws Exception {
        mockMvc.perform(post("/api/packages")
                        .with(user("superadmin@example.com").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("Premium")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Professional"))
                .andExpect(jsonPath("$.data.availablePackage").value("Premium"))
                .andExpect(jsonPath("$.data.description").value("Professional package"))
                .andExpect(jsonPath("$.data.price").value(499.0))
                .andExpect(jsonPath("$.data.billingCycle").value("Monthly"))
                .andExpect(jsonPath("$.data.userLimit").value(25))
                .andExpect(jsonPath("$.data.storageLimit").value(100))
                .andExpect(jsonPath("$.data.permissions").isArray());

        mockMvc.perform(get("/api/packages")
                        .with(user("superadmin@example.com").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].availablePackage").value("Premium"))
                .andExpect(jsonPath("$.data[0].permissions").isArray());
    }

    @Test
    void unsupportedFrontendPackageOptionIsRejected() throws Exception {
        mockMvc.perform(post("/api/packages")
                        .with(user("superadmin@example.com").roles("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody("AVAILABLE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.availablePackage")
                        .value("Package must be Basic, Standard, Premium, or Existing"));
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
                  "permissions": [
                    {
                      "id": "course-management",
                      "name": "Course Management",
                      "enabled": true,
                      "features": [
                        {
                          "id": "create-course",
                          "name": "Create Course",
                          "permissions": {
                            "create": true,
                            "read": true,
                            "update": false,
                            "delete": false
                          }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(availability);
    }
}
