package com.example.lms.service;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.QuestionBankDto;
import com.example.lms.entity.QuestionBankEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.QuestionBankException;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.QuestionBankRepository;
import com.example.lms.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionBankServiceTest {

    @Mock
    private QuestionBankRepository questionBankRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private UserRepository userRepository;

    @TempDir
    Path uploadDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private QuestionBankService service;
    private User owner;
    private AuthPrincipal instructor;

    @BeforeEach
    void setUp() {
        service = new QuestionBankService(
                questionBankRepository,
                courseRepository,
                userRepository,
                objectMapper,
                List.of()
        );
        ReflectionTestUtils.setField(service, "uploadDirectory", uploadDirectory.toString());
        owner = user(10L, "owner@example.com", "INSTRUCTOR");
        instructor = new AuthPrincipal(owner.getId(), owner.getEmail(), owner.getRole());
        lenient().when(questionBankRepository.saveAndFlush(any(QuestionBankEntity.class)))
                .thenAnswer(invocation -> {
                    QuestionBankEntity bank = invocation.getArgument(0);
                    if (bank.getId() == null) {
                        bank.setId(1L);
                    }
                    if (bank.getVersion() == null) {
                        bank.setVersion(0L);
                    }
                    if (bank.getCreatedAt() == null) {
                        bank.setCreatedAt(LocalDateTime.now());
                    }
                    bank.setUpdatedAt(LocalDateTime.now());
                    return bank;
                });
    }

    @Test
    void createsValidSingleAnswerAndDefaultsScore() {
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        QuestionBankDto.CreateRequest request = requestWithQuestion(
                QuestionBankDto.QuestionType.SINGLE_ANSWER,
                List.of(option("A", true), option("B", false)),
                null
        );

        QuestionBankService.CreateResult result = service.create(instructor, request, null);

        assertFalse(result.replayed());
        QuestionBankDto.QuestionData question = result.response().getQuestions().get(0);
        assertEquals(BigDecimal.ZERO, question.getScore());
        assertEquals(1, question.getOptions().stream()
                .filter(QuestionBankDto.OptionData::isCorrect).count());
    }

    @Test
    void rejectsSingleAnswerWithMultipleCorrectOptions() {
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        QuestionBankDto.CreateRequest request = requestWithQuestion(
                QuestionBankDto.QuestionType.SINGLE_ANSWER,
                List.of(option("A", true), option("B", true)),
                BigDecimal.TEN
        );

        QuestionBankException exception = assertThrows(
                QuestionBankException.class,
                () -> service.create(instructor, request, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("exactly one correct"));
        verify(questionBankRepository, never()).saveAndFlush(any());
    }

    @Test
    void acceptsMultipleAnswerWithOneOrMoreCorrectOptions() {
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        QuestionBankDto.CreateRequest request = requestWithQuestion(
                QuestionBankDto.QuestionType.MULTIPLE_ANSWER,
                List.of(option("A", true), option("B", true), option("C", false)),
                new BigDecimal("100")
        );

        QuestionBankDto.Response response = service.create(instructor, request, null).response();

        assertEquals(3, response.getQuestions().get(0).getOptions().size());
        assertEquals(new BigDecimal("100"), response.getQuestions().get(0).getScore());
    }

    @Test
    void rejectsShortTextWithChoiceOptions() {
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        QuestionBankDto.CreateRequest request = requestWithQuestion(
                QuestionBankDto.QuestionType.SHORT_TEXT,
                List.of(option("Not allowed", true)),
                BigDecimal.ZERO
        );

        QuestionBankException exception = assertThrows(
                QuestionBankException.class,
                () -> service.create(instructor, request, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("must not contain answer options"));
    }

    @Test
    void enforcesTextLimitsAndScoreBoundaries() {
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        QuestionBankDto.CreateRequest tooLong = requestWithQuestion(
                null, List.of(), BigDecimal.ZERO);
        tooLong.getQuestions().get(0).setQuestionText("x".repeat(501));
        QuestionBankException lengthError = assertThrows(
                QuestionBankException.class,
                () -> service.create(instructor, tooLong, null)
        );
        assertTrue(lengthError.getMessage().contains("500"));

        QuestionBankDto.CreateRequest scoreTooHigh = requestWithQuestion(
                QuestionBankDto.QuestionType.SHORT_TEXT,
                List.of(),
                new BigDecimal("100.01")
        );
        QuestionBankException scoreError = assertThrows(
                QuestionBankException.class,
                () -> service.create(instructor, scoreTooHigh, null)
        );
        assertTrue(scoreError.getMessage().contains("between 0 and 100"));
    }

    @Test
    void appliesRoleAndInstructorOwnershipRules() throws Exception {
        QuestionBankDto.Content content = QuestionBankDto.Content.builder().build();
        QuestionBankEntity bank = bank(5L, 0L, owner, content);
        when(questionBankRepository.findWithReferencesById(5L)).thenReturn(Optional.of(bank));

        assertThrows(
                AccessDeniedException.class,
                () -> service.get(5L, new AuthPrincipal(20L, "other@example.com", "INSTRUCTOR"))
        );
        assertThrows(
                AccessDeniedException.class,
                () -> service.get(5L, new AuthPrincipal(30L, "learner@example.com", "LEARNER"))
        );

        QuestionBankDto.Response adminResult =
                service.get(5L, new AuthPrincipal(30L, "admin@example.com", "ADMIN"));
        assertEquals(5L, adminResult.getId());
    }

    @Test
    void replaysIdempotentCreateWithoutSecondInsert() {
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        AtomicReference<QuestionBankEntity> stored = new AtomicReference<>();
        when(questionBankRepository.findByCreatedByIdAndIdempotencyKey(
                owner.getId(), "save-once"))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(questionBankRepository.saveAndFlush(any(QuestionBankEntity.class)))
                .thenAnswer(invocation -> {
                    QuestionBankEntity bank = invocation.getArgument(0);
                    bank.setId(99L);
                    bank.setVersion(0L);
                    bank.setCreatedAt(LocalDateTime.now());
                    bank.setUpdatedAt(LocalDateTime.now());
                    stored.set(bank);
                    return bank;
                });
        QuestionBankDto.CreateRequest request = QuestionBankDto.CreateRequest.builder()
                .name("Reusable Bank")
                .build();

        QuestionBankService.CreateResult first =
                service.create(instructor, request, "save-once");
        QuestionBankService.CreateResult retry =
                service.create(instructor, request, "save-once");

        assertFalse(first.replayed());
        assertTrue(retry.replayed());
        assertEquals(first.response().getId(), retry.response().getId());
        verify(questionBankRepository).saveAndFlush(any(QuestionBankEntity.class));
    }

    @Test
    void rejectsStaleVersionBeforeUpdate() throws Exception {
        QuestionBankEntity bank = bank(
                7L, 3L, owner, QuestionBankDto.Content.builder().build());
        when(questionBankRepository.findWithReferencesById(7L)).thenReturn(Optional.of(bank));
        QuestionBankDto.UpdateRequest request = QuestionBankDto.UpdateRequest.builder()
                .name("Updated Bank")
                .version(2L)
                .build();

        QuestionBankException exception = assertThrows(
                QuestionBankException.class,
                () -> service.update(7L, request, instructor)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertTrue(exception.getMessage().contains("already updated"));
        verify(questionBankRepository, never()).saveAndFlush(any());
    }

    @Test
    void copiesExistingQuestionAndOptionsAsIndependentRecords() throws Exception {
        QuestionBankDto.QuestionData sourceQuestion = validSingleQuestion(
                "source-question", "source-option-a", "source-option-b");
        QuestionBankDto.Content sourceContent = QuestionBankDto.Content.builder()
                .questions(new ArrayList<>(List.of(sourceQuestion)))
                .build();
        QuestionBankDto.Content targetContent = QuestionBankDto.Content.builder().build();
        QuestionBankEntity sourceBank = bank(1L, 0L, owner, sourceContent);
        QuestionBankEntity targetBank = bank(2L, 0L, owner, targetContent);
        when(questionBankRepository.findWithReferencesById(1L))
                .thenReturn(Optional.of(sourceBank));
        when(questionBankRepository.findWithReferencesById(2L))
                .thenReturn(Optional.of(targetBank));

        QuestionBankDto.Response response = service.addExistingQuestion(
                2L,
                QuestionBankDto.AddExistingQuestionRequest.builder()
                        .sourceBankId(1L)
                        .sourceQuestionId("source-question")
                        .version(0L)
                        .build(),
                instructor
        );

        QuestionBankDto.QuestionData copied = response.getQuestions().get(0);
        assertNotEquals(sourceQuestion.getId(), copied.getId());
        assertNotEquals(sourceQuestion.getOptions().get(0).getId(),
                copied.getOptions().get(0).getId());
        copied.getOptions().get(0).setOptionText("Changed copy");
        assertEquals("A", sourceQuestion.getOptions().get(0).getOptionText());
    }

    @Test
    void reordersSectionsAndQuestionsUsingCompleteOrders() throws Exception {
        QuestionBankDto.SectionData first = section("s1", "First", 1);
        QuestionBankDto.SectionData second = section("s2", "Second", 2);
        QuestionBankDto.QuestionData q1 =
                validSingleQuestion("q1", "q1a", "q1b");
        QuestionBankDto.QuestionData q2 =
                validSingleQuestion("q2", "q2a", "q2b");
        q1.setPosition(1);
        q2.setPosition(2);
        QuestionBankDto.Content content = QuestionBankDto.Content.builder()
                .sections(new ArrayList<>(List.of(first, second)))
                .questions(new ArrayList<>(List.of(q1, q2)))
                .build();
        QuestionBankEntity bank = bank(8L, 0L, owner, content);
        when(questionBankRepository.findWithReferencesById(8L)).thenReturn(Optional.of(bank));

        QuestionBankDto.Response sections = service.reorderSections(
                8L,
                QuestionBankDto.ReorderRequest.builder()
                        .ids(List.of("s2", "s1"))
                        .version(0L)
                        .build(),
                instructor
        );
        assertEquals(List.of("s2", "s1"),
                sections.getSections().stream().map(QuestionBankDto.SectionData::getId).toList());

        QuestionBankDto.Response questions = service.reorderQuestions(
                8L,
                QuestionBankDto.QuestionReorderRequest.builder()
                        .questionIds(List.of("q2", "q1"))
                        .version(0L)
                        .build(),
                instructor
        );
        assertEquals(List.of("q2", "q1"),
                questions.getQuestions().stream().map(QuestionBankDto.QuestionData::getId).toList());
    }

    @Test
    void storesValidUploadOutsideStaticResourcesAndPersistsMetadata() throws Exception {
        QuestionBankEntity bank = bank(
                11L, 0L, owner, QuestionBankDto.Content.builder().build());
        when(questionBankRepository.findWithReferencesById(11L)).thenReturn(Optional.of(bank));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../questions.csv",
                "text/csv",
                "question,answer\nOne,Two".getBytes(StandardCharsets.UTF_8)
        );

        QuestionBankDto.Response response =
                service.importQuestions(11L, 0L, file, instructor);

        assertEquals(1, response.getImports().size());
        QuestionBankDto.ImportData metadata = response.getImports().get(0);
        assertEquals("questions.csv", metadata.getSanitizedFilename());
        assertEquals(QuestionBankDto.ImportStatus.STORED, metadata.getStatus());
        assertTrue(Files.exists(uploadDirectory.resolve(metadata.getStoredFilename())));
    }

    @Test
    void rejectsUnsupportedAndOversizedUploads() throws Exception {
        QuestionBankEntity bank = bank(
                12L, 0L, owner, QuestionBankDto.Content.builder().build());
        when(questionBankRepository.findWithReferencesById(12L)).thenReturn(Optional.of(bank));
        MockMultipartFile unsupported = new MockMultipartFile(
                "file", "questions.exe", "application/octet-stream", new byte[]{1});

        QuestionBankException typeError = assertThrows(
                QuestionBankException.class,
                () -> service.importQuestions(12L, 0L, unsupported, instructor)
        );
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, typeError.getStatus());

        MultipartFile oversized = org.mockito.Mockito.mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(100L * 1024L * 1024L + 1L);
        QuestionBankException sizeError = assertThrows(
                QuestionBankException.class,
                () -> service.importQuestions(12L, 0L, oversized, instructor)
        );
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, sizeError.getStatus());
        assertEquals(0, Files.list(uploadDirectory).count());
    }

    @Test
    void exportsCsvWithSafeEscaping() throws Exception {
        QuestionBankDto.QuestionData question =
                validSingleQuestion("q-csv", "csv-a", "csv-b");
        question.setQuestionText("He said \"yes\",\nthen continued");
        question.getOptions().get(0).setOptionText("A, \"quoted\"");
        QuestionBankDto.Content content = QuestionBankDto.Content.builder()
                .questions(new ArrayList<>(List.of(question)))
                .build();
        QuestionBankEntity bank = bank(13L, 0L, owner, content);
        bank.setName("CSV Safety");
        when(questionBankRepository.findWithReferencesById(13L)).thenReturn(Optional.of(bank));

        QuestionBankService.CsvExport export =
                service.exportQuestions(13L, instructor);
        String csv = new String(export.bytes(), StandardCharsets.UTF_8);

        assertEquals("csv-safety-questions.csv", export.filename());
        assertTrue(csv.contains("\"He said \"\"yes\"\",\nthen continued\""));
        assertTrue(csv.contains("\"A, \"\"quoted\"\"\""));
    }

    @Test
    void returnsNotFoundForMissingBankQuestionAndSection() throws Exception {
        when(questionBankRepository.findWithReferencesById(404L)).thenReturn(Optional.empty());
        QuestionBankException missingBank = assertThrows(
                QuestionBankException.class,
                () -> service.get(404L, instructor)
        );
        assertEquals(HttpStatus.NOT_FOUND, missingBank.getStatus());

        QuestionBankEntity bank = bank(
                14L, 0L, owner, QuestionBankDto.Content.builder().build());
        when(questionBankRepository.findWithReferencesById(14L)).thenReturn(Optional.of(bank));
        QuestionBankException missingQuestion = assertThrows(
                QuestionBankException.class,
                () -> service.deleteQuestion(14L, "missing", 0L, instructor)
        );
        assertEquals(HttpStatus.NOT_FOUND, missingQuestion.getStatus());

        QuestionBankException missingSection = assertThrows(
                QuestionBankException.class,
                () -> service.updateSection(
                        14L,
                        "missing",
                        QuestionBankDto.SectionRequest.builder()
                                .name("Updated")
                                .version(0L)
                                .build(),
                        instructor
                )
        );
        assertEquals(HttpStatus.NOT_FOUND, missingSection.getStatus());
    }

    private QuestionBankDto.CreateRequest requestWithQuestion(
            QuestionBankDto.QuestionType type,
            List<QuestionBankDto.OptionInput> options,
            BigDecimal score
    ) {
        QuestionBankDto.CreateQuestionInput question =
                QuestionBankDto.CreateQuestionInput.builder()
                        .questionType(type)
                        .questionText("")
                        .score(score)
                        .feedback("")
                        .textAnswer(type == QuestionBankDto.QuestionType.SHORT_TEXT ? "answer" : "")
                        .options(options)
                        .build();
        return QuestionBankDto.CreateRequest.builder()
                .name("Assessment Bank")
                .questions(new ArrayList<>(List.of(question)))
                .build();
    }

    private QuestionBankDto.OptionInput option(String text, boolean correct) {
        return QuestionBankDto.OptionInput.builder()
                .optionText(text)
                .correct(correct)
                .build();
    }

    private QuestionBankDto.QuestionData validSingleQuestion(
            String questionId,
            String firstOptionId,
            String secondOptionId
    ) {
        return QuestionBankDto.QuestionData.builder()
                .id(questionId)
                .questionType(QuestionBankDto.QuestionType.SINGLE_ANSWER)
                .questionText("Question")
                .score(BigDecimal.ZERO)
                .textAnswer("")
                .feedback("")
                .position(1)
                .options(new ArrayList<>(List.of(
                        QuestionBankDto.OptionData.builder()
                                .id(firstOptionId)
                                .optionText("A")
                                .correct(true)
                                .optionPoints(BigDecimal.ZERO)
                                .position(1)
                                .build(),
                        QuestionBankDto.OptionData.builder()
                                .id(secondOptionId)
                                .optionText("B")
                                .correct(false)
                                .optionPoints(BigDecimal.ZERO)
                                .position(2)
                                .build()
                )))
                .build();
    }

    private QuestionBankDto.SectionData section(
            String id,
            String name,
            int position
    ) {
        return QuestionBankDto.SectionData.builder()
                .id(id)
                .name(name)
                .position(position)
                .build();
    }

    private QuestionBankEntity bank(
            Long id,
            Long version,
            User creator,
            QuestionBankDto.Content content
    ) throws Exception {
        QuestionBankEntity bank = new QuestionBankEntity();
        bank.setId(id);
        bank.setName("Question Bank " + id);
        bank.setCreatedBy(creator);
        bank.setVersion(version);
        bank.setCreatedAt(LocalDateTime.now());
        bank.setUpdatedAt(LocalDateTime.now());
        bank.setContentJson(objectMapper.writeValueAsString(content));
        return bank;
    }

    private User user(Long id, String email, String role) {
        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .email(email)
                .countryCode("+1")
                .phoneNumber("1234567890")
                .password("not-used")
                .role(role)
                .acceptedTerms(true)
                .active(true)
                .build();
        user.setId(id);
        return user;
    }
}
