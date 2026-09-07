package com.example.lms;

import com.example.lms.config.CourseSchemaCompatibilityMigration;
import com.example.lms.entity.CourseCategoryEntity;
import com.example.lms.entity.User;
import com.example.lms.repository.CourseCategoryRepository;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.UserRepository;
import com.example.lms.service.CourseCategoryCatalogService;
import com.example.lms.util.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    @Autowired
    private CourseCategoryCatalogService categoryCatalogService;

    @Autowired
    private JwtUtil jwtUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CourseCategoryEntity category;
    private User instructor;

    @BeforeEach
    void setUpCourseData() {
        courseRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        category = categoryCatalogService.getSelectableCategories().getFirst();

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
                  "name": "Java Programming Course",
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
                .andExpect(jsonPath("$.data.name").value("Java Programming Course"))
                .andExpect(jsonPath("$.data.instructorId").value(instructor.getId()));

        assertThat(courseRepository.count()).isEqualTo(1);
        assertThat(courseRepository.findAll().getFirst().getName()).isEqualTo("Java Programming Course");
        assertThat(isNullable("category")).isEqualTo("YES");
        assertThat(isNullable("instructor")).isEqualTo("YES");
        assertThat(isNullable("level")).isEqualTo("YES");

        mockMvc.perform(get("/api/courses")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Java Programming Course"));
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
                  "name": "Instructor Owned Course",
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
                .andExpect(jsonPath("$.data.categoryName").value("Technical"))
                .andExpect(jsonPath("$.data.instructorId").value(instructor.getId()))
                .andExpect(jsonPath("$.data.instructorName").value("Course Instructor"));

        assertThat(courseRepository.findAll().getFirst().getInstructorId())
                .isEqualTo(instructor.getId());
    }

    @Test
    void categoryDropdownReturnsOnlyTheFourFixedValues() throws Exception {
        CourseCategoryEntity customCategory = new CourseCategoryEntity();
        customCategory.setName("Custom Category");
        customCategory.setActive(true);
        categoryRepository.save(customCategory);

        mockMvc.perform(get("/api/course-categories/active")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].id").value(category.getId()))
                .andExpect(jsonPath("$.data[0].name").value("Technical"))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[1].name").value("Soft Skills"))
                .andExpect(jsonPath("$.data[2].name").value("Compliance"))
                .andExpect(jsonPath("$.data[3].name").value("Leadership"));

        mockMvc.perform(get("/api/course-lookups/categories")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].name").value("Technical"))
                .andExpect(jsonPath("$.data[1].name").value("Soft Skills"))
                .andExpect(jsonPath("$.data[2].name").value("Compliance"))
                .andExpect(jsonPath("$.data[3].name").value("Leadership"));
    }

    @Test
    void createCourseRejectsAnActiveCategoryOutsideTheExactFixedCatalog() throws Exception {
        CourseCategoryEntity customCategory = new CourseCategoryEntity();
        customCategory.setName("technical");
        customCategory.setActive(true);
        customCategory = categoryRepository.save(customCategory);

        performCreate(customCategory.getId(), instructor.getId(), "admin@example.com", "ADMIN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Course Category must be one of: Technical, Soft Skills, Compliance, or Leadership."));
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
    void missingMandatoryFieldsReturnErrorsForTheCreateCourseForm() throws Exception {
        MockMultipartFile coursePart = new MockMultipartFile(
                "course",
                "course.json",
                MediaType.APPLICATION_JSON_VALUE,
                "{\"name\":\"   \",\"level\":\"   \"}".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/courses")
                        .file(coursePart)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "Please correct the validation errors before submitting."))
                .andExpect(jsonPath("$.data.name").value("Course Name is required."))
                .andExpect(jsonPath("$.data.categoryId").value("Course Category is required."))
                .andExpect(jsonPath("$.data.instructorId").value(
                        "Instructor selection is required."))
                .andExpect(jsonPath("$.data.level").value("Course Level is required."))
                .andExpect(jsonPath("$.data.description").doesNotExist())
                .andExpect(jsonPath("$.data.thumbnail").doesNotExist());

        assertThat(courseRepository.count()).isZero();
    }

    @Test
    void courseNameIsMandatoryAndMustContainThreeToTenStoredWords() throws Exception {
        performCreateWithName("   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.name").value("Course Name is required."));

        performCreateWithName("Java Course")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Course Name must contain at least 3 words."));

        performCreateWithName("one two three four five six seven eight nine ten eleven")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Course Name must not exceed 10 words."));

        performCreateWithName("  Modern   Java   Programming  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Modern Java Programming"));

        assertThat(courseRepository.findAll())
                .singleElement()
                .extracting(course -> course.getName())
                .isEqualTo("Modern Java Programming");
    }

    @Test
    void courseNameMustBeUniqueIgnoringCaseAndWhitespaceNormalization() throws Exception {
        performCreateWithName("Modern Java Programming")
                .andExpect(status().isCreated());

        performCreateWithName("  MODERN   JAVA   PROGRAMMING  ")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Course Name already exists."));

        assertThat(courseRepository.count()).isEqualTo(1);
    }

    @Test
    void tenWordCourseNameIsAccepted() throws Exception {
        String tenWords = "one two three four five six seven eight nine ten";

        performCreateWithName(tenWords)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value(tenWords));
    }

    @Test
    void thumbnailIsOptionalAndAcceptsJpgJpegAndPng() throws Exception {
        performCreateWithName("Optional Thumbnail Course")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.thumbnailUrl").value(
                        "/images/default-course-thumbnail.svg"));

        assertThat(courseRepository.findAll().getFirst().getThumbnailUrl())
                .isEqualTo("/images/default-course-thumbnail.svg");

        mockMvc.perform(get("/images/default-course-thumbnail.svg"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"));

        String[][] supportedFiles = {
                {"thumbnail.jpg", "image/jpeg", "Jpg Thumbnail Course"},
                {"thumbnail.JPEG", "image/jpeg", "Jpeg Thumbnail Course"},
                {"thumbnail.png", "image/png", "Png Thumbnail Course"}
        };
        for (String[] supportedFile : supportedFiles) {
            MockMultipartFile thumbnail = new MockMultipartFile(
                    "thumbnail", supportedFile[0], supportedFile[1], new byte[]{1, 2, 3});
            String response = performCreateWithThumbnail(supportedFile[2], thumbnail)
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            deleteCreatedThumbnail(response);
        }
    }

    @Test
    void thumbnailRejectsUnsupportedOrMismatchedFileTypes() throws Exception {
        MockMultipartFile unsupported = new MockMultipartFile(
                "thumbnail", "thumbnail.gif", "image/gif", new byte[]{1});
        performCreateWithThumbnail("Unsupported Thumbnail Course", unsupported)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Thumbnail must be a JPG, JPEG, or PNG file."));

        MockMultipartFile mismatched = new MockMultipartFile(
                "thumbnail", "thumbnail.jpg", "image/png", new byte[]{1});
        performCreateWithThumbnail("Mismatched Thumbnail Course", mismatched)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Thumbnail must be a JPG, JPEG, or PNG file."));

        assertThat(courseRepository.count()).isZero();
    }

    @Test
    void thumbnailUsesTwoMegabyteLimitFromTheSrsRange() throws Exception {
        MockMultipartFile maximumSize = new MockMultipartFile(
                "thumbnail", "maximum.jpg", "image/jpeg", new byte[2 * 1024 * 1024]);
        String response = performCreateWithThumbnail("Maximum Thumbnail Size", maximumSize)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        deleteCreatedThumbnail(response);

        MockMultipartFile oversized = new MockMultipartFile(
                "thumbnail", "oversized.png", "image/png", new byte[2 * 1024 * 1024 + 1]);
        performCreateWithThumbnail("Oversized Thumbnail Course", oversized)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Thumbnail must not exceed 2 MB."));

        assertThat(courseRepository.count()).isEqualTo(1);
    }

    @Test
    void unauthenticatedAndUnauthorizedLookupsAreRejected() throws Exception {
        mockMvc.perform(get("/api/course-categories/active"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/instructors/active")
                        .with(user("student@example.com").roles("STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void createCourseUsesThePlatformBearerTokenAndStandardSecurityErrors() throws Exception {
        performCreateWithAuthorization(null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication is required."));

        performCreateWithAuthorization(bearerToken(
                "learner@example.com", 999L, "LEARNER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "You are not authorized to perform this action."));

        performCreateWithAuthorization(bearerToken(
                "admin@example.com", 1L, "ADMIN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(courseRepository.count()).isEqualTo(1);
    }

    @Test
    void courseAccessIsEnforcedByTheSecurityFilterChain() throws Exception {
        String learnerToken = bearerToken("learner@example.com", 999L, "LEARNER");

        mockMvc.perform(post("/api/courses/filter-chain-probe")
                        .header(HttpHeaders.AUTHORIZATION, learnerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "You are not authorized to perform this action."));

        mockMvc.perform(get("/api/courses")
                        .header(HttpHeaders.AUTHORIZATION, learnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication is required."));
    }

    private org.springframework.test.web.servlet.ResultActions performCreate(
            Long categoryId, Long instructorId, String email, String role) throws Exception {
        String json = """
                {
                  "name": "Validation Test Course",
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

    private org.springframework.test.web.servlet.ResultActions performCreateWithName(
            String courseName
    ) throws Exception {
        String json = """
                {
                  "name": "%s",
                  "categoryId": %d,
                  "instructorId": %d,
                  "level": "BEGINNER"
                }
                """.formatted(courseName, category.getId(), instructor.getId());
        MockMultipartFile coursePart = new MockMultipartFile(
                "course", "course.json", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/courses")
                .file(coursePart)
                .with(user("admin@example.com").roles("ADMIN")));
    }

    private org.springframework.test.web.servlet.ResultActions performCreateWithThumbnail(
            String courseName, MockMultipartFile thumbnail
    ) throws Exception {
        String json = """
                {
                  "name": "%s",
                  "categoryId": %d,
                  "instructorId": %d,
                  "level": "BEGINNER"
                }
                """.formatted(courseName, category.getId(), instructor.getId());
        MockMultipartFile coursePart = new MockMultipartFile(
                "course", "course.json", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/courses")
                .file(coursePart)
                .file(thumbnail)
                .with(user("admin@example.com").roles("ADMIN")));
    }

    private org.springframework.test.web.servlet.ResultActions performCreateWithAuthorization(
            String authorization
    ) throws Exception {
        String json = """
                {
                  "name": "Bearer Authentication Course",
                  "categoryId": %d,
                  "instructorId": %d,
                  "level": "BEGINNER"
                }
                """.formatted(category.getId(), instructor.getId());
        MockMultipartFile coursePart = new MockMultipartFile(
                "course", "course.json", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
        var request = multipart("/api/courses").file(coursePart);
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mockMvc.perform(request);
    }

    private String bearerToken(String email, Long userId, String role) {
        return "Bearer " + jwtUtil.generateToken(email, userId, role);
    }

    private void deleteCreatedThumbnail(String responseBody) throws Exception {
        JsonNode response = objectMapper.readTree(responseBody);
        String thumbnailUrl = response.path("data").path("thumbnailUrl").asText();
        Path thumbnailDirectory = Path.of("uploads", "course-thumbnails")
                .toAbsolutePath()
                .normalize();
        Path storedFile = Path.of(thumbnailUrl.substring(1)).toAbsolutePath().normalize();
        assertThat(storedFile).startsWith(thumbnailDirectory);
        assertThat(Files.deleteIfExists(storedFile)).isTrue();
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
