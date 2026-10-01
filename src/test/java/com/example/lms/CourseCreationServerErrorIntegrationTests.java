package com.example.lms;

import com.example.lms.service.CourseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseCreationServerErrorIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseService courseService;

    @Test
    void unavailableServerReturnsTheExactCourseCreationMessage() throws Exception {
        when(courseService.createCourse(any(), isNull(), any()))
                .thenThrow(new DataAccessResourceFailureException("Database unavailable"));

        String request = """
                {
                  "name": "Unavailable Server Course",
                  "categoryId": 1,
                  "instructorId": 1,
                  "level": "BEGINNER"
                }
                """;
        MockMultipartFile coursePart = new MockMultipartFile(
                "course",
                "course.json",
                MediaType.APPLICATION_JSON_VALUE,
                request.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/courses")
                        .file(coursePart)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "Unable to create the course due to a server error. Please try again later."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
