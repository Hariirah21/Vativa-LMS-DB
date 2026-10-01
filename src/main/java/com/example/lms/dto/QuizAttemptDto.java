package com.example.lms.dto;

import com.example.lms.entity.QuizAttemptEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class QuizAttemptDto {

    private QuizAttemptDto() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StartRequest {
        @NotNull(message = "Course ID is required.")
        @Positive(message = "Course ID must be greater than zero.")
        private Long courseId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaveAnswerRequest {
        @NotBlank(message = "Question ID is required.")
        @Size(max = 100, message = "Question ID must not exceed 100 characters.")
        private String questionId;

        @Builder.Default
        private List<@NotBlank(message = "Selected option IDs must not be blank.") String>
                selectedOptionIds = new ArrayList<>();

        @Size(max = 500, message = "Text answer must not exceed 500 characters.")
        private String textAnswer;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OptionResponse {
        private String id;
        private String text;
        private Integer position;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SavedAnswerResponse {
        private String questionId;
        private List<String> selectedOptionIds;
        private String textAnswer;
        private LocalDateTime savedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionResponse {
        private Long attemptId;
        private Long quizId;
        private String questionId;
        private Integer questionNumber;
        private Integer totalQuestions;
        private String questionText;
        private QuestionBankDto.QuestionType questionType;
        private List<OptionResponse> options;
        private SavedAnswerResponse savedAnswer;
        private QuizAttemptEntity.Status attemptStatus;
        private String previousQuestionId;
        private String nextQuestionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AttemptResponse {
        private Long attemptId;
        private Long quizId;
        private String quizName;
        private Long courseId;
        private Integer attemptNumber;
        private QuizAttemptEntity.Status status;
        private LocalDateTime startedAt;
        private LocalDateTime submittedAt;
        private Integer totalQuestions;
        private Integer answeredQuestions;
        private List<SavedAnswerResponse> savedAnswers;
        private QuestionResponse currentQuestion;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CorrectAnswerResponse {
        private List<String> selectedOptionIds;
        private String textAnswer;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionReviewResponse {
        private String questionId;
        private String questionText;
        private QuestionBankDto.QuestionType questionType;
        private List<OptionResponse> options;
        private SavedAnswerResponse learnerAnswer;
        private Boolean correct;
        private BigDecimal awardedPoints;
        private CorrectAnswerResponse correctAnswer;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResultResponse {
        private Long attemptId;
        private Long quizId;
        private String quizName;
        private Long courseId;
        private Integer attemptNumber;
        private QuizAttemptEntity.Status status;
        private Integer correctAnswers;
        private Integer totalQuestions;
        private BigDecimal earnedPoints;
        private BigDecimal possiblePoints;
        private BigDecimal score;
        private BigDecimal passPercentage;
        private Boolean passed;
        private Long timeSpentSeconds;
        private LocalDateTime startedAt;
        private LocalDateTime submittedAt;
        private boolean nextUnitAllowed;
        private List<QuestionReviewResponse> questions;
    }
}
