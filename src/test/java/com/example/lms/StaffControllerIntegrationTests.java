package com.example.lms;

import com.example.lms.entity.User;
import com.example.lms.repository.RoleRepository;
import com.example.lms.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaffControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private User registeredUser;

    @BeforeEach
    void setUpUsers() {
        roleRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .firstName("Test")
                .lastName("Admin")
                .email("admin@example.com")
                .countryCode("+91")
                .phoneNumber("9999999999")
                .password("test-password")
                .role("ADMIN")
                .acceptedTerms(true)
                .active(true)
                .build());
        registeredUser = userRepository.save(User.builder()
                .firstName("Jane")
                .lastName("Doe")
                .email("jane@example.com")
                .countryCode("+91")
                .phoneNumber("8888888888")
                .password("test-password")
                .role("LEARNER")
                .acceptedTerms(true)
                .active(true)
                .build());
    }

    @Test
    void adminAssignsRolesToRegisteredUsersWithoutStaffMemberTable() throws Exception {
        String createBody = requestBody("Jane Doe", "jane@example.com", "Instructor");

        String createResponse = mockMvc.perform(post("/api/staff")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(registeredUser.getId()))
                .andExpect(jsonPath("$.username").value("Jane Doe"))
                .andExpect(jsonPath("$.role").value("Instructor"))
                .andExpect(jsonPath("$.status").value("Active"))
                .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(createResponse);
        long userId = created.get("id").asLong();
        assertThat(roleRepository.existsByNameIgnoreCaseAndCreatedByAdminId(
                "Instructor",
                userRepository.findByEmailIgnoreCase("admin@example.com").orElseThrow().getId()))
                .isTrue();

        mockMvc.perform(get("/api/staff")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("jane@example.com"));

        mockMvc.perform(put("/api/staff/{id}", userId)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(
                                "Jane Doe",
                                "jane@example.com",
                                "Content Manager")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("Content Manager"));

        assertThat(userRepository.findById(userId).orElseThrow().getRole())
                .isEqualTo("Content Manager");

        mockMvc.perform(delete("/api/staff/{id}", userId)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(userId).orElseThrow().getActive()).isFalse();
    }

    private String requestBody(String username, String email, String role) {
        return """
                {
                  "username": "%s",
                  "email": "%s",
                  "role": "%s",
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
                """.formatted(username, email, role);
    }
}
