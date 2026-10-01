package com.example.lms;

import com.example.lms.entity.CourseCategoryEntity;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.User;
import com.example.lms.repository.CourseCategoryRepository;
import com.example.lms.repository.CourseEnrollmentRepository;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.UserRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseEnrollmentIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private CourseEnrollmentRepository enrollmentRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseCategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;

    private CourseEntity course;
    private User learner;

    @BeforeEach
    void setUp() {
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        CourseCategoryEntity category = new CourseCategoryEntity();
        category.setName("Enrollment Testing");
        category.setActive(true);
        category = categoryRepository.save(category);

        User instructor = saveUser("instructor@enrollment.test", "INSTRUCTOR", true);
        learner = saveUser("learner@enrollment.test", "LEARNER", true);
        course = courseRepository.save(CourseEntity.builder()
                .name("Enrollment Course")
                .categoryId(category.getId())
                .instructorId(instructor.getId())
                .level("BEGINNER")
                .build());
    }

    @Test
    void adminCanEnrollListAndUnenrollLearner() throws Exception {
        String request = "{\"userIds\":[" + learner.getId() + "]}";

        mockMvc.perform(post("/api/courses/{courseId}/enrollments", course.getId())
                        .with(user("admin@test.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.affectedCount").value(1));

        assertThat(enrollmentRepository.existsByCourseIdAndUserId(course.getId(), learner.getId()))
                .isTrue();

        mockMvc.perform(get("/api/courses/{courseId}/enrollments", course.getId())
                        .with(user("admin@test.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(learner.getId()))
                .andExpect(jsonPath("$.data[0].status").value("Not Started"));

        mockMvc.perform(delete("/api/courses/{courseId}/enrollments", course.getId())
                        .with(user("admin@test.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.affectedCount").value(1));

        assertThat(enrollmentRepository.existsByCourseIdAndUserId(course.getId(), learner.getId()))
                .isFalse();
    }

    @Test
    void enrollmentRejectsInactiveOrNonLearnerUsersAndDuplicates() throws Exception {
        User inactive = saveUser("inactive@enrollment.test", "LEARNER", false);
        User admin = saveUser("another-admin@enrollment.test", "ADMIN", true);

        performEnroll(inactive.getId()).andExpect(status().isBadRequest());
        performEnroll(admin.getId()).andExpect(status().isBadRequest());
        performEnroll(learner.getId()).andExpect(status().isCreated());
        performEnroll(learner.getId()).andExpect(status().isConflict());
    }

    private org.springframework.test.web.servlet.ResultActions performEnroll(Long userId) throws Exception {
        return mockMvc.perform(post("/api/courses/{courseId}/enrollments", course.getId())
                .with(user("admin@test.com").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userIds\":[" + userId + "]}"));
    }

    private User saveUser(String email, String role, boolean active) {
        return userRepository.save(User.builder()
                .firstName("Test")
                .lastName("User")
                .email(email)
                .countryCode("+91")
                .phoneNumber(String.valueOf(Math.abs(email.hashCode()) % 1_000_000_000L + 1_000_000_000L))
                .password("test-password")
                .role(role)
                .acceptedTerms(true)
                .active(active)
                .build());
    }
}
