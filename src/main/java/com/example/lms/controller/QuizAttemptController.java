package com.example.lms.controller;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.QuizAttemptDto;
import com.example.lms.service.QuizAttemptService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('LEARNER')")
public class QuizAttemptController {

    private final QuizAttemptService quizAttemptService;

    @PostMapping("/question-banks/{quizId}/attempts/start")
    public ResponseEntity<ApiResponse<QuizAttemptDto.AttemptResponse>> start(
            @PathVariable @Positive Long quizId,
            @Valid @RequestBody QuizAttemptDto.StartRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Quiz attempt started or resumed successfully.",
                quizAttemptService.start(quizId, request, principal)));
    }

    @PostMapping("/question-banks/{quizId}/attempts/retest")
    public ResponseEntity<ApiResponse<QuizAttemptDto.AttemptResponse>> retest(
            @PathVariable @Positive Long quizId,
            @Valid @RequestBody QuizAttemptDto.StartRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Quiz retest started or resumed successfully.",
                quizAttemptService.retest(quizId, request, principal)));
    }

    @GetMapping("/question-banks/{quizId}/attempts/active")
    public ResponseEntity<ApiResponse<QuizAttemptDto.AttemptResponse>> active(
            @PathVariable @Positive Long quizId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Active quiz attempt fetched successfully.",
                quizAttemptService.getActive(quizId, principal)));
    }

    @GetMapping("/quiz-attempts/{attemptId}")
    public ResponseEntity<ApiResponse<QuizAttemptDto.AttemptResponse>> getAttempt(
            @PathVariable @Positive Long attemptId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Quiz attempt fetched successfully.",
                quizAttemptService.getAttempt(attemptId, principal)));
    }

    @GetMapping("/quiz-attempts/{attemptId}/questions/{questionId}")
    public ResponseEntity<ApiResponse<QuizAttemptDto.QuestionResponse>> getQuestion(
            @PathVariable @Positive Long attemptId,
            @PathVariable @NotBlank String questionId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Quiz question fetched successfully.",
                quizAttemptService.getQuestion(attemptId, questionId, principal)));
    }

    @PutMapping("/quiz-attempts/{attemptId}/answers")
    public ResponseEntity<ApiResponse<QuizAttemptDto.SavedAnswerResponse>> saveAnswer(
            @PathVariable @Positive Long attemptId,
            @Valid @RequestBody QuizAttemptDto.SaveAnswerRequest request,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Quiz answer saved successfully.",
                quizAttemptService.saveAnswer(attemptId, request, principal)));
    }

    @PostMapping("/quiz-attempts/{attemptId}/finish")
    public ResponseEntity<ApiResponse<QuizAttemptDto.ResultResponse>> finish(
            @PathVariable @Positive Long attemptId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Quiz submitted successfully.",
                quizAttemptService.finish(attemptId, principal)));
    }

    @GetMapping("/quiz-attempts/{attemptId}/result")
    public ResponseEntity<ApiResponse<QuizAttemptDto.ResultResponse>> result(
            @PathVariable @Positive Long attemptId,
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Quiz result fetched successfully.",
                quizAttemptService.getResult(attemptId, principal)));
    }
}
