package com.example.lms;

import com.example.lms.entity.CourseCategoryEntity;
import com.example.lms.entity.CourseEnrollmentEntity;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.LoginActivityEntity;
import com.example.lms.entity.User;
import com.example.lms.repository.CourseCategoryRepository;
import com.example.lms.repository.CourseEnrollmentRepository;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.LoginActivityRepository;
import com.example.lms.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LearnerReportControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseCategoryRepository categoryRepository;
    @Autowired private CourseEnrollmentRepository enrollmentRepository;
    @Autowired private LoginActivityRepository loginActivityRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User instructorOne;
    private User instructorTwo;
    private User alice;
    private User bob;
    private User cara;
    private User dana;
    private User inactiveLearner;

    @BeforeEach
    void setUp() {
        clearDatabase();

        instructorOne = saveUser("Ian", "Instructor", "ian@reports.test", "INSTRUCTOR", true);
        instructorTwo = saveUser("Ivy", "Instructor", "ivy@reports.test", "ROLE_INSTRUCTOR", true);
        alice = saveUser("Alice", "O'Neil+7", "alice.7@reports.test", "LEARNER", true);
        bob = saveUser("Bob", "Incomplete", "bob@reports.test", "ROLE_LEARNER", true);
        cara = saveUser("Cara", "Unassigned_100%", "cara@reports.test", "LEARNER", true);
        dana = saveUser("Dana", "Other Course", "dana@reports.test", "LEARNER", true);
        inactiveLearner = saveUser("Inactive", "Learner", "inactive@reports.test", "LEARNER", false);
        saveUser("Not", "A Learner", "admin@reports.test", "ADMIN", true);

        CourseCategoryEntity category = new CourseCategoryEntity();
        category.setName("Learner Report Testing");
        category.setActive(true);
        category = categoryRepository.save(category);

        CourseEntity courseOne = saveCourse("Report Course One", category.getId(), instructorOne.getId());
        CourseEntity courseTwo = saveCourse("Report Course Two", category.getId(), instructorOne.getId());
        CourseEntity otherCourse = saveCourse("Report Course Three", category.getId(), instructorTwo.getId());

        saveEnrollment(courseOne.getId(), alice.getId(), 100, 80);
        saveEnrollment(courseTwo.getId(), alice.getId(), 45, 60);
        // Deliberately invalid legacy score: report aggregation must exclude it.
        saveEnrollment(otherCourse.getId(), alice.getId(), 100, 150);
        saveEnrollment(courseTwo.getId(), bob.getId(), 10, null);
        saveEnrollment(otherCourse.getId(), dana.getId(), 20, 90);

        loginActivityRepository.save(LoginActivityEntity.builder()
                .userId(alice.getId())
                .loggedInAt(LocalDateTime.of(2026, 9, 7, 8, 30))
                .expiresAt(LocalDateTime.of(2026, 9, 7, 9, 30))
                .build());
        loginActivityRepository.save(LoginActivityEntity.builder()
                .userId(alice.getId())
                .loggedInAt(LocalDateTime.of(2026, 9, 8, 9, 45))
                .expiresAt(LocalDateTime.of(2026, 9, 8, 10, 45))
                .build());
    }

    @Test
    void adminDefaultListReturnsEveryAvailableLearnerAndRequiredFields() throws Exception {
        mockMvc.perform(get("/api/learner-reports")
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[0].learnerId").value(alice.getId()))
                .andExpect(jsonPath("$.data[0].learnerName").value("Alice O'Neil+7"))
                .andExpect(jsonPath("$.data[0].role").value("LEARNER"))
                .andExpect(jsonPath("$.data[0].department").value(nullValue()));
    }

    @Test
    void instructorListIsRestrictedToLearnersInOwnedCourses() throws Exception {
        mockMvc.perform(get("/api/learner-reports")
                        .with(user(instructorOne.getEmail()).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].learnerId").value(alice.getId()))
                .andExpect(jsonPath("$.data[1].learnerId").value(bob.getId()));
    }

    @Test
    void searchIsTrimmedCaseInsensitiveAndAcceptsNumbersAndSpecialCharacters() throws Exception {
        mockMvc.perform(get("/api/learner-reports")
                        .param("search", "  o'NEIL+7  ")
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].learnerId").value(alice.getId()));

        mockMvc.perform(get("/api/learner-reports")
                        .param("search", "alice.7@reports.test")
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        mockMvc.perform(get("/api/learner-reports")
                        .param("search", "100%")
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].learnerId").value(cara.getId()));
    }

    @Test
    void searchWithNoMatchAndEmptySearchReturnSuccessfulResults() throws Exception {
        mockMvc.perform(get("/api/learner-reports")
                        .param("search", "No such learner")
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(get("/api/learner-reports")
                        .param("search", "   ")
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)));
    }

    @Test
    void searchLongerThanOneHundredCharactersIsRejected() throws Exception {
        mockMvc.perform(get("/api/learner-reports")
                        .param("search", "a".repeat(101))
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Search must not exceed 100 characters."));
    }

    @Test
    void eachSupportedFilterUsesExistingEnrollmentCompletionRules() throws Exception {
        mockMvc.perform(get("/api/learner-reports")
                        .param("filter", "Completed Courses")
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].learnerId").value(alice.getId()));

        mockMvc.perform(get("/api/learner-reports")
                        .param("filter", "Incompleted Courses")
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));

        mockMvc.perform(get("/api/learner-reports")
                        .param("filter", "Enrolled Courses")
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
    }

    @Test
    void filterCanReturnEmptyAndInvalidFilterIsRejected() throws Exception {
        mockMvc.perform(get("/api/learner-reports")
                        .param("search", "Cara")
                        .param("filter", "Completed Courses")
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(get("/api/learner-reports")
                        .param("filter", "Incomplete Courses")
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void instructorFilterIsEvaluatedOnlyAgainstOwnedCourses() throws Exception {
        mockMvc.perform(get("/api/learner-reports")
                        .param("filter", "Completed Courses")
                        .with(user(instructorTwo.getEmail()).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].learnerId").value(alice.getId()));

        mockMvc.perform(get("/api/learner-reports")
                        .param("filter", "Incompleted Courses")
                        .with(user(instructorTwo.getEmail()).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].learnerId").value(dana.getId()));
    }

    @Test
    void adminPreviewReturnsAllPersistedAndDerivedMetricsWithoutInflation() throws Exception {
        String body = mockMvc.perform(get("/api/learner-reports/{learnerId}", alice.getId())
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.learnerId").value(alice.getId()))
                .andExpect(jsonPath("$.data.learnerName").value("Alice O'Neil+7"))
                .andExpect(jsonPath("$.data.role").value("LEARNER"))
                .andExpect(jsonPath("$.data.department").value(nullValue()))
                .andExpect(jsonPath("$.data.registrationDate").isNotEmpty())
                .andExpect(jsonPath("$.data.lastLogin").value("2026-09-08T09:45:00"))
                .andExpect(jsonPath("$.data.lastActivity").value("2026-09-08T10:15:00"))
                .andExpect(jsonPath("$.data.completedCourses").value(2))
                .andExpect(jsonPath("$.data.incompleteCourses").value(1))
                .andExpect(jsonPath("$.data.enrolledCourses").value(3))
                .andExpect(jsonPath("$.data.assignedCourses").value(3))
                .andExpect(jsonPath("$.data.averageScore").value(70.0))
                .andExpect(jsonPath("$.data.studyTime").value(nullValue()))
                .andExpect(jsonPath("$.data.totalTimeOnPlatform").value(nullValue()))
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).get("data");
        assertThat(data.get("lastActivity").asText()).isNotEqualTo(data.get("lastLogin").asText());
    }

    @Test
    void instructorPreviewAggregatesOnlyOwnedCourses() throws Exception {
        mockMvc.perform(get("/api/learner-reports/{learnerId}", alice.getId())
                        .with(user(instructorOne.getEmail()).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedCourses").value(1))
                .andExpect(jsonPath("$.data.incompleteCourses").value(1))
                .andExpect(jsonPath("$.data.enrolledCourses").value(2))
                .andExpect(jsonPath("$.data.assignedCourses").value(2))
                .andExpect(jsonPath("$.data.averageScore").value(70.0));
    }

    @Test
    void previewWithNoScoresOrActivityUsesNullAndZeroConvention() throws Exception {
        mockMvc.perform(get("/api/learner-reports/{learnerId}", cara.getId())
                        .with(user("admin@reports.test").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedCourses").value(0))
                .andExpect(jsonPath("$.data.incompleteCourses").value(0))
                .andExpect(jsonPath("$.data.enrolledCourses").value(0))
                .andExpect(jsonPath("$.data.averageScore").value(nullValue()))
                .andExpect(jsonPath("$.data.lastLogin").value(nullValue()))
                .andExpect(jsonPath("$.data.lastActivity").value(nullValue()));
    }

    @Test
    void missingUnavailableAndUnauthorizedLearnerPreviewAreBlocked() throws Exception {
        mockMvc.perform(get("/api/learner-reports/{learnerId}", Long.MAX_VALUE)
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "The selected learner is no longer available. Please select another learner."));

        mockMvc.perform(get("/api/learner-reports/{learnerId}", inactiveLearner.getId())
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/learner-reports/{learnerId}", dana.getId())
                        .with(user(instructorOne.getEmail()).roles("INSTRUCTOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "You are not authorized to view this learner report."));
    }

    @Test
    void unauthenticatedAndUnsupportedRolesCannotAccessAnyReportOperation() throws Exception {
        mockMvc.perform(get("/api/learner-reports"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/learner-reports")
                        .with(user("learner@reports.test").roles("LEARNER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/learner-reports/export")
                        .with(user("learner@reports.test").roles("LEARNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void exportReturnsUtf8CsvForCurrentSearchAndFilter() throws Exception {
        String csv = mockMvc.perform(get("/api/learner-reports/export")
                        .param("search", "Alice")
                        .param("filter", "Completed Courses")
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string(
                        "Content-Disposition",
                        containsString("attachment; filename=\"learner-reports.csv\"")))
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("\"Learner ID\",\"Learner Name\"");
        assertThat(csv).contains("\"Alice O'Neil+7\"");
        assertThat(csv).contains("\"70.00\"");
        assertThat(csv).doesNotContain("Bob Incomplete", "Dana Other Course");
        assertThat(csv.lines()).hasSize(2);
    }

    @Test
    void instructorExportUsesTheSameOwnershipScopeAsListAndPreview() throws Exception {
        String csv = mockMvc.perform(get("/api/learner-reports/export")
                        .with(user(instructorOne.getEmail()).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("Alice O'Neil+7", "Bob Incomplete");
        assertThat(csv).doesNotContain("Cara Unassigned_100%", "Dana Other Course");
        assertThat(csv).contains("\"1\",\"1\",\"2\",\"2\",\"70.00\"");
    }

    @Test
    void emptyLearnerDatasetReturnsSuccessfulEmptyListAndHeaderOnlyExport() throws Exception {
        clearDatabase();

        mockMvc.perform(get("/api/learner-reports")
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        String csv = mockMvc.perform(get("/api/learner-reports/export")
                        .with(user("admin@reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(csv.lines()).hasSize(1);
    }

    private void clearDatabase() {
        jdbcTemplate.update("delete from ebook_media");
        jdbcTemplate.update("delete from ebooks");
        jdbcTemplate.update("delete from multimedia");
        jdbcTemplate.update("delete from sections");
        jdbcTemplate.update("delete from login_activities");
        jdbcTemplate.update("delete from course_enrollments");
        jdbcTemplate.update("delete from courses");
        jdbcTemplate.update("delete from course_categories");
        jdbcTemplate.update("delete from roles");
        jdbcTemplate.update("delete from password_reset_token");
        jdbcTemplate.update("delete from users");
    }

    private User saveUser(String firstName,
                          String lastName,
                          String email,
                          String role,
                          boolean active) {
        return userRepository.save(User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .countryCode("+91")
                .phoneNumber(String.valueOf(Math.abs(email.hashCode()) % 1_000_000_000L + 1_000_000_000L))
                .password("test-password")
                .role(role)
                .acceptedTerms(true)
                .active(active)
                .build());
    }

    private CourseEntity saveCourse(String name, Long categoryId, Long instructorId) {
        return courseRepository.save(CourseEntity.builder()
                .name(name)
                .categoryId(categoryId)
                .instructorId(instructorId)
                .level("BEGINNER")
                .build());
    }

    private void saveEnrollment(Long courseId, Long userId, int progress, Integer score) {
        enrollmentRepository.save(CourseEnrollmentEntity.builder()
                .courseId(courseId)
                .userId(userId)
                .progressPercent(progress)
                .completedAt(progress >= 100
                        ? LocalDateTime.of(2026, 9, 8, 10, 15)
                        : null)
                .scorePercent(score)
                .build());
    }
}
