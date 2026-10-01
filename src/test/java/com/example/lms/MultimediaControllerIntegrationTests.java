package com.example.lms;

import com.example.lms.dto.MultimediaDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.CourseEnrollmentEntity;
import com.example.lms.entity.MultimediaEntity;
import com.example.lms.entity.MultimediaResourceType;
import com.example.lms.entity.User;
import com.example.lms.repository.CourseEnrollmentRepository;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.MultimediaRepository;
import com.example.lms.repository.UserRepository;
import com.example.lms.service.MultimediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MultimediaControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MultimediaRepository multimediaRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseEnrollmentRepository enrollmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MultimediaService multimediaService;

    private CourseEntity course;

    @BeforeEach
    void setUp() {
        multimediaRepository.deleteAll();
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
        userRepository.deleteAll();
        course = courseRepository.save(CourseEntity.builder()
                .name("Multimedia Test Course")
                .categoryId(1L)
                .instructorId(2L)
                .level("BEGINNER")
                .build());
    }

    @Test
    void resourceTypesAreAvailableForSelection() throws Exception {
        mockMvc.perform(get("/api/multimedia/resource-types")
                        .with(user("instructor@example.com").roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("VIDEO"))
                .andExpect(jsonPath("$.data[1]").value("AUDIO"))
                .andExpect(jsonPath("$.data[2]").value("IMAGE"))
                .andExpect(jsonPath("$.data[3]").value("DOCUMENT"))
                .andExpect(jsonPath("$.data[4]").value("ARCHIVE"))
                .andExpect(jsonPath("$.data[5]").value("OTHER"));
    }

    @Test
    void effectiveAdministratorUploadConfigurationIsAvailable() throws Exception {
        mockMvc.perform(get("/api/multimedia/upload-configuration")
                        .with(user("instructor@example.com").roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supportedFileTypes[0]").value("MP4"))
                .andExpect(jsonPath("$.data.supportedFileTypes[1]").value("PDF"))
                .andExpect(jsonPath("$.data.supportedFileTypes[2]").value("PNG"))
                .andExpect(jsonPath("$.data.maximumUploadSizeBytes").value(1_048_576))
                .andExpect(jsonPath("$.data.maximumUploadSizeMegabytes").value(1));
    }

    @Test
    void selectedFileCanBePreviewedBeforeSaveWithoutCreatingAResource() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.mp4", "video/mp4", new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart("/api/multimedia/preview")
                        .file(file)
                        .with(user("instructor@example.com").roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("lesson.mp4"))
                .andExpect(jsonPath("$.data.contentType").value("video/mp4"))
                .andExpect(jsonPath("$.data.size").value(4))
                .andExpect(jsonPath("$.data.inlinePreviewSupported").value(true));

        assertThat(multimediaRepository.count()).isZero();
    }

    @Test
    void preAuthorizeDenialReturnsStandardApiResponse() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/multimedia/preview")
                        .file(file)
                        .with(user("learner@example.com").roles("LEARNER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(
                        "You are not authorized to perform this action."));

        assertThat(multimediaRepository.count()).isZero();
    }

    @Test
    void invalidFileCannotBePreviewed() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.exe", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[]{1});

        mockMvc.perform(multipart("/api/multimedia/preview")
                        .file(file)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Unsupported file format. Allowed formats: MP4, PDF, PNG."));

        assertThat(multimediaRepository.count()).isZero();
    }

    @Test
    void administratorConfiguredMaximumUploadSizeIsEnforced() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[1_048_577]);

        mockMvc.perform(multipart("/api/multimedia/preview")
                        .file(file)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File size must not exceed 1 MB."));

        assertThat(multimediaRepository.count()).isZero();
    }

    @Test
    void selectedResourceTypeIsRequiredPersistedAndReturned() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.mp4", "video/mp4", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/multimedia/upload")
                        .file(file)
                        .param("resourceName", "  Lesson video  ")
                        .param("resourceDescription", "An introductory lesson")
                        .param("resourceType", "VIDEO")
                        .param("courseId", course.getId().toString())
                        .param("published", "true")
                        .with(user("instructor@example.com").roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resourceName").value("Lesson video"))
                .andExpect(jsonPath("$.data.resourceType").value("VIDEO"));

        assertThat(multimediaRepository.findAll())
                .singleElement()
                .satisfies(resource -> {
                    assertThat(resource.getResourceName()).isEqualTo("Lesson video");
                    assertThat(resource.getResourceType()).isEqualTo(MultimediaResourceType.VIDEO);
                });

        mockMvc.perform(get("/api/multimedia/{courseId}", course.getId())
                        .with(user("learner@example.com").roles("LEARNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].resourceType").value("VIDEO"));
    }

    @Test
    void concurrentDuplicateSaveClicksReturnOnlyTheFirstResource() throws Exception {
        UUID submissionId = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MultimediaDto.ResourceResponse> first = executor.submit(() -> {
                start.await();
                return multimediaService.upload(uploadRequest(
                        submissionId, "first.pdf", new MockMultipartFile(
                                "file", "first.pdf", MediaType.APPLICATION_PDF_VALUE,
                                new byte[]{1, 2, 3})), "admin@example.com");
            });
            Future<MultimediaDto.ResourceResponse> duplicate = executor.submit(() -> {
                start.await();
                return multimediaService.upload(uploadRequest(
                        submissionId, "duplicate.pdf", new MockMultipartFile(
                                "file", "duplicate.pdf", MediaType.APPLICATION_PDF_VALUE,
                                new byte[]{4, 5, 6})), "admin@example.com");
            });

            start.countDown();
            MultimediaDto.ResourceResponse firstResponse = first.get(10, TimeUnit.SECONDS);
            MultimediaDto.ResourceResponse duplicateResponse = duplicate.get(10, TimeUnit.SECONDS);

            assertThat(duplicateResponse.getId()).isEqualTo(firstResponse.getId());
            assertThat(multimediaRepository.count()).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void differentSubmissionsCanUploadConcurrently() throws Exception {
        CountDownLatch streamsOpened = new CountDownLatch(2);
        CountDownLatch allowFileCopy = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MultimediaDto.ResourceResponse> first = executor.submit(() ->
                    multimediaService.upload(uploadRequest(
                            UUID.randomUUID(), "admin-one.pdf",
                            blockingFile("admin-one.pdf", streamsOpened, allowFileCopy)),
                            "admin-one@example.com"));
            Future<MultimediaDto.ResourceResponse> second = executor.submit(() ->
                    multimediaService.upload(uploadRequest(
                            UUID.randomUUID(), "instructor-two.pdf",
                            blockingFile("instructor-two.pdf", streamsOpened, allowFileCopy)),
                            "instructor-two@example.com"));

            assertThat(streamsOpened.await(10, TimeUnit.SECONDS))
                    .as("different submission IDs should both enter file copy concurrently")
                    .isTrue();
            allowFileCopy.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS).getId())
                    .isNotEqualTo(second.get(10, TimeUnit.SECONDS).getId());
            assertThat(multimediaRepository.count()).isEqualTo(2);
        } finally {
            allowFileCopy.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void assignedLearnerCanViewAndDownloadUnpublishedCourseResource() throws Exception {
        User learner = createLearner("assigned-learner@example.com");
        enrollmentRepository.save(CourseEnrollmentEntity.builder()
                .courseId(course.getId())
                .userId(learner.getId())
                .build());
        MultimediaEntity resource = uploadResource(false);

        mockMvc.perform(get("/api/multimedia/{courseId}", course.getId())
                        .with(user(learner.getEmail()).roles("LEARNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(resource.getId().toString()));

        mockMvc.perform(get("/api/multimedia/files/{id}", resource.getId())
                        .with(user(learner.getEmail()).roles("LEARNER")))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .containsExactly(1, 2, 3));
    }

    @Test
    void unassignedOrExpiredLearnerCannotViewOrDownloadUnpublishedResource() throws Exception {
        User learner = createLearner("unassigned-learner@example.com");
        MultimediaEntity resource = uploadResource(false);

        assertUnpublishedResourceIsForbidden(learner, resource);

        enrollmentRepository.save(CourseEnrollmentEntity.builder()
                .courseId(course.getId())
                .userId(learner.getId())
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build());

        assertUnpublishedResourceIsForbidden(learner, resource);
    }

    @Test
    void uploadWithoutResourceTypeIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/multimedia/upload")
                        .file(file)
                        .param("resourceName", "Lesson notes")
                        .param("courseId", course.getId().toString())
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.resourceType").value("Resource type is required."));

        assertThat(multimediaRepository.count()).isZero();
    }

    @Test
    void resourceNameLengthReturnsDistinctMinimumAndMaximumMessages() throws Exception {
        performUploadWithResourceName("  abc  ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.resourceName").value("Minimum 4 characters."));

        performUploadWithResourceName("  " + "x".repeat(101) + "  ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.resourceName").value("Maximum 100 characters."));

        assertThat(multimediaRepository.count()).isZero();
    }

    @Test
    void blankResourceNameReturnsRequiredMessage() throws Exception {
        performUploadWithResourceName("     ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.resourceName").value("Resource name is required."));

        assertThat(multimediaRepository.count()).isZero();
    }

    @Test
    void multimediaEntityIsBoundToResourceNameValidation() {
        MultimediaEntity multimedia = MultimediaEntity.builder()
                .resourceName("  abc  ")
                .resourceType(MultimediaResourceType.DOCUMENT)
                .filePath("test/lesson.pdf")
                .originalFileName("lesson.pdf")
                .contentType(MediaType.APPLICATION_PDF_VALUE)
                .fileSize(3)
                .course(course)
                .uploadedBy("admin@example.com")
                .build();

        assertThatThrownBy(() -> multimediaRepository.saveAndFlush(multimedia))
                .isInstanceOf(jakarta.validation.ConstraintViolationException.class)
                .hasMessageContaining("Minimum 4 characters.");
    }

    private org.springframework.test.web.servlet.ResultActions performUploadWithResourceName(
            String resourceName
    ) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lesson.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{1, 2, 3});
        return mockMvc.perform(multipart("/api/multimedia/upload")
                .file(file)
                .param("resourceName", resourceName)
                .param("resourceType", "DOCUMENT")
                .param("courseId", course.getId().toString())
                .with(user("admin@example.com").roles("ADMIN")));
    }

    private User createLearner(String email) {
        return userRepository.save(User.builder()
                .firstName("Multimedia")
                .lastName("Learner")
                .email(email)
                .countryCode("+91")
                .phoneNumber("9000000000")
                .password("test-password")
                .role("LEARNER")
                .acceptedTerms(true)
                .active(true)
                .build());
    }

    private MultimediaDto.UploadRequest uploadRequest(
            UUID clientRequestId,
            String resourceName,
            MockMultipartFile file
    ) {
        MultimediaDto.UploadRequest request = new MultimediaDto.UploadRequest();
        request.setResourceName(resourceName);
        request.setResourceType(MultimediaResourceType.DOCUMENT);
        request.setCourseId(course.getId());
        request.setFile(file);
        request.setClientRequestId(clientRequestId);
        return request;
    }

    private MockMultipartFile blockingFile(
            String fileName,
            CountDownLatch streamsOpened,
            CountDownLatch allowFileCopy
    ) {
        return new MockMultipartFile(
                "file", fileName, MediaType.APPLICATION_PDF_VALUE, new byte[]{1, 2, 3}) {
            @Override
            public InputStream getInputStream() throws IOException {
                streamsOpened.countDown();
                try {
                    if (!allowFileCopy.await(10, TimeUnit.SECONDS)) {
                        throw new IOException("Timed out waiting to copy the concurrent upload.");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Concurrent upload was interrupted.", exception);
                }
                return super.getInputStream();
            }
        };
    }

    private MultimediaEntity uploadResource(boolean published) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "assigned.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/multimedia/upload")
                        .file(file)
                        .param("resourceName", "Assigned lesson")
                        .param("resourceType", "DOCUMENT")
                        .param("courseId", course.getId().toString())
                        .param("published", Boolean.toString(published))
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
        return multimediaRepository.findAll().getFirst();
    }

    private void assertUnpublishedResourceIsForbidden(
            User learner,
            MultimediaEntity resource
    ) throws Exception {
        mockMvc.perform(get("/api/multimedia/{courseId}", course.getId())
                        .with(user(learner.getEmail()).roles("LEARNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/multimedia/files/{id}", resource.getId())
                        .with(user(learner.getEmail()).roles("LEARNER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "This resource is not published or assigned to this learner."));
    }
}
