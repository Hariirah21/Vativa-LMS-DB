package com.example.lms;

import com.example.lms.entity.CourseCategoryEntity;
import com.example.lms.entity.CourseEnrollmentEntity;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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
class CourseReportControllerIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseCategoryRepository categoryRepository;
    @Autowired private CourseEnrollmentRepository enrollmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User instructorOne;
    private User instructorTwo;
    private CourseEntity javaCourse;
    private CourseEntity safetyCourse;
    private CourseEntity dataCourse;

    @BeforeEach
    void setUp() {
        clearDatabase();

        instructorOne = saveUser("Iris", "Instructor", "iris@course-reports.test", "INSTRUCTOR");
        instructorTwo = saveUser("Ivan", "Instructor", "ivan@course-reports.test", "INSTRUCTOR");
        User learnerOne = saveUser("Alice", "Learner", "alice@course-reports.test", "LEARNER");
        User learnerTwo = saveUser("Bob", "Learner", "bob@course-reports.test", "LEARNER");
        User learnerThree = saveUser("Cara", "Learner", "cara@course-reports.test", "LEARNER");

        CourseCategoryEntity technical = saveCategory("Technical");
        CourseCategoryEntity compliance = saveCategory("Compliance & Safety");

        javaCourse = saveCourse(
                "Advanced Java Programming", "JAVA-101", technical.getId(), instructorOne.getId());
        safetyCourse = saveCourse(
                "Workplace Safety Essentials", "SAFE_100%", compliance.getId(), instructorTwo.getId());
        dataCourse = saveCourse(
                "Java Data Structures", null, technical.getId(), instructorOne.getId());

        saveEnrollment(javaCourse, learnerOne, 100);
        saveEnrollment(javaCourse, learnerTwo, 50);
        saveEnrollment(javaCourse, learnerThree, 0);
        saveEnrollment(dataCourse, learnerOne, 100);
    }

    @Test
    void defaultListReturnsAllCoursesWithDatabaseAggregates() throws Exception {
        mockMvc.perform(get("/api/course-reports")
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].courseId").value(javaCourse.getId()))
                .andExpect(jsonPath("$.data[0].courseTitle").value("Advanced Java Programming"))
                .andExpect(jsonPath("$.data[0].courseCode").value("JAVA-101"))
                .andExpect(jsonPath("$.data[0].category").value("Technical"))
                .andExpect(jsonPath("$.data[0].enrollments").value(3))
                .andExpect(jsonPath("$.data[0].completionRate").value(33.33))
                .andExpect(jsonPath("$.data[0].completedLearners").value(1))
                .andExpect(jsonPath("$.data[0].learnersNotStarted").value(1));
    }

    @Test
    void searchCoversTitleCodeAndCategoryAndEscapesWildcards() throws Exception {
        assertSingleSearchResult("  advanced JAVA  ", javaCourse.getId());
        assertSingleSearchResult("java-101", javaCourse.getId());
        assertSingleSearchResult("Compliance & Safety", safetyCourse.getId());
        assertSingleSearchResult("100%", safetyCourse.getId());
        assertSingleSearchResult("SAFE_", safetyCourse.getId());
    }

    @Test
    void categoryAndNumericExactFiltersAreSupported() throws Exception {
        assertSingleFilterResult("category", "Compliance & Safety", safetyCourse.getId());
        assertSingleFilterResult("enrollments", "3", javaCourse.getId());
        assertSingleFilterResult("completionRate", "33.33", javaCourse.getId());
        assertSingleFilterResult("learnersNotStarted", "1", javaCourse.getId());

        mockMvc.perform(get("/api/course-reports")
                        .param("completedLearners", "1")
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void numericRangeFiltersCanBeCombinedWithoutInventedThresholds() throws Exception {
        mockMvc.perform(get("/api/course-reports")
                        .param("minEnrollments", "1")
                        .param("maxEnrollments", "3")
                        .param("minCompletionRate", "30")
                        .param("maxCompletionRate", "40")
                        .param("minCompletedLearners", "1")
                        .param("maxLearnersNotStarted", "1")
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].courseId").value(javaCourse.getId()));
    }

    @Test
    void blankSearchUsesNoDefaultFilterAndNoMatchUsesRequiredEmptyMessage() throws Exception {
        mockMvc.perform(get("/api/course-reports")
                        .param("search", "   ")
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));

        mockMvc.perform(get("/api/course-reports")
                        .param("search", "does not exist")
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("No course report data available."))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void overviewReturnsProgressBreakdownAndMarksUnavailableMetricsAsNull() throws Exception {
        mockMvc.perform(get("/api/course-reports/{courseId}", javaCourse.getId())
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseTitle").value("Advanced Java Programming"))
                .andExpect(jsonPath("$.data.courseCode").value("JAVA-101"))
                .andExpect(jsonPath("$.data.category").value("Technical"))
                .andExpect(jsonPath("$.data.enrollments").value(3))
                .andExpect(jsonPath("$.data.learners").value(3))
                .andExpect(jsonPath("$.data.completionRate").value(33.33))
                .andExpect(jsonPath("$.data.completedLearners").value(1))
                .andExpect(jsonPath("$.data.learnersInProgress").value(1))
                .andExpect(jsonPath("$.data.learnersNotStarted").value(1))
                .andExpect(jsonPath("$.data.averageUserProgress").value(50.0))
                .andExpect(jsonPath("$.data.totalTimeSpent").value(nullValue()))
                .andExpect(jsonPath("$.data.certificatesIssued").value(nullValue()));
    }

    @Test
    void zeroEnrollmentCourseReturnsZeroWithoutDivisionByZero() throws Exception {
        mockMvc.perform(get("/api/course-reports/{courseId}", safetyCourse.getId())
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enrollments").value(0))
                .andExpect(jsonPath("$.data.completedLearners").value(0))
                .andExpect(jsonPath("$.data.learnersInProgress").value(0))
                .andExpect(jsonPath("$.data.learnersNotStarted").value(0))
                .andExpect(jsonPath("$.data.completionRate").value(0))
                .andExpect(jsonPath("$.data.averageUserProgress").value(0));
    }

    @Test
    void missingAndInvalidCourseUseTheRequiredErrors() throws Exception {
        mockMvc.perform(get("/api/course-reports/{courseId}", Long.MAX_VALUE)
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "The selected course is no longer available. Please select another course."));

        mockMvc.perform(get("/api/course-reports/{courseId}", 0)
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Course ID must be a positive number."));
    }

    @Test
    void searchAndFilterValidationRejectInvalidInput() throws Exception {
        mockMvc.perform(get("/api/course-reports")
                        .param("search", "a".repeat(101))
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/course-reports")
                        .param("minEnrollments", "5")
                        .param("maxEnrollments", "2")
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Minimum enrollments must not exceed maximum enrollments."));

        mockMvc.perform(get("/api/course-reports")
                        .param("completionRate", "101")
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void instructorCanOnlyViewAndExportOwnedCourses() throws Exception {
        mockMvc.perform(get("/api/course-reports")
                        .with(user(instructorOne.getEmail()).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].courseId").value(javaCourse.getId()))
                .andExpect(jsonPath("$.data[1].courseId").value(dataCourse.getId()));

        mockMvc.perform(get("/api/course-reports/{courseId}", safetyCourse.getId())
                        .with(user(instructorOne.getEmail()).roles("INSTRUCTOR")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "You are not authorized to view this course report."));

        String csv = mockMvc.perform(get("/api/course-reports/export/csv")
                        .with(user(instructorOne.getEmail()).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("Advanced Java Programming", "Java Data Structures")
                .doesNotContain("Workplace Safety Essentials");
    }

    @Test
    void unauthenticatedAndLearnerRolesCannotAccessCourseReports() throws Exception {
        mockMvc.perform(get("/api/course-reports"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/course-reports")
                        .with(user("learner@course-reports.test").roles("LEARNER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/course-reports/export/excel")
                        .with(user("learner@course-reports.test").roles("LEARNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void csvExportUsesCurrentSearchAndFilterAndExpectedColumns() throws Exception {
        String csv = mockMvc.perform(get("/api/course-reports/export/csv")
                        .param("search", "JAVA-101")
                        .param("minEnrollments", "2")
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string(
                        "Content-Disposition",
                        containsString("attachment; filename=\"course-reports.csv\"")))
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).contains(
                        "\"Course\",\"Course Code\",\"Category\",\"Enrollments\"",
                        "\"Advanced Java Programming\",\"JAVA-101\"",
                        "\"Total Time Spent\",\"Certificates Issued\"")
                .doesNotContain("Workplace Safety Essentials", "Java Data Structures");
        assertThat(csv.lines()).hasSize(2);
    }

    @Test
    void excelExportIsAValidOpenXmlWorkbookWithCurrentCriteria() throws Exception {
        byte[] content = mockMvc.perform(get("/api/course-reports/export/excel")
                        .param("category", "Compliance & Safety")
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(
                        "Content-Disposition",
                        containsString("attachment; filename=\"course-reports.xlsx\"")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(content).startsWith((byte) 'P', (byte) 'K');
        Map<String, String> entries = unzipTextEntries(content);
        assertThat(entries).containsKeys(
                "[Content_Types].xml", "xl/workbook.xml", "xl/worksheets/sheet1.xml");
        assertThat(entries.get("xl/workbook.xml")).contains("Course Report");
        assertThat(entries.get("xl/worksheets/sheet1.xml"))
                .contains("Workplace Safety Essentials", "SAFE_100%")
                .doesNotContain("Advanced Java Programming");
    }

    @Test
    void emptyDatabaseReturnsEmptyListAndHeaderOnlyExports() throws Exception {
        clearDatabase();

        mockMvc.perform(get("/api/course-reports")
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("No course report data available."))
                .andExpect(jsonPath("$.data", hasSize(0)));

        String csv = mockMvc.perform(get("/api/course-reports/export/csv")
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(csv.lines()).hasSize(1);
    }

    private void assertSingleSearchResult(String search, Long courseId) throws Exception {
        mockMvc.perform(get("/api/course-reports")
                        .param("search", search)
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].courseId").value(courseId));
    }

    private void assertSingleFilterResult(String name, String value, Long courseId) throws Exception {
        mockMvc.perform(get("/api/course-reports")
                        .param(name, value)
                        .with(user("admin@course-reports.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].courseId").value(courseId));
    }

    private Map<String, String> unzipTextEntries(byte[] content) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private User saveUser(String firstName, String lastName, String email, String role) {
        return userRepository.save(User.builder()
                .firstName(firstName)
                .lastName(lastName)
                .email(email)
                .countryCode("+91")
                .phoneNumber(String.valueOf(
                        Math.abs(email.hashCode()) % 1_000_000_000L + 1_000_000_000L))
                .password("test-password")
                .role(role)
                .acceptedTerms(true)
                .active(true)
                .build());
    }

    private CourseCategoryEntity saveCategory(String name) {
        CourseCategoryEntity category = new CourseCategoryEntity();
        category.setName(name);
        category.setActive(true);
        return categoryRepository.save(category);
    }

    private CourseEntity saveCourse(
            String name, String courseCode, Long categoryId, Long instructorId) {
        return courseRepository.save(CourseEntity.builder()
                .name(name)
                .courseCode(courseCode)
                .categoryId(categoryId)
                .instructorId(instructorId)
                .level("BEGINNER")
                .build());
    }

    private void saveEnrollment(CourseEntity course, User learner, int progress) {
        enrollmentRepository.save(CourseEnrollmentEntity.builder()
                .courseId(course.getId())
                .userId(learner.getId())
                .progressPercent(progress)
                .build());
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
}
