package com.example.lms;

import com.example.lms.entity.*;
import com.example.lms.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EbookControllerIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired EbookRepository ebookRepository;
    @Autowired EbookMediaRepository mediaRepository;
    @Autowired CourseRepository courseRepository;
    private CourseEntity course;

    @BeforeEach
    void setUp() {
        mediaRepository.deleteAll();
        ebookRepository.deleteAll();
        courseRepository.deleteAll();
        course = courseRepository.save(CourseEntity.builder()
                .name("Ebook Test Course").categoryId(1L).instructorId(2L).level("BEGINNER").build());
    }

    @Test
    void adminCanSaveDraftAndDuplicateClickIsIdempotent() throws Exception {
        UUID requestId = UUID.randomUUID();
        String body = createBody(requestId, "Valid Ebook", "<p>Required content</p>");
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/ebooks").with(user("admin@example.com").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"))
                    .andExpect(jsonPath("$.data.title").value("Valid Ebook"));
        }
        assertThat(ebookRepository.count()).isEqualTo(1);
    }

    @Test
    void mandatoryFieldsAndLimitsAreValidated() throws Exception {
        String body = """
                {"title":"abc","description":"%s","content":" ","courseId":%d}
                """.formatted("x".repeat(501), course.getId());
        mockMvc.perform(post("/api/ebooks").with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.title").value("Ebook Title must be between 4 and 100 characters."))
                .andExpect(jsonPath("$.data.description").value("Description exceeds maximum length."))
                .andExpect(jsonPath("$.data.content").value("Ebook Content is required."));
    }

    @Test
    void learnerCannotCreateOrReadDraftButCanReadPublishedEbook() throws Exception {
        mockMvc.perform(post("/api/ebooks").with(user("learner@example.com").roles("LEARNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody(null, "Learner Ebook", "content")))
                .andExpect(status().isForbidden());

        EbookEntity ebook = createDraft();
        mockMvc.perform(get("/api/ebooks/{id}", ebook.getId()).with(user("learner@example.com").roles("LEARNER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This ebook is not published."));

        mockMvc.perform(post("/api/ebooks/{id}/publish", ebook.getId()).with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + ebook.getVersion() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/ebooks/{id}", ebook.getId()).with(user("learner@example.com").roles("LEARNER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("Draft Ebook"));
    }

    @Test
    void staleConcurrentUpdateIsRejectedAndPublishIsIdempotent() throws Exception {
        EbookEntity ebook = createDraft();
        long originalVersion = ebook.getVersion();
        String update = """
                {"title":"Updated Ebook","description":"Updated","content":"Updated content","version":%d}
                """.formatted(originalVersion);
        mockMvc.perform(put("/api/ebooks/{id}", ebook.getId()).with(user("instructor@example.com").roles("INSTRUCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/ebooks/{id}", ebook.getId()).with(user("admin@example.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This ebook was updated by another user. Refresh to load the latest version."));

        EbookEntity latest = ebookRepository.findById(ebook.getId()).orElseThrow();
        String publish = "{\"version\":" + latest.getVersion() + "}";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/ebooks/{id}/publish", ebook.getId()).with(user("admin@example.com").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(publish))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        }
    }

    @Test
    void mediaTypeIsValidatedAndValidImageCanBeUploaded() throws Exception {
        EbookEntity ebook = createDraft();
        MockMultipartFile invalid = new MockMultipartFile("file", "notes.txt", "text/plain", "bad".getBytes());
        mockMvc.perform(multipart("/api/ebooks/{id}/media", ebook.getId()).file(invalid)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid file format."));

        MockMultipartFile image = new MockMultipartFile("file", "diagram.png", "image/png", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/ebooks/{id}/media", ebook.getId()).file(image)
                        .with(user("instructor@example.com").roles("INSTRUCTOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fileName").value("diagram.png"));
        assertThat(mediaRepository.count()).isEqualTo(1);
    }

    private EbookEntity createDraft() {
        return ebookRepository.saveAndFlush(EbookEntity.builder().title("Draft Ebook").description("Description")
                .content("Required content").status(EbookStatus.DRAFT).course(course).createdBy("admin@example.com").build());
    }

    private String createBody(UUID requestId, String title, String content) {
        String request = requestId == null ? "null" : "\"" + requestId + "\"";
        return """
                {"title":"%s","description":"Description","content":"%s","courseId":%d,"clientRequestId":%s}
                """.formatted(title, content, course.getId(), request);
    }
}
