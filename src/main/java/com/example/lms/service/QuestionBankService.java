package com.example.lms.service;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.QuestionBankDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.QuestionBankEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.QuestionBankException;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.QuestionBankRepository;
import com.example.lms.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionBankService {

    private static final long MAX_FILE_SIZE_BYTES = 100L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "doc", "docx", "csv", "xls", "xlsx");
    private static final Set<String> MANAGER_ROLES =
            Set.of("ADMIN", "SUPER_ADMIN", "INSTRUCTOR");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final QuestionBankRepository questionBankRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final List<ImportParser> importParsers;

    @Value("${app.question-bank.upload-directory:./data/question-imports}")
    private String uploadDirectory;

    public interface ImportParser {
        boolean supports(String extension);
        String name();
        void parse(Path storedFile) throws IOException;
    }

    public record CreateResult(QuestionBankDto.Response response, boolean replayed) {
    }

    public record CsvExport(byte[] bytes, String filename) {
    }

    @Transactional
    public CreateResult create(
            AuthPrincipal principal,
            QuestionBankDto.CreateRequest request,
            String rawIdempotencyKey
    ) {
        requireManager(principal);
        String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
        String requestHash = hashRequest(request);

        if (idempotencyKey != null) {
            QuestionBankEntity existing = questionBankRepository
                    .findByCreatedByIdAndIdempotencyKey(principal.getId(), idempotencyKey)
                    .orElse(null);
            if (existing != null) {
                if (!Objects.equals(existing.getRequestHash(), requestHash)) {
                    throw conflict("This Idempotency-Key was already used for a different request.");
                }
                return new CreateResult(toResponse(existing, readContent(existing)), true);
            }
        }

        User creator = userRepository.findById(principal.getId())
                .orElseThrow(() -> new QuestionBankException(
                        "Authenticated user no longer exists.", HttpStatus.UNAUTHORIZED));
        QuestionBankDto.Content content = buildInitialContent(request);
        QuestionBankEntity bank = new QuestionBankEntity();
        bank.setName(normalizeBankName(request.getName()));
        bank.setDescription(normalizeDescription(request.getDescription()));
        bank.setCourse(resolveCourse(request.getCourseId()));
        bank.setCreatedBy(creator);
        bank.setContentJson(writeContent(content));
        bank.setIdempotencyKey(idempotencyKey);
        bank.setRequestHash(idempotencyKey == null ? null : requestHash);

        try {
            bank = questionBankRepository.saveAndFlush(bank);
        } catch (DataIntegrityViolationException ex) {
            throw conflict("A Question Bank create request with this Idempotency-Key is already being processed.");
        }
        return new CreateResult(toResponse(bank, content), false);
    }

    @Transactional(readOnly = true)
    public QuestionBankDto.PageResponse list(
            AuthPrincipal principal,
            String rawSearch,
            int page,
            int size
    ) {
        requireManager(principal);
        if (page < 0) {
            throw badRequest("Page must be zero or greater.");
        }
        if (size < 1 || size > 100) {
            throw badRequest("Page size must be between 1 and 100.");
        }
        String search = normalizeSearch(rawSearch);
        PageRequest pageable = PageRequest.of(
                page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id"));
        Page<QuestionBankEntity> result = isAdmin(principal)
                ? questionBankRepository.searchAll(search, pageable)
                : questionBankRepository.searchByCreator(principal.getId(), search, pageable);
        List<QuestionBankDto.SummaryResponse> summaries = result.getContent().stream()
                .map(bank -> toSummary(bank, readContent(bank)))
                .toList();
        return QuestionBankDto.PageResponse.builder()
                .content(summaries)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .last(result.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public QuestionBankDto.Response get(Long bankId, AuthPrincipal principal) {
        QuestionBankEntity bank = getBank(bankId);
        requireAccess(bank, principal);
        return toResponse(bank, readContent(bank));
    }

    @Transactional
    public QuestionBankDto.Response update(
            Long bankId,
            QuestionBankDto.UpdateRequest request,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = getBank(bankId);
        requireAccess(bank, principal);
        checkVersion(bank, request.getVersion());
        bank.setName(normalizeBankName(request.getName()));
        bank.setDescription(normalizeDescription(request.getDescription()));
        bank.setCourse(resolveCourse(request.getCourseId()));
        return persist(bank, readContent(bank));
    }

    @Transactional
    public void delete(Long bankId, Long expectedVersion, AuthPrincipal principal) {
        QuestionBankEntity bank = getBank(bankId);
        requireAccess(bank, principal);
        checkVersion(bank, expectedVersion);
        List<String> storedFiles = readContent(bank).getImports().stream()
                .map(QuestionBankDto.ImportData::getStoredFilename)
                .filter(Objects::nonNull)
                .toList();
        questionBankRepository.delete(bank);
        questionBankRepository.flush();
        afterCommit(() -> storedFiles.forEach(this::deleteStoredFile));
    }

    @Transactional
    public QuestionBankDto.Response addSection(
            Long bankId,
            QuestionBankDto.SectionRequest request,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, request.getVersion(), principal);
        QuestionBankDto.Content content = readContent(bank);
        String name = normalizeSectionName(request.getName());
        ensureUniqueSectionName(content, name, null);
        content.getSections().add(QuestionBankDto.SectionData.builder()
                .id(newId())
                .name(name)
                .position(content.getSections().size() + 1)
                .build());
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response updateSection(
            Long bankId,
            String sectionId,
            QuestionBankDto.SectionRequest request,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, request.getVersion(), principal);
        QuestionBankDto.Content content = readContent(bank);
        QuestionBankDto.SectionData section = findSection(content, sectionId);
        String name = normalizeSectionName(request.getName());
        ensureUniqueSectionName(content, name, sectionId);
        section.setName(name);
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response deleteSection(
            Long bankId,
            String sectionId,
            Long expectedVersion,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, expectedVersion, principal);
        QuestionBankDto.Content content = readContent(bank);
        QuestionBankDto.SectionData section = findSection(content, sectionId);
        content.getSections().remove(section);
        content.getQuestions().stream()
                .filter(question -> Objects.equals(question.getSectionId(), sectionId))
                .forEach(question -> question.setSectionId(null));
        normalizePositions(content);
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response duplicateSection(
            Long bankId,
            String sectionId,
            Long expectedVersion,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, expectedVersion, principal);
        QuestionBankDto.Content content = readContent(bank);
        QuestionBankDto.SectionData source = findSection(content, sectionId);
        String copiedSectionId = newId();
        content.getSections().add(QuestionBankDto.SectionData.builder()
                .id(copiedSectionId)
                .name(uniqueSectionName(content, source.getName() + " Copy"))
                .position(content.getSections().size() + 1)
                .build());
        List<QuestionBankDto.QuestionData> copies = content.getQuestions().stream()
                .filter(question -> Objects.equals(question.getSectionId(), sectionId))
                .map(question -> copyQuestion(question, copiedSectionId))
                .toList();
        content.getQuestions().addAll(copies);
        normalizePositions(content);
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response moveSection(
            Long bankId,
            String sectionId,
            int direction,
            Long expectedVersion,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, expectedVersion, principal);
        QuestionBankDto.Content content = readContent(bank);
        int currentIndex = indexOfSection(content.getSections(), sectionId);
        int targetIndex = currentIndex + direction;
        if (targetIndex < 0 || targetIndex >= content.getSections().size()) {
            return toResponse(bank, content);
        }
        java.util.Collections.swap(content.getSections(), currentIndex, targetIndex);
        normalizePositions(content);
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response copySection(
            Long sourceBankId,
            String sectionId,
            QuestionBankDto.CopyRequest request,
            AuthPrincipal principal
    ) {
        QuestionBankEntity sourceBank = getBank(sourceBankId);
        requireAccess(sourceBank, principal);
        QuestionBankDto.Content sourceContent = readContent(sourceBank);
        QuestionBankDto.SectionData source = findSection(sourceContent, sectionId);

        QuestionBankEntity targetBank = mutableBank(
                request.getTargetBankId(), request.getTargetVersion(), principal);
        QuestionBankDto.Content targetContent = readContent(targetBank);
        String copiedSectionId = newId();
        targetContent.getSections().add(QuestionBankDto.SectionData.builder()
                .id(copiedSectionId)
                .name(uniqueSectionName(targetContent, source.getName()))
                .position(targetContent.getSections().size() + 1)
                .build());
        sourceContent.getQuestions().stream()
                .filter(question -> Objects.equals(question.getSectionId(), sectionId))
                .map(question -> copyQuestion(question, copiedSectionId))
                .forEach(targetContent.getQuestions()::add);
        normalizePositions(targetContent);
        return persist(targetBank, targetContent);
    }

    @Transactional
    public QuestionBankDto.Response reorderSections(
            Long bankId,
            QuestionBankDto.ReorderRequest request,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, request.getVersion(), principal);
        QuestionBankDto.Content content = readContent(bank);
        validateCompleteOrder(
                request.getIds(),
                content.getSections().stream().map(QuestionBankDto.SectionData::getId).toList(),
                "section"
        );
        Map<String, QuestionBankDto.SectionData> byId = content.getSections().stream()
                .collect(Collectors.toMap(QuestionBankDto.SectionData::getId, Function.identity()));
        List<QuestionBankDto.SectionData> ordered = new ArrayList<>();
        for (int index = 0; index < request.getIds().size(); index++) {
            QuestionBankDto.SectionData section = byId.get(request.getIds().get(index));
            section.setPosition(index + 1);
            ordered.add(section);
        }
        content.setSections(ordered);
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response addQuestion(
            Long bankId,
            QuestionBankDto.QuestionRequest request,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, request.getVersion(), principal);
        QuestionBankDto.Content content = readContent(bank);
        requireSectionIfPresent(content, request.getSectionId());
        QuestionBankDto.QuestionData question = fromQuestionRequest(request, null);
        question.setId(newId());
        question.setPosition(nextQuestionPosition(content, question.getSectionId()));
        validateQuestion(question);
        content.getQuestions().add(question);
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response updateQuestion(
            Long bankId,
            String questionId,
            QuestionBankDto.QuestionRequest request,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, request.getVersion(), principal);
        QuestionBankDto.Content content = readContent(bank);
        requireSectionIfPresent(content, request.getSectionId());
        QuestionBankDto.QuestionData existing = findQuestion(content, questionId);
        String priorSectionId = existing.getSectionId();
        Integer priorPosition = existing.getPosition();
        QuestionBankDto.QuestionData updated = fromQuestionRequest(request, existing);
        updated.setId(existing.getId());
        updated.setPosition(Objects.equals(priorSectionId, updated.getSectionId())
                ? priorPosition
                : nextQuestionPosition(content, updated.getSectionId()));
        validateQuestion(updated);
        content.getQuestions().set(content.getQuestions().indexOf(existing), updated);
        normalizePositions(content);
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response deleteQuestion(
            Long bankId,
            String questionId,
            Long expectedVersion,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, expectedVersion, principal);
        QuestionBankDto.Content content = readContent(bank);
        content.getQuestions().remove(findQuestion(content, questionId));
        normalizePositions(content);
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response duplicateQuestion(
            Long bankId,
            String questionId,
            Long expectedVersion,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, expectedVersion, principal);
        QuestionBankDto.Content content = readContent(bank);
        QuestionBankDto.QuestionData source = findQuestion(content, questionId);
        QuestionBankDto.QuestionData copy = copyQuestion(source, source.getSectionId());
        copy.setPosition(nextQuestionPosition(content, copy.getSectionId()));
        content.getQuestions().add(copy);
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response addExistingQuestion(
            Long targetBankId,
            QuestionBankDto.AddExistingQuestionRequest request,
            AuthPrincipal principal
    ) {
        QuestionBankEntity sourceBank = getBank(request.getSourceBankId());
        requireAccess(sourceBank, principal);
        QuestionBankDto.QuestionData source =
                findQuestion(readContent(sourceBank), request.getSourceQuestionId());

        QuestionBankEntity targetBank = mutableBank(targetBankId, request.getVersion(), principal);
        QuestionBankDto.Content targetContent = readContent(targetBank);
        requireSectionIfPresent(targetContent, request.getTargetSectionId());
        QuestionBankDto.QuestionData copy = copyQuestion(source, request.getTargetSectionId());
        copy.setPosition(nextQuestionPosition(targetContent, copy.getSectionId()));
        targetContent.getQuestions().add(copy);
        return persist(targetBank, targetContent);
    }

    @Transactional
    public QuestionBankDto.Response moveQuestion(
            Long bankId,
            String questionId,
            QuestionBankDto.MoveQuestionRequest request,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, request.getVersion(), principal);
        QuestionBankDto.Content content = readContent(bank);
        requireSectionIfPresent(content, request.getTargetSectionId());
        QuestionBankDto.QuestionData question = findQuestion(content, questionId);
        question.setSectionId(request.getTargetSectionId());
        int targetPosition = request.getTargetPosition() == null
            ? nextQuestionPosition(content, request.getTargetSectionId())
            : request.getTargetPosition() + 1;
        int targetSize = (int) content.getQuestions().stream()
            .filter(candidate -> Objects.equals(candidate.getSectionId(), request.getTargetSectionId()))
            .count();
        if (targetPosition < 1 || targetPosition > targetSize + 1) {
            throw badRequest("Target position is outside the question order.");
        }
        question.setPosition(targetPosition);
        normalizePositions(content);
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response setQuestionHidden(
            Long bankId,
            String questionId,
            QuestionBankDto.HiddenRequest request,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, request.getVersion(), principal);
        QuestionBankDto.Content content = readContent(bank);
        findQuestion(content, questionId).setHidden(Boolean.TRUE.equals(request.getHidden()));
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response reorderQuestions(
            Long bankId,
            QuestionBankDto.QuestionReorderRequest request,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, request.getVersion(), principal);
        QuestionBankDto.Content content = readContent(bank);
        requireSectionIfPresent(content, request.getSectionId());
        List<QuestionBankDto.QuestionData> group = questionsInSection(content, request.getSectionId());
        validateCompleteOrder(
                request.getQuestionIds(),
                group.stream().map(QuestionBankDto.QuestionData::getId).toList(),
                "question"
        );
        Map<String, QuestionBankDto.QuestionData> byId = group.stream()
                .collect(Collectors.toMap(QuestionBankDto.QuestionData::getId, Function.identity()));
        for (int index = 0; index < request.getQuestionIds().size(); index++) {
            byId.get(request.getQuestionIds().get(index)).setPosition(index + 1);
        }
        normalizePositions(content);
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response addOption(
            Long bankId,
            String questionId,
            QuestionBankDto.OptionRequest request,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, request.getVersion(), principal);
        QuestionBankDto.Content content = readContent(bank);
        QuestionBankDto.QuestionData question = findQuestion(content, questionId);
        QuestionBankDto.OptionData option = fromOptionInput(
                QuestionBankDto.OptionInput.builder()
                        .optionText(request.getOptionText())
                        .correct(request.getCorrect())
                        .optionPoints(request.getOptionPoints())
                        .build(),
                question.getOptions().size() + 1
        );
        question.getOptions().add(option);
        validateQuestion(question);
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response updateOption(
            Long bankId,
            String questionId,
            String optionId,
            QuestionBankDto.OptionRequest request,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, request.getVersion(), principal);
        QuestionBankDto.Content content = readContent(bank);
        QuestionBankDto.QuestionData question = findQuestion(content, questionId);
        QuestionBankDto.OptionData option = findOption(question, optionId);
        option.setOptionText(defaultString(request.getOptionText()));
        option.setCorrect(Boolean.TRUE.equals(request.getCorrect()));
        option.setOptionPoints(defaultNumber(request.getOptionPoints()));
        validateQuestion(question);
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response deleteOption(
            Long bankId,
            String questionId,
            String optionId,
            Long expectedVersion,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, expectedVersion, principal);
        QuestionBankDto.Content content = readContent(bank);
        QuestionBankDto.QuestionData question = findQuestion(content, questionId);
        question.getOptions().remove(findOption(question, optionId));
        normalizeOptionPositions(question);
        validateQuestion(question);
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response duplicateOption(
            Long bankId,
            String questionId,
            String optionId,
            Long expectedVersion,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, expectedVersion, principal);
        QuestionBankDto.Content content = readContent(bank);
        QuestionBankDto.QuestionData question = findQuestion(content, questionId);
        QuestionBankDto.OptionData source = findOption(question, optionId);
        QuestionBankDto.OptionData copy = QuestionBankDto.OptionData.builder()
                .id(newId())
                .optionText(source.getOptionText())
                .correct(false)
                .optionPoints(source.getOptionPoints())
                .position(question.getOptions().size() + 1)
                .build();
        question.getOptions().add(copy);
        validateQuestion(question);
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response reorderOptions(
            Long bankId,
            String questionId,
            QuestionBankDto.ReorderRequest request,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, request.getVersion(), principal);
        QuestionBankDto.Content content = readContent(bank);
        QuestionBankDto.QuestionData question = findQuestion(content, questionId);
        validateCompleteOrder(
                request.getIds(),
                question.getOptions().stream().map(QuestionBankDto.OptionData::getId).toList(),
                "option"
        );
        Map<String, QuestionBankDto.OptionData> byId = question.getOptions().stream()
                .collect(Collectors.toMap(QuestionBankDto.OptionData::getId, Function.identity()));
        List<QuestionBankDto.OptionData> ordered = new ArrayList<>();
        for (int index = 0; index < request.getIds().size(); index++) {
            QuestionBankDto.OptionData option = byId.get(request.getIds().get(index));
            option.setPosition(index + 1);
            ordered.add(option);
        }
        question.setOptions(ordered);
        return persist(bank, content);
    }

    @Transactional
    public QuestionBankDto.Response importQuestions(
            Long bankId,
            Long expectedVersion,
            MultipartFile file,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = mutableBank(bankId, expectedVersion, principal);
        QuestionBankDto.Content content = readContent(bank);
        StoredFile stored = storeFile(file);
        ImportParseResult parsed = parseStructuredCsv(stored, content);
        QuestionBankDto.ImportData metadata = QuestionBankDto.ImportData.builder()
                .id(newId())
                .originalFilename(stored.originalFilename())
                .sanitizedFilename(stored.sanitizedFilename())
                .storedFilename(stored.storedFilename())
                .contentType(stored.contentType())
                .extension(stored.extension())
                .sizeBytes(stored.sizeBytes())
                .status(parsed.status())
                .parserName(parsed.parserName())
                .errorMessage(parsed.errorMessage())
                .uploadedByUserId(principal.getId())
                .createdAt(stored.createdAt())
                .build();
        content.getImports().add(metadata);
        try {
            return persist(bank, content);
        } catch (RuntimeException ex) {
            deleteStoredFile(stored.storedFilename());
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public CsvExport exportQuestions(Long bankId, AuthPrincipal principal) {
        QuestionBankEntity bank = getBank(bankId);
        requireAccess(bank, principal);
        QuestionBankDto.Content content = readContent(bank);
        Map<String, String> sectionNames = content.getSections().stream()
                .collect(Collectors.toMap(
                        QuestionBankDto.SectionData::getId,
                        QuestionBankDto.SectionData::getName
                ));

        StringBuilder csv = new StringBuilder();
        appendCsvRow(csv, List.of(
                "question_id", "section", "question_order", "question_type",
                "question_text", "scoring_type", "score", "text_answer",
                "feedback", "hidden", "option_order", "option_text",
                "correct", "option_points"
        ));
        for (QuestionBankDto.QuestionData question : orderedQuestions(content)) {
            List<QuestionBankDto.OptionData> options = question.getOptions();
            if (options.isEmpty()) {
                appendQuestionCsvRow(csv, question, sectionNames, null);
            } else {
                options.stream()
                        .sorted((left, right) -> Integer.compare(left.getPosition(), right.getPosition()))
                        .forEach(option -> appendQuestionCsvRow(csv, question, sectionNames, option));
            }
        }
        String downloadName = safeDownloadName(bank.getName()) + "-questions.csv";
        return new CsvExport(csv.toString().getBytes(StandardCharsets.UTF_8), downloadName);
    }

    private StoredFile storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("Please select a question file to upload.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new QuestionBankException("File size exceeds 100 MB.", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        String originalFilename = file.getOriginalFilename();
        String sanitizedFilename = sanitizeFilename(originalFilename);
        String extension = extensionOf(sanitizedFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new QuestionBankException(
                    "Unsupported file format. Allowed formats: PDF, DOC, DOCX, CSV, XLS, XLSX.",
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE
            );
        }

        Path root = Path.of(uploadDirectory).toAbsolutePath().normalize();
        String storedFilename = UUID.randomUUID() + "." + extension;
        Path target = root.resolve(storedFilename).normalize();
        if (!target.startsWith(root)) {
            throw badRequest("Invalid upload filename.");
        }
        try {
            Files.createDirectories(root);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }
        } catch (IOException ex) {
            throw new QuestionBankException(
                    "Unable to store the question file. Please try again later.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        QuestionBankDto.ImportStatus status = QuestionBankDto.ImportStatus.STORED;
        String parserName = null;
        String errorMessage = null;
        ImportParser parser = importParsers.stream()
                .filter(candidate -> candidate.supports(extension))
                .findFirst()
                .orElse(null);
        if (parser != null) {
            parserName = parser.name();
            try {
                parser.parse(target);
                status = QuestionBankDto.ImportStatus.PARSED;
            } catch (IOException ex) {
                status = QuestionBankDto.ImportStatus.FAILED;
                errorMessage = "The file was stored, but parsing failed.";
            }
        }
        return new StoredFile(
                originalFilename == null ? sanitizedFilename : originalFilename,
                sanitizedFilename,
                storedFilename,
                file.getContentType(),
                extension,
                file.getSize(),
                status,
                parserName,
                errorMessage,
                LocalDateTime.now()
        );
    }

    private void deleteStoredFile(String storedFilename) {
        if (storedFilename == null || storedFilename.isBlank()) {
            return;
        }
        Path root = Path.of(uploadDirectory).toAbsolutePath().normalize();
        Path target = root.resolve(storedFilename).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Best-effort cleanup must not hide a successful database delete.
        }
    }

    private ImportParseResult parseStructuredCsv(StoredFile stored, QuestionBankDto.Content content) {
        if (!"csv".equals(stored.extension())) {
            return new ImportParseResult(stored.status(), stored.parserName(), stored.errorMessage());
        }
        Path root = Path.of(uploadDirectory).toAbsolutePath().normalize();
        Path source = root.resolve(stored.storedFilename()).normalize();
        try {
            List<List<String>> rows = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    rows.add(parseCsvLine(line));
                }
            }
            if (rows.isEmpty()) {
                throw badRequest("The imported CSV file is empty.");
            }
            Map<String, Integer> headers = rows.get(0).stream()
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toMap(Function.identity(), rows.get(0)::indexOf, (left, right) -> left));
            if (!headers.containsKey("question_text") || !headers.containsKey("question_type")) {
                return new ImportParseResult(QuestionBankDto.ImportStatus.STORED, "CSV", null);
            }
            Map<String, QuestionBankDto.QuestionData> questions = new LinkedHashMap<>();
            for (List<String> row : rows.subList(1, rows.size())) {
                if (row.stream().allMatch(String::isBlank)) {
                    continue;
                }
                String questionText = csvValue(row, headers, "question_text");
                QuestionBankDto.QuestionType type;
                try {
                    type = QuestionBankDto.QuestionType.valueOf(
                            csvValue(row, headers, "question_type").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    throw badRequest("The CSV contains an unsupported question type.");
                }
                String sectionName = csvValue(row, headers, "section");
                String sectionId = findOrCreateImportedSection(content, sectionName);
                String key = sectionId + "\u0000" + questionText;
                QuestionBankDto.QuestionData question = questions.computeIfAbsent(key, ignored ->
                        QuestionBankDto.QuestionData.builder()
                                .id(newId()).sectionId(sectionId).questionType(type)
                                .questionText(questionText)
                                .scoringType(parseEnum(csvValue(row, headers, "scoring_type"), QuestionBankDto.ScoringType.class))
                                .score(parseDecimal(csvValue(row, headers, "score"), "score"))
                                .textAnswer(csvValue(row, headers, "text_answer"))
                                .feedback(csvValue(row, headers, "feedback"))
                                .correctFeedback(csvValue(row, headers, "correct_feedback"))
                                .incorrectFeedback(csvValue(row, headers, "incorrect_feedback"))
                                .correctScore(parseDecimal(csvValue(row, headers, "correct_score"), "correct_score"))
                                .incorrectScore(parseDecimal(csvValue(row, headers, "incorrect_score"), "incorrect_score"))
                                .position(nextQuestionPosition(content, sectionId))
                                .build());
                String optionText = csvValue(row, headers, "option_text");
                if (!optionText.isBlank()) {
                    question.getOptions().add(QuestionBankDto.OptionData.builder()
                            .id(newId()).optionText(optionText)
                            .correct(Boolean.parseBoolean(csvValue(row, headers, "correct")))
                            .optionPoints(parseDecimal(csvValue(row, headers, "option_points"), "option_points"))
                            .position(question.getOptions().size() + 1).build());
                }
            }
            questions.values().forEach(question -> {
                validateQuestion(question);
                question.setPosition(nextQuestionPosition(content, question.getSectionId()));
                content.getQuestions().add(question);
            });
            return new ImportParseResult(QuestionBankDto.ImportStatus.PARSED, "CSV", null);
        } catch (IOException ex) {
            return new ImportParseResult(QuestionBankDto.ImportStatus.FAILED, "CSV",
                    "The file was stored, but parsing failed.");
        }
    }

    private String csvValue(List<String> row, Map<String, Integer> headers, String name) {
        Integer index = headers.get(name);
        return index == null || index >= row.size() ? "" : row.get(index).trim();
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        if (quoted) {
            throw badRequest("The imported CSV contains an unterminated quoted field.");
        }
        fields.add(field.toString());
        return fields;
    }

    private String findOrCreateImportedSection(QuestionBankDto.Content content, String name) {
        if (name.isBlank()) {
            return null;
        }
        return content.getSections().stream()
                .filter(section -> section.getName().equalsIgnoreCase(name))
                .map(QuestionBankDto.SectionData::getId)
                .findFirst()
                .orElseGet(() -> {
                    String id = newId();
                    content.getSections().add(QuestionBankDto.SectionData.builder()
                            .id(id).name(normalizeSectionName(name))
                            .position(content.getSections().size() + 1).build());
                    return id;
                });
    }

    private <T extends Enum<T>> T parseEnum(String value, Class<T> type) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw badRequest("The imported CSV contains an invalid " + type.getSimpleName() + ".");
        }
    }

    private BigDecimal parseDecimal(String value, String field) {
        if (value.isBlank()) {
            return ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw badRequest("The imported CSV contains an invalid " + field + ".");
        }
    }

    private record ImportParseResult(
            QuestionBankDto.ImportStatus status,
            String parserName,
            String errorMessage
    ) {
    }

    private String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw badRequest("Uploaded file must have a filename.");
        }
        String withNormalizedSeparators = originalFilename.replace('\\', '/');
        String baseName = withNormalizedSeparators.substring(
                withNormalizedSeparators.lastIndexOf('/') + 1);
        if (baseName.isBlank() || ".".equals(baseName) || "..".equals(baseName)) {
            throw badRequest("Invalid upload filename.");
        }
        String sanitized = baseName
                .replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("[^A-Za-z0-9._ -]", "_")
                .replaceAll("\\.{2,}", ".")
                .trim();
        if (sanitized.isBlank() || sanitized.length() > 255) {
            throw badRequest("Invalid upload filename.");
        }
        return sanitized;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 1 || dot == filename.length() - 1
                ? ""
                : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void appendQuestionCsvRow(
            StringBuilder csv,
            QuestionBankDto.QuestionData question,
            Map<String, String> sectionNames,
            QuestionBankDto.OptionData option
    ) {
        appendCsvRow(csv, List.of(
                question.getId(),
                sectionNames.getOrDefault(question.getSectionId(), ""),
                String.valueOf(question.getPosition()),
                enumName(question.getQuestionType()),
                question.getQuestionText(),
                enumName(question.getScoringType()),
                question.getScore().toPlainString(),
                question.getTextAnswer(),
                question.getFeedback(),
                String.valueOf(question.isHidden()),
                option == null ? "" : String.valueOf(option.getPosition()),
                option == null ? "" : option.getOptionText(),
                option == null ? "" : String.valueOf(option.isCorrect()),
                option == null ? "" : option.getOptionPoints().toPlainString()
        ));
    }

    private void appendCsvRow(StringBuilder csv, List<String> fields) {
        csv.append(fields.stream().map(this::csvField).collect(Collectors.joining(",")))
                .append("\r\n");
    }

    private String csvField(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String safeDownloadName(String value) {
        String safe = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return safe.isBlank() ? "question-bank" : safe;
    }

    private String enumName(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private record StoredFile(
            String originalFilename,
            String sanitizedFilename,
            String storedFilename,
            String contentType,
            String extension,
            long sizeBytes,
            QuestionBankDto.ImportStatus status,
            String parserName,
            String errorMessage,
            LocalDateTime createdAt
    ) {
    }

    private QuestionBankDto.Content buildInitialContent(QuestionBankDto.CreateRequest request) {
        QuestionBankDto.Content content = QuestionBankDto.Content.builder().build();
        Map<String, String> sectionClientIds = new HashMap<>();
        Set<String> sectionNames = new HashSet<>();

        List<QuestionBankDto.SectionInput> sections =
                request.getSections() == null ? List.of() : request.getSections();
        for (int index = 0; index < sections.size(); index++) {
            QuestionBankDto.SectionInput input = sections.get(index);
            String name = normalizeSectionName(input.getName());
            if (!sectionNames.add(name.toLowerCase(Locale.ROOT))) {
                throw conflict("Section names must be unique within a Question Bank.");
            }
            String id = newId();
            String clientId = blankToNull(input.getClientId());
            if (clientId != null && sectionClientIds.putIfAbsent(clientId, id) != null) {
                throw badRequest("Section client references must be unique.");
            }
            content.getSections().add(QuestionBankDto.SectionData.builder()
                    .id(id)
                    .name(name)
                    .position(index + 1)
                    .build());
        }

        List<QuestionBankDto.CreateQuestionInput> questions =
                request.getQuestions() == null ? List.of() : request.getQuestions();
        for (QuestionBankDto.CreateQuestionInput input : questions) {
            String clientSectionId = blankToNull(input.getSectionClientId());
            String sectionId = clientSectionId == null ? null : sectionClientIds.get(clientSectionId);
            if (clientSectionId != null && sectionId == null) {
                throw badRequest("Question references an unknown section client ID.");
            }
            QuestionBankDto.QuestionRequest mapped = QuestionBankDto.QuestionRequest.builder()
                    .sectionId(sectionId)
                    .questionType(input.getQuestionType())
                    .questionText(input.getQuestionText())
                    .scoringType(input.getScoringType())
                    .score(input.getScore())
                    .textAnswer(input.getTextAnswer())
                    .feedback(input.getFeedback())
                    .correctFeedback(input.getCorrectFeedback())
                    .incorrectFeedback(input.getIncorrectFeedback())
                    .correctScore(input.getCorrectScore())
                    .incorrectScore(input.getIncorrectScore())
                    .hidden(input.getHidden())
                    .options(input.getOptions())
                    .version(0L)
                    .build();
            QuestionBankDto.QuestionData question = fromQuestionRequest(mapped, null);
            question.setId(newId());
            question.setPosition(nextQuestionPosition(content, sectionId));
            validateQuestion(question);
            content.getQuestions().add(question);
        }
        normalizePositions(content);
        return content;
    }

    private QuestionBankDto.QuestionData fromQuestionRequest(
            QuestionBankDto.QuestionRequest request,
            QuestionBankDto.QuestionData existing
    ) {
        Map<String, QuestionBankDto.OptionData> existingOptions = existing == null
                ? Map.of()
                : existing.getOptions().stream().collect(Collectors.toMap(
                        QuestionBankDto.OptionData::getId,
                        Function.identity()
                ));
        Set<String> usedIds = new HashSet<>();
        List<QuestionBankDto.OptionData> options = new ArrayList<>();
        List<QuestionBankDto.OptionInput> inputs =
                request.getOptions() == null ? List.of() : request.getOptions();
        for (int index = 0; index < inputs.size(); index++) {
            QuestionBankDto.OptionInput input = inputs.get(index);
            String suppliedId = blankToNull(input.getId());
            if (suppliedId != null && !existingOptions.containsKey(suppliedId)) {
                throw badRequest("Option ID does not belong to this question: " + suppliedId);
            }
            if (suppliedId != null && !usedIds.add(suppliedId)) {
                throw badRequest("Option IDs must not be duplicated.");
            }
            QuestionBankDto.OptionData option = fromOptionInput(input, index + 1);
            if (suppliedId != null) {
                option.setId(suppliedId);
            }
            options.add(option);
        }
        return QuestionBankDto.QuestionData.builder()
                .id(existing == null ? null : existing.getId())
                .sectionId(blankToNull(request.getSectionId()))
                .questionType(request.getQuestionType())
                .questionText(defaultString(request.getQuestionText()))
                .scoringType(request.getScoringType())
                .score(defaultNumber(request.getScore()))
                .textAnswer(defaultString(request.getTextAnswer()))
                .feedback(defaultString(request.getFeedback()))
                .correctFeedback(defaultString(request.getCorrectFeedback()))
                .incorrectFeedback(defaultString(request.getIncorrectFeedback()))
                .correctScore(defaultNumber(request.getCorrectScore()))
                .incorrectScore(defaultNumber(request.getIncorrectScore()))
                .hidden(Boolean.TRUE.equals(request.getHidden()))
                .position(existing == null ? null : existing.getPosition())
                .options(options)
                .build();
    }

    private QuestionBankDto.OptionData fromOptionInput(
            QuestionBankDto.OptionInput input,
            int position
    ) {
        return QuestionBankDto.OptionData.builder()
                .id(blankToNull(input.getId()) == null ? newId() : input.getId())
                .optionText(defaultString(input.getOptionText()))
                .correct(Boolean.TRUE.equals(input.getCorrect()))
                .optionPoints(defaultNumber(input.getOptionPoints()))
                .position(position)
                .build();
    }

    private QuestionBankDto.QuestionData copyQuestion(
            QuestionBankDto.QuestionData source,
            String targetSectionId
    ) {
        List<QuestionBankDto.OptionData> optionCopies = new ArrayList<>();
        for (int index = 0; index < source.getOptions().size(); index++) {
            QuestionBankDto.OptionData option = source.getOptions().get(index);
            optionCopies.add(QuestionBankDto.OptionData.builder()
                    .id(newId())
                    .optionText(option.getOptionText())
                    .correct(option.isCorrect())
                    .optionPoints(option.getOptionPoints())
                    .position(index + 1)
                    .build());
        }
        return QuestionBankDto.QuestionData.builder()
                .id(newId())
                .sectionId(targetSectionId)
                .questionType(source.getQuestionType())
                .questionText(source.getQuestionText())
                .scoringType(source.getScoringType())
                .score(source.getScore())
                .textAnswer(source.getTextAnswer())
                .feedback(source.getFeedback())
                .correctFeedback(source.getCorrectFeedback())
                .incorrectFeedback(source.getIncorrectFeedback())
                .correctScore(source.getCorrectScore())
                .incorrectScore(source.getIncorrectScore())
                .hidden(source.isHidden())
                .position(source.getPosition())
                .options(optionCopies)
                .build();
    }

    void validateQuestion(QuestionBankDto.QuestionData question) {
        assertLength(question.getQuestionText(), 500, "Question text");
        assertLength(question.getTextAnswer(), 500, "Text answer");
        assertLength(question.getFeedback(), 500, "Feedback");
        assertLength(question.getCorrectFeedback(), 500, "Correct feedback");
        assertLength(question.getIncorrectFeedback(), 500, "Incorrect feedback");
        assertRange(question.getScore(), "Score");
        assertRange(question.getCorrectScore(), "Correct score");
        assertRange(question.getIncorrectScore(), "Incorrect score");

        List<QuestionBankDto.OptionData> options =
                question.getOptions() == null ? List.of() : question.getOptions();
        question.setOptions(new ArrayList<>(options));
        for (QuestionBankDto.OptionData option : options) {
            assertLength(option.getOptionText(), 200, "Option text");
            assertRange(option.getOptionPoints(), "Option points");
        }

        if (question.getQuestionType() == null) {
            if (!options.isEmpty()
                    || question.getScoringType() != null
                    || !question.getTextAnswer().isBlank()
                    || question.getScore().compareTo(ZERO) != 0) {
                throw badRequest(
                        "A question without a selected type cannot contain answers, options, or scoring.");
            }
            return;
        }

        if (question.getQuestionType() == QuestionBankDto.QuestionType.SHORT_TEXT) {
            if (!options.isEmpty()) {
                throw badRequest("SHORT_TEXT questions must not contain answer options.");
            }
            if (question.getScoringType() == QuestionBankDto.ScoringType.ADVANCED) {
                throw badRequest("Advanced per-option scoring is not valid for SHORT_TEXT questions.");
            }
            return;
        }

        if (!question.getTextAnswer().isBlank()) {
            throw badRequest("Choice questions must not contain a short-text answer.");
        }
        if (options.size() < 2) {
            throw badRequest("Choice questions must contain at least two options.");
        }
        for (QuestionBankDto.OptionData option : options) {
            if (option.getOptionText().isBlank()) {
                throw badRequest("Choice option text must not be empty.");
            }
        }

        long correctOptions = options.stream()
                .filter(QuestionBankDto.OptionData::isCorrect)
                .count();
        if (question.getQuestionType() == QuestionBankDto.QuestionType.SINGLE_ANSWER
                && correctOptions != 1) {
            throw badRequest("SINGLE_ANSWER questions must have exactly one correct option.");
        }
        if (question.getQuestionType() == QuestionBankDto.QuestionType.MULTIPLE_ANSWER
                && correctOptions < 1) {
            throw badRequest("MULTIPLE_ANSWER questions must have one or more correct options.");
        }

        if (question.getScoringType() == QuestionBankDto.ScoringType.ADVANCED) {
            BigDecimal total = options.stream()
                    .map(QuestionBankDto.OptionData::getOptionPoints)
                    .reduce(ZERO, BigDecimal::add);
            if (total.compareTo(ONE_HUNDRED) > 0) {
                throw badRequest("Advanced option points must total no more than 100.");
            }
        } else if (options.stream()
                .anyMatch(option -> option.getOptionPoints().compareTo(ZERO) != 0)) {
            throw badRequest("Per-option points require ADVANCED scoring.");
        }
    }

    private void assertRange(BigDecimal value, String field) {
        if (value == null || value.compareTo(ZERO) < 0 || value.compareTo(ONE_HUNDRED) > 0
                || value.scale() > 2) {
            throw badRequest(field + " must be between 0 and 100 with at most two decimal places.");
        }
    }

    private void assertLength(String value, int maximum, String field) {
        if (value != null && value.length() > maximum) {
            throw badRequest(field + " must not exceed " + maximum + " characters.");
        }
    }

    private QuestionBankDto.SectionData findSection(
            QuestionBankDto.Content content,
            String sectionId
    ) {
        return content.getSections().stream()
                .filter(section -> Objects.equals(section.getId(), sectionId))
                .findFirst()
                .orElseThrow(() -> new QuestionBankException(
                        "Question section not found.", HttpStatus.NOT_FOUND));
    }

    private int indexOfSection(
            List<QuestionBankDto.SectionData> sections,
            String sectionId
    ) {
        for (int index = 0; index < sections.size(); index++) {
            if (Objects.equals(sections.get(index).getId(), sectionId)) {
                return index;
            }
        }
        throw new QuestionBankException("Question section not found.", HttpStatus.NOT_FOUND);
    }

    private QuestionBankDto.QuestionData findQuestion(
            QuestionBankDto.Content content,
            String questionId
    ) {
        return content.getQuestions().stream()
                .filter(question -> Objects.equals(question.getId(), questionId))
                .findFirst()
                .orElseThrow(() -> new QuestionBankException(
                        "Question not found.", HttpStatus.NOT_FOUND));
    }

    private QuestionBankDto.OptionData findOption(
            QuestionBankDto.QuestionData question,
            String optionId
    ) {
        return question.getOptions().stream()
                .filter(option -> Objects.equals(option.getId(), optionId))
                .findFirst()
                .orElseThrow(() -> new QuestionBankException(
                        "Question option not found.", HttpStatus.NOT_FOUND));
    }

    private void requireSectionIfPresent(QuestionBankDto.Content content, String sectionId) {
        if (blankToNull(sectionId) != null) {
            findSection(content, sectionId);
        }
    }

    private void ensureUniqueSectionName(
            QuestionBankDto.Content content,
            String name,
            String excludedId
    ) {
        boolean duplicate = content.getSections().stream()
                .anyMatch(section -> !Objects.equals(section.getId(), excludedId)
                        && section.getName().equalsIgnoreCase(name));
        if (duplicate) {
            throw conflict("Section names must be unique within a Question Bank.");
        }
    }

    private String uniqueSectionName(QuestionBankDto.Content content, String desired) {
        String base = normalizeSectionName(desired);
        String candidate = base;
        int suffix = 2;
        while (sectionNameExists(content, candidate)) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    private boolean sectionNameExists(QuestionBankDto.Content content, String candidate) {
        return content.getSections().stream()
                .anyMatch(section -> section.getName().equalsIgnoreCase(candidate));
    }

    private void validateCompleteOrder(
            List<String> requestedIds,
            List<String> actualIds,
            String itemName
    ) {
        if (requestedIds == null
                || requestedIds.size() != actualIds.size()
                || new HashSet<>(requestedIds).size() != requestedIds.size()
                || !new HashSet<>(requestedIds).equals(new HashSet<>(actualIds))) {
            throw badRequest(
                    "Reordering must include every " + itemName + " exactly once.");
        }
    }

    private QuestionBankEntity mutableBank(
            Long bankId,
            Long expectedVersion,
            AuthPrincipal principal
    ) {
        QuestionBankEntity bank = getBank(bankId);
        requireAccess(bank, principal);
        checkVersion(bank, expectedVersion);
        return bank;
    }

    private QuestionBankEntity getBank(Long bankId) {
        return questionBankRepository.findWithReferencesById(bankId)
                .orElseThrow(() -> new QuestionBankException(
                        "Question Bank not found.", HttpStatus.NOT_FOUND));
    }

    private QuestionBankDto.Response persist(
            QuestionBankEntity bank,
            QuestionBankDto.Content content
    ) {
        normalizePositions(content);
        bank.setContentJson(writeContent(content));
        bank.touch();
        QuestionBankEntity saved = questionBankRepository.saveAndFlush(bank);
        return toResponse(saved, content);
    }

    private QuestionBankDto.Content readContent(QuestionBankEntity bank) {
        try {
            QuestionBankDto.Content content = objectMapper.readValue(
                    bank.getContentJson(), QuestionBankDto.Content.class);
            if (content.getSections() == null) {
                content.setSections(new ArrayList<>());
            }
            if (content.getQuestions() == null) {
                content.setQuestions(new ArrayList<>());
            }
            if (content.getImports() == null) {
                content.setImports(new ArrayList<>());
            }
            for (QuestionBankDto.QuestionData question : content.getQuestions()) {
                question.setQuestionText(defaultString(question.getQuestionText()));
                question.setScore(defaultNumber(question.getScore()));
                question.setTextAnswer(defaultString(question.getTextAnswer()));
                question.setFeedback(defaultString(question.getFeedback()));
                question.setCorrectFeedback(defaultString(question.getCorrectFeedback()));
                question.setIncorrectFeedback(defaultString(question.getIncorrectFeedback()));
                question.setCorrectScore(defaultNumber(question.getCorrectScore()));
                question.setIncorrectScore(defaultNumber(question.getIncorrectScore()));
                if (question.getOptions() == null) {
                    question.setOptions(new ArrayList<>());
                }
                for (QuestionBankDto.OptionData option : question.getOptions()) {
                    option.setOptionText(defaultString(option.getOptionText()));
                    option.setOptionPoints(defaultNumber(option.getOptionPoints()));
                }
            }
            normalizePositions(content);
            return content;
        } catch (JsonProcessingException ex) {
            throw new QuestionBankException(
                    "Unable to load Question Bank content.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private String writeContent(QuestionBankDto.Content content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException ex) {
            throw new QuestionBankException(
                    "Unable to save Question Bank content.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private void normalizePositions(QuestionBankDto.Content content) {
        content.getSections().sort(Comparator
                .comparing(
                        QuestionBankDto.SectionData::getPosition,
                        Comparator.nullsLast(Integer::compareTo))
                .thenComparing(QuestionBankDto.SectionData::getId));
        for (int index = 0; index < content.getSections().size(); index++) {
            content.getSections().get(index).setPosition(index + 1);
        }

        Map<String, List<QuestionBankDto.QuestionData>> groups = new LinkedHashMap<>();
        for (QuestionBankDto.QuestionData question : content.getQuestions()) {
            String key = question.getSectionId() == null ? "" : question.getSectionId();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(question);
            normalizeOptionPositions(question);
        }
        for (List<QuestionBankDto.QuestionData> group : groups.values()) {
            group.sort(Comparator
                    .comparing(
                            QuestionBankDto.QuestionData::getPosition,
                            Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(QuestionBankDto.QuestionData::getId));
            for (int index = 0; index < group.size(); index++) {
                group.get(index).setPosition(index + 1);
            }
        }

        Map<String, Integer> sectionOrder = new HashMap<>();
        sectionOrder.put("", 0);
        for (QuestionBankDto.SectionData section : content.getSections()) {
            sectionOrder.put(section.getId(), section.getPosition());
        }
        content.getQuestions().sort(Comparator
                .comparingInt((QuestionBankDto.QuestionData question) ->
                        sectionOrder.getOrDefault(
                                question.getSectionId() == null ? "" : question.getSectionId(),
                                Integer.MAX_VALUE))
                .thenComparing(QuestionBankDto.QuestionData::getPosition)
                .thenComparing(QuestionBankDto.QuestionData::getId));
        content.getImports().sort(Comparator.comparing(
                QuestionBankDto.ImportData::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
    }

    private void normalizeOptionPositions(QuestionBankDto.QuestionData question) {
        question.getOptions().sort(Comparator
                .comparing(
                        QuestionBankDto.OptionData::getPosition,
                        Comparator.nullsLast(Integer::compareTo))
                .thenComparing(QuestionBankDto.OptionData::getId));
        for (int index = 0; index < question.getOptions().size(); index++) {
            question.getOptions().get(index).setPosition(index + 1);
        }
    }

    private List<QuestionBankDto.QuestionData> questionsInSection(
            QuestionBankDto.Content content,
            String sectionId
    ) {
        return content.getQuestions().stream()
                .filter(question -> Objects.equals(question.getSectionId(), sectionId))
                .sorted(Comparator.comparing(QuestionBankDto.QuestionData::getPosition))
                .toList();
    }

    private List<QuestionBankDto.QuestionData> orderedQuestions(
            QuestionBankDto.Content content
    ) {
        normalizePositions(content);
        return content.getQuestions();
    }

    private int nextQuestionPosition(QuestionBankDto.Content content, String sectionId) {
        return (int) content.getQuestions().stream()
                .filter(question -> Objects.equals(question.getSectionId(), sectionId))
                .count() + 1;
    }

    private void checkVersion(QuestionBankEntity bank, Long expectedVersion) {
        if (expectedVersion == null) {
            throw badRequest("Version is required.");
        }
        if (!Objects.equals(bank.getVersion(), expectedVersion)) {
            throw conflict(
                    "Another user has already updated this Question Bank. Refresh and try again.");
        }
    }

    private void requireManager(AuthPrincipal principal) {
        if (principal == null || principal.getRole() == null
                || !MANAGER_ROLES.contains(principal.getRole().toUpperCase(Locale.ROOT))) {
            throw new AccessDeniedException(
                    "Only administrators and instructors may manage Question Banks.");
        }
    }

    private void requireAccess(QuestionBankEntity bank, AuthPrincipal principal) {
        requireManager(principal);
        if (!isAdmin(principal)
                && !Objects.equals(bank.getCreatedBy().getId(), principal.getId())) {
            throw new AccessDeniedException(
                    "Instructors may manage only the Question Banks they created.");
        }
    }

    private boolean isAdmin(AuthPrincipal principal) {
        String role = principal.getRole().toUpperCase(Locale.ROOT);
        return "ADMIN".equals(role) || "SUPER_ADMIN".equals(role);
    }

    private CourseEntity resolveCourse(Long courseId) {
        if (courseId == null) {
            return null;
        }
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new QuestionBankException(
                        "Course not found.", HttpStatus.NOT_FOUND));
    }

    private QuestionBankDto.Response toResponse(
            QuestionBankEntity bank,
            QuestionBankDto.Content content
    ) {
        return QuestionBankDto.Response.builder()
                .id(bank.getId())
                .name(bank.getName())
                .description(bank.getDescription())
                .courseId(bank.getCourse() == null ? null : bank.getCourse().getId())
                .courseName(bank.getCourse() == null ? null : bank.getCourse().getName())
                .createdByUserId(bank.getCreatedBy().getId())
                .createdByEmail(bank.getCreatedBy().getEmail())
                .sections(content.getSections())
                .questions(content.getQuestions())
                .imports(content.getImports())
                .version(bank.getVersion())
                .createdAt(bank.getCreatedAt())
                .updatedAt(bank.getUpdatedAt())
                .build();
    }

    private QuestionBankDto.SummaryResponse toSummary(
            QuestionBankEntity bank,
            QuestionBankDto.Content content
    ) {
        return QuestionBankDto.SummaryResponse.builder()
                .id(bank.getId())
                .name(bank.getName())
                .description(bank.getDescription())
                .courseId(bank.getCourse() == null ? null : bank.getCourse().getId())
                .courseName(bank.getCourse() == null ? null : bank.getCourse().getName())
                .createdByUserId(bank.getCreatedBy().getId())
                .createdByEmail(bank.getCreatedBy().getEmail())
                .sectionCount(content.getSections().size())
                .questionCount(content.getQuestions().size())
                .version(bank.getVersion())
                .createdAt(bank.getCreatedAt())
                .updatedAt(bank.getUpdatedAt())
                .build();
    }

    private String normalizeBankName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) {
            throw badRequest("Question Bank Name is required.");
        }
        if (name.length() > 255) {
            throw badRequest("Question Bank Name must not exceed 255 characters.");
        }
        return name;
    }

    private String normalizeSectionName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank()) {
            throw badRequest("Section Name is required.");
        }
        if (name.length() > 255) {
            throw badRequest("Section Name must not exceed 255 characters.");
        }
        return name;
    }

    private String normalizeDescription(String rawDescription) {
        return rawDescription == null ? null : rawDescription.trim();
    }

    private String normalizeSearch(String rawSearch) {
        String search = blankToNull(rawSearch);
        if (search != null && search.length() > 255) {
            throw badRequest("Search text must not exceed 255 characters.");
        }
        return search;
    }

    private String normalizeIdempotencyKey(String rawKey) {
        String key = blankToNull(rawKey);
        if (key == null) {
            return null;
        }
        if (key.length() > 200 || key.chars().anyMatch(Character::isISOControl)) {
            throw badRequest("Idempotency-Key must be at most 200 printable characters.");
        }
        return key;
    }

    private String hashRequest(QuestionBankDto.CreateRequest request) {
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(request);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(serialized);
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new QuestionBankException(
                    "Unable to process the Question Bank request.",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private BigDecimal defaultNumber(BigDecimal value) {
        return value == null ? ZERO : value;
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }

    private QuestionBankException badRequest(String message) {
        return new QuestionBankException(message, HttpStatus.BAD_REQUEST);
    }

    private QuestionBankException conflict(String message) {
        return new QuestionBankException(message, HttpStatus.CONFLICT);
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    }
            );
        } else {
            action.run();
        }
    }
}
