package com.example.lms.controller;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.QuestionBankDto;
import com.example.lms.service.QuestionBankService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/question-banks")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INSTRUCTOR')")
public class QuestionBankController {

    private final QuestionBankService questionBankService;

    @PostMapping
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody QuestionBankDto.CreateRequest request
    ) {
        QuestionBankService.CreateResult result =
                questionBankService.create(principal, request, idempotencyKey);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        String message = result.replayed()
                ? "Question Bank already created; the original result was returned."
                : "Question Bank created successfully.";
        return ResponseEntity.status(status)
                .body(ApiResponse.success(message, result.response()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<QuestionBankDto.PageResponse>> list(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Question Banks fetched successfully.",
                questionBankService.list(principal, search, page, size)
        ));
    }

    @GetMapping("/{bankId}")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> get(
            @PathVariable Long bankId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ok("Question Bank fetched successfully.",
                questionBankService.get(bankId, principal));
    }

    @PutMapping("/{bankId}")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> update(
            @PathVariable Long bankId,
            @Valid @RequestBody QuestionBankDto.UpdateRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ok("Question Bank updated successfully.",
                questionBankService.update(bankId, request, principal));
    }

    @DeleteMapping("/{bankId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long bankId,
            @RequestParam @PositiveOrZero Long version,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        questionBankService.delete(bankId, version, principal);
        return ResponseEntity.ok(ApiResponse.success("Question Bank deleted successfully."));
    }

    @PostMapping("/{bankId}/sections")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> addSection(
            @PathVariable Long bankId,
            @Valid @RequestBody QuestionBankDto.SectionRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return created("Question section added successfully.",
                questionBankService.addSection(bankId, request, principal));
    }

    @PutMapping("/{bankId}/sections/{sectionId}")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> updateSection(
            @PathVariable Long bankId,
            @PathVariable String sectionId,
            @Valid @RequestBody QuestionBankDto.SectionRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ok("Question section updated successfully.",
                questionBankService.updateSection(bankId, sectionId, request, principal));
    }

    @DeleteMapping("/{bankId}/sections/{sectionId}")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> deleteSection(
            @PathVariable Long bankId,
            @PathVariable String sectionId,
            @RequestParam @PositiveOrZero Long version,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ok("Question section deleted successfully.",
                questionBankService.deleteSection(bankId, sectionId, version, principal));
    }

    @PostMapping("/{bankId}/sections/{sectionId}/duplicate")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> duplicateSection(
            @PathVariable Long bankId,
            @PathVariable String sectionId,
            @RequestParam @PositiveOrZero Long version,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return created("Question section duplicated successfully.",
                questionBankService.duplicateSection(bankId, sectionId, version, principal));
    }

    @PostMapping("/{bankId}/sections/{sectionId}/move-up")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> moveSectionUp(
            @PathVariable Long bankId,
            @PathVariable String sectionId,
            @RequestParam @PositiveOrZero Long version,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ok("Question section moved up successfully.",
                questionBankService.moveSection(bankId, sectionId, -1, version, principal));
    }

    @PostMapping("/{bankId}/sections/{sectionId}/move-down")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> moveSectionDown(
            @PathVariable Long bankId,
            @PathVariable String sectionId,
            @RequestParam @PositiveOrZero Long version,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ok("Question section moved down successfully.",
                questionBankService.moveSection(bankId, sectionId, 1, version, principal));
    }

    @PostMapping("/{bankId}/sections/{sectionId}/copy")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> copySection(
            @PathVariable Long bankId,
            @PathVariable String sectionId,
            @Valid @RequestBody QuestionBankDto.CopyRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return created("Question section copied successfully.",
                questionBankService.copySection(bankId, sectionId, request, principal));
    }

    @PatchMapping("/{bankId}/sections/reorder")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> reorderSections(
            @PathVariable Long bankId,
            @Valid @RequestBody QuestionBankDto.ReorderRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ok("Question sections reordered successfully.",
                questionBankService.reorderSections(bankId, request, principal));
    }

    @PostMapping("/{bankId}/questions")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> addQuestion(
            @PathVariable Long bankId,
            @Valid @RequestBody QuestionBankDto.QuestionRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return created("Question added successfully.",
                questionBankService.addQuestion(bankId, request, principal));
    }

    @PutMapping("/{bankId}/questions/{questionId}")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> updateQuestion(
            @PathVariable Long bankId,
            @PathVariable String questionId,
            @Valid @RequestBody QuestionBankDto.QuestionRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ok("Question updated successfully.",
                questionBankService.updateQuestion(bankId, questionId, request, principal));
    }

    @DeleteMapping("/{bankId}/questions/{questionId}")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> deleteQuestion(
            @PathVariable Long bankId,
            @PathVariable String questionId,
            @RequestParam @PositiveOrZero Long version,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ok("Question deleted successfully.",
                questionBankService.deleteQuestion(bankId, questionId, version, principal));
    }

    @PostMapping("/{bankId}/questions/{questionId}/duplicate")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> duplicateQuestion(
            @PathVariable Long bankId,
            @PathVariable String questionId,
            @RequestParam @PositiveOrZero Long version,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return created("Question duplicated successfully.",
                questionBankService.duplicateQuestion(bankId, questionId, version, principal));
    }

    @PostMapping("/{bankId}/questions/from-existing")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> addExistingQuestion(
            @PathVariable Long bankId,
            @Valid @RequestBody QuestionBankDto.AddExistingQuestionRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return created("Existing question copied successfully.",
                questionBankService.addExistingQuestion(bankId, request, principal));
    }

    @PatchMapping("/{bankId}/questions/{questionId}/move")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> moveQuestion(
            @PathVariable Long bankId,
            @PathVariable String questionId,
            @Valid @RequestBody QuestionBankDto.MoveQuestionRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ok("Question moved successfully.",
                questionBankService.moveQuestion(bankId, questionId, request, principal));
    }

    @PatchMapping("/{bankId}/questions/{questionId}/hidden")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> setHidden(
            @PathVariable Long bankId,
            @PathVariable String questionId,
            @Valid @RequestBody QuestionBankDto.HiddenRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ok("Question visibility updated successfully.",
                questionBankService.setQuestionHidden(bankId, questionId, request, principal));
    }

    @PatchMapping("/{bankId}/questions/reorder")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> reorderQuestions(
            @PathVariable Long bankId,
            @Valid @RequestBody QuestionBankDto.QuestionReorderRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ok("Questions reordered successfully.",
                questionBankService.reorderQuestions(bankId, request, principal));
    }

    @PostMapping("/{bankId}/questions/{questionId}/options")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> addOption(
            @PathVariable Long bankId,
            @PathVariable String questionId,
            @Valid @RequestBody QuestionBankDto.OptionRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return created("Question option added successfully.",
                questionBankService.addOption(bankId, questionId, request, principal));
    }

    @PutMapping("/{bankId}/questions/{questionId}/options/{optionId}")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> updateOption(
            @PathVariable Long bankId,
            @PathVariable String questionId,
            @PathVariable String optionId,
            @Valid @RequestBody QuestionBankDto.OptionRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ok("Question option updated successfully.",
                questionBankService.updateOption(bankId, questionId, optionId, request, principal));
    }

    @DeleteMapping("/{bankId}/questions/{questionId}/options/{optionId}")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> deleteOption(
            @PathVariable Long bankId,
            @PathVariable String questionId,
            @PathVariable String optionId,
            @RequestParam @PositiveOrZero Long version,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ok("Question option deleted successfully.",
                questionBankService.deleteOption(bankId, questionId, optionId, version, principal));
    }

    @PostMapping("/{bankId}/questions/{questionId}/options/{optionId}/duplicate")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> duplicateOption(
            @PathVariable Long bankId,
            @PathVariable String questionId,
            @PathVariable String optionId,
            @RequestParam @PositiveOrZero Long version,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return created("Question option duplicated successfully.",
                questionBankService.duplicateOption(
                        bankId, questionId, optionId, version, principal));
    }

    @PatchMapping("/{bankId}/questions/{questionId}/options/reorder")
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> reorderOptions(
            @PathVariable Long bankId,
            @PathVariable String questionId,
            @Valid @RequestBody QuestionBankDto.ReorderRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ok("Question options reordered successfully.",
                questionBankService.reorderOptions(bankId, questionId, request, principal));
    }

    @PostMapping(
            value = "/{bankId}/imports",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<QuestionBankDto.Response>> importQuestions(
            @PathVariable Long bankId,
            @RequestParam @PositiveOrZero Long version,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return created("Question file uploaded successfully.",
                questionBankService.importQuestions(bankId, version, file, principal));
    }

    @GetMapping(value = "/{bankId}/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportQuestions(
            @PathVariable Long bankId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        QuestionBankService.CsvExport export =
                questionBankService.exportQuestions(bankId, principal);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(export.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(export.bytes());
    }

    private ResponseEntity<ApiResponse<QuestionBankDto.Response>> ok(
            String message,
            QuestionBankDto.Response response
    ) {
        return ResponseEntity.ok(ApiResponse.success(message, response));
    }

    private ResponseEntity<ApiResponse<QuestionBankDto.Response>> created(
            String message,
            QuestionBankDto.Response response
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(message, response));
    }
}
