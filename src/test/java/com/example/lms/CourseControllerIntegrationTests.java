package com.example.lms;

import com.example.lms.config.CourseSchemaCompatibilityMigration;
import com.example.lms.entity.CourseCategoryEntity;
import com.example.lms.entity.User;
import com.example.lms.repository.CourseCategoryRepository;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseCategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CourseSchemaCompatibilityMigration courseSchemaMigration;

    private CourseCategoryEntity category;
    private User instructor;

    @BeforeEach
    void setUpCourseData() {
        courseRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        category = new CourseCategoryEntity();
        category.setName("Technology");
        category.setDescription("Technology courses");
        category.setActive(true);
        category = categoryRepository.save(category);

        instructor = userRepository.save(User.builder()
                .firstName("Course")
                .lastName("Instructor")
                .email("instructor@example.com")
                .countryCode("+91")
                .phoneNumber("7777777777")
                .password("test-password")
                .role("INSTRUCTOR")
                .acceptedTerms(true)
                .active(true)
                .build());
    }

    @Test
    void createCoursePersistsMultipartFormToDatabase() throws Exception {
        addLegacyRequiredCourseColumns();
        courseSchemaMigration.migrate();

        String json = """
                {
                  "name": "Java Course",
                  "description": "A persisted integration test course.",
                  "categoryId": %d,
                  "instructorId": %d,
                  "level": "BEGINNER"
                }
                """.formatted(category.getId(), instructor.getId());
        MockMultipartFile coursePart = new MockMultipartFile(
                "course",
                "course.json",
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/courses")
                        .file(coursePart)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Java Course"))
                .andExpect(jsonPath("$.data.instructorId").value(instructor.getId()));

        assertThat(courseRepository.count()).isEqualTo(1);
        assertThat(courseRepository.findAll().getFirst().getName()).isEqualTo("Java Course");
        assertThat(isNullable("category")).isEqualTo("YES");
        assertThat(isNullable("instructor")).isEqualTo("YES");
        assertThat(isNullable("level")).isEqualTo("YES");

        mockMvc.perform(get("/api/courses")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Java Course"));
    }

    @Test
    void instructorCourseIsAlwaysAssignedToTheSignedInInstructor() throws Exception {
        User otherInstructor = userRepository.save(User.builder()
                .firstName("Other")
                .lastName("Instructor")
                .email("other-instructor@example.com")
                .countryCode("+91")
                .phoneNumber("8888888888")
                .password("test-password")
                .role("INSTRUCTOR")
                .acceptedTerms(true)
                .active(true)
                .build());
        String json = """
                {
                  "name": "Instructor-owned Course",
                  "categoryId": %d,
                  "instructorId": %d,
                  "level": "INTERMEDIATE"
                }
                """.formatted(category.getId(), otherInstructor.getId());
        MockMultipartFile coursePart = new MockMultipartFile(
                "course",
                "course.json",
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/courses")
                        .file(coursePart)
                        .with(user(instructor.getEmail()).roles("INSTRUCTOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.categoryId").value(category.getId()))
                .andExpect(jsonPath("$.data.categoryName").value("Technology"))
                .andExpect(jsonPath("$.data.instructorId").value(instructor.getId()))
                .andExpect(jsonPath("$.data.instructorName").value("Course Instructor"));

        assertThat(courseRepository.findAll().getFirst().getInstructorId())
                .isEqualTo(instructor.getId());
    }

    @Test
    void activeCategoriesReturnCleanDatabaseDto() throws Exception {
        mockMvc.perform(get("/api/course-categories/active")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(category.getId()))
                .andExpect(jsonPath("$.data[0].name").value("Technology"))
                .andExpect(jsonPath("$.data[0].description").value("Technology courses"))
                .andExpect(jsonPath("$.data[0].active").value(true));
    }

    @Test
    void adminSeesAllActiveInstructors() throws Exception {
        userRepository.save(User.builder()
                .firstName("Second").lastName("Instructor")
                .email("second@example.com").countryCode("+91").phoneNumber("9999999999")
                .password("test-password").role("INSTRUCTOR")
                .acceptedTerms(true).active(true).build());

        mockMvc.perform(get("/api/instructors/active")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void instructorSeesOnlyTheirOwnProfile() throws Exception {
        mockMvc.perform(get("/api/instructors/active")
                        .with(user(instructor.getEmail()).roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(instructor.getId()));
    }

    @Test
    void invalidCategoryAndInstructorReturnNotFound() throws Exception {
        performCreate(999999L, instructor.getId(), "admin@example.com", "ADMIN")
                .andExpect(status().isNotFound());
        performCreate(category.getId(), 999999L, "admin@example.com", "ADMIN")
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedAndUnauthorizedLookupsAreRejected() throws Exception {
        mockMvc.perform(get("/api/course-categories/active"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/instructors/active")
                        .with(user("student@example.com").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions performCreate(
            Long categoryId, Long instructorId, String email, String role) throws Exception {
        String json = """
                {
                  "name": "Validation Course",
                  "categoryId": %d,
                  "instructorId": %d,
                  "level": "BEGINNER"
                }
                """.formatted(categoryId, instructorId);
        MockMultipartFile coursePart = new MockMultipartFile(
                "course", "course.json", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/courses")
                .file(coursePart)
                .with(user(email).roles(role)));
    }

    private void addLegacyRequiredCourseColumns() {
        jdbcTemplate.execute("ALTER TABLE courses DROP COLUMN IF EXISTS category");
        jdbcTemplate.execute("ALTER TABLE courses DROP COLUMN IF EXISTS instructor");
        jdbcTemplate.execute("ALTER TABLE courses DROP COLUMN IF EXISTS level");
        jdbcTemplate.execute("ALTER TABLE courses ADD COLUMN category VARCHAR(100) NOT NULL");
        jdbcTemplate.execute("ALTER TABLE courses ADD COLUMN instructor VARCHAR(100) NOT NULL");
        jdbcTemplate.execute("ALTER TABLE courses ADD COLUMN level VARCHAR(32) NOT NULL");
    }

    private String isNullable(String column) {
        return jdbcTemplate.queryForObject("""
                SELECT is_nullable
                FROM information_schema.columns
                WHERE LOWER(table_schema) = LOWER(CURRENT_SCHEMA())
                  AND LOWER(table_name) = 'courses'
                  AND LOWER(column_name) = ?
                """, String.class, column);
    }
}
