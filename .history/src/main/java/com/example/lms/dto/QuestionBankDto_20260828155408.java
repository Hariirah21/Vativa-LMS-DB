package com.example.lms.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class QuestionBankDto {

    private QuestionBankDto() {
    }

    public enum QuestionType {
        SINGLE_ANSWER,
        MULTIPLE_ANSWER,
        SHORT_TEXT
    }

    public enum ScoringType {
        BASIC,
        ADVANCED
    }

    public enum ImportStatus {
        STORED,
        PARSED,
        FAILED
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotBlank(message = "Question Bank Name is required.")
        @Size(max = 255, message = "Question Bank Name must not exceed 255 characters.")
        private String name;

        @Size(max = 1000, message = "Question Bank Description must not exceed 1000 characters.")
        private String description;

        private Long courseId;

        @Valid
        @Builder.Default
        private List<SectionInput> sections = new ArrayList<>();

        @Valid
        @Builder.Default
        private List<CreateQuestionInput> questions = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        @NotBlank(message = "Question Bank Name is required.")
        @Size(max = 255, message = "Question Bank Name must not exceed 255 characters.")
        private String name;
        @Size(max = 1000, message = "Question Bank Description must not exceed 1000 characters.")
        private String description;
        private Long courseId;
        @NotNull(message = "Version is required.")
        @PositiveOrZero(message = "Version must be zero or greater.")
        private Long version;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SectionInput {
        @Size(max = 100, message = "Section client reference must not exceed 100 characters.")
        private String clientId;
        @NotBlank(message = "Section Name is required.")
        @Size(max = 255, message = "Section Name must not exceed 255 characters.")
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SectionRequest {
        @NotBlank(message = "Section Name is required.")
        @Size(max = 255, message = "Section Name must not exceed 255 characters.")
        private String name;
        @NotNull(message = "Version is required.")
        @PositiveOrZero(message = "Version must be zero or greater.")
        private Long version;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateQuestionInput {
        private String sectionClientId;
        private QuestionType questionType;
        @Size(max = 500, message = "Question text must not exceed 500 characters.")
        private String questionText;
        private ScoringType scoringType;
        @DecimalMin(value = "0.00", message = "Score must be between 0 and 100.")
        @DecimalMax(value = "100.00", message = "Score must be between 0 and 100.")
        @Digits(integer = 3, fraction = 2, message = "Score must have at most two decimal places.")
        private BigDecimal score;
        @Size(max = 500, message = "Text answer must not exceed 500 characters.")
        private String textAnswer;
        @Size(max = 500, message = "Feedback must not exceed 500 characters.")
        private String feedback;
        @Size(max = 500, message = "Correct feedback must not exceed 500 characters.")
        private String correctFeedback;
        @Size(max = 500, message = "Incorrect feedback must not exceed 500 characters.")
        private String incorrectFeedback;
        @DecimalMin(value = "0.00", message = "Correct score must be between 0 and 100.")
        @DecimalMax(value = "100.00", message = "Correct score must be between 0 and 100.")
        private BigDecimal correctScore;
        @DecimalMin(value = "0.00", message = "Incorrect score must be between 0 and 100.")
        @DecimalMax(value = "100.00", message = "Incorrect score must be between 0 and 100.")
        private BigDecimal incorrectScore;
        private Boolean hidden;
        @Valid
        @Builder.Default
        private List<OptionInput> options = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionRequest {
        private String sectionId;
        private QuestionType questionType;
        @Size(max = 500, message = "Question text must not exceed 500 characters.")
        private String questionText;
        private ScoringType scoringType;
        @DecimalMin(value = "0.00", message = "Score must be between 0 and 100.")
        @DecimalMax(value = "100.00", message = "Score must be between 0 and 100.")
        @Digits(integer = 3, fraction = 2, message = "Score must have at most two decimal places.")
        private BigDecimal score;
        @Size(max = 500, message = "Text answer must not exceed 500 characters.")
        private String textAnswer;
        @Size(max = 500, message = "Feedback must not exceed 500 characters.")
        private String feedback;
        @Size(max = 500, message = "Correct feedback must not exceed 500 characters.")
        private String correctFeedback;
        @Size(max = 500, message = "Incorrect feedback must not exceed 500 characters.")
        private String incorrectFeedback;
        @DecimalMin(value = "0.00", message = "Correct score must be between 0 and 100.")
        @DecimalMax(value = "100.00", message = "Correct score must be between 0 and 100.")
        private BigDecimal correctScore;
        @DecimalMin(value = "0.00", message = "Incorrect score must be between 0 and 100.")
        @DecimalMax(value = "100.00", message = "Incorrect score must be between 0 and 100.")
        private BigDecimal incorrectScore;
        private Boolean hidden;
        @Valid
        @Builder.Default
        private List<OptionInput> options = new ArrayList<>();
        @NotNull(message = "Version is required.")
        @PositiveOrZero(message = "Version must be zero or greater.")
        private Long version;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OptionInput {
        private String id;
        @Size(max = 200, message = "Option text must not exceed 200 characters.")
        private String optionText;
        private Boolean correct;
        @DecimalMin(value = "0.00", message = "Option points must be between 0 and 100.")
        @DecimalMax(value = "100.00", message = "Option points must be between 0 and 100.")
        @Digits(integer = 3, fraction = 2, message = "Option points must have at most two decimal places.")
        private BigDecimal optionPoints;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OptionRequest {
        @Size(max = 200, message = "Option text must not exceed 200 characters.")
        private String optionText;
        private Boolean correct;
        @DecimalMin(value = "0.00", message = "Option points must be between 0 and 100.")
        @DecimalMax(value = "100.00", message = "Option points must be between 0 and 100.")
        @Digits(integer = 3, fraction = 2, message = "Option points must have at most two decimal places.")
        private BigDecimal optionPoints;
        @NotNull(message = "Version is required.")
        @PositiveOrZero(message = "Version must be zero or greater.")
        private Long version;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReorderRequest {
        @NotEmpty(message = "At least one ID is required for reordering.")
        private List<@NotBlank(message = "Reorder IDs must not be blank.") String> ids;
        @NotNull(message = "Version is required.")
        @PositiveOrZero(message = "Version must be zero or greater.")
        private Long version;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionReorderRequest {
        private String sectionId;
        @NotEmpty(message = "At least one question ID is required for reordering.")
        private List<@NotBlank(message = "Question IDs must not be blank.") String> questionIds;
        @NotNull(message = "Version is required.")
        @PositiveOrZero(message = "Version must be zero or greater.")
        private Long version;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CopyRequest {
        @NotNull(message = "Target Question Bank ID is required.")
        private Long targetBankId;
        private String targetSectionId;
        @NotNull(message = "Target Question Bank version is required.")
        @PositiveOrZero(message = "Version must be zero or greater.")
        private Long targetVersion;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AddExistingQuestionRequest {
        @NotNull(message = "Source Question Bank ID is required.")
        private Long sourceBankId;
        @NotBlank(message = "Source Question ID is required.")
        private String sourceQuestionId;
        private String targetSectionId;
        @NotNull(message = "Version is required.")
        @PositiveOrZero(message = "Version must be zero or greater.")
        private Long version;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MoveQuestionRequest {
        private String targetSectionId;
        @PositiveOrZero(message = "Target position must be zero or greater.")
        private Integer targetPosition;
        @NotNull(message = "Version is required.")
        @PositiveOrZero(message = "Version must be zero or greater.")
        private Long version;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HiddenRequest {
        @NotNull(message = "Hidden value is required.")
        private Boolean hidden;
        @NotNull(message = "Version is required.")
        @PositiveOrZero(message = "Version must be zero or greater.")
        private Long version;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Content {
        @Builder.Default
        private List<SectionData> sections = new ArrayList<>();
        @Builder.Default
        private List<QuestionData> questions = new ArrayList<>();
        @Builder.Default
        private List<ImportData> imports = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SectionData {
        private String id;
        private String name;
        private Integer position;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuestionData {
        private String id;
        private String sectionId;
        private QuestionType questionType;
        @Builder.Default
        private String questionText = "";
        private ScoringType scoringType;
        @Builder.Default
        private BigDecimal score = BigDecimal.ZERO;
        @Builder.Default
        private String textAnswer = "";
        @Builder.Default
        private String feedback = "";
        @Builder.Default
        private String correctFeedback = "";
        @Builder.Default
        private String incorrectFeedback = "";
        @Builder.Default
        private BigDecimal correctScore = BigDecimal.ZERO;
        @Builder.Default
        private BigDecimal incorrectScore = BigDecimal.ZERO;
        private boolean hidden;
        private Integer position;
        @Builder.Default
        private List<OptionData> options = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OptionData {
        private String id;
        @Builder.Default
        private String optionText = "";
        private boolean correct;
        @Builder.Default
        private BigDecimal optionPoints = BigDecimal.ZERO;
        private Integer position;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ImportData {
        private String id;
        private String originalFilename;
        private String sanitizedFilename;
        private String storedFilename;
        private String contentType;
        private String extension;
        private Long sizeBytes;
        private ImportStatus status;
        private String parserName;
        private String errorMessage;
        private Long uploadedByUserId;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SummaryResponse {
        private Long id;
        private String name;
        private String description;
        private Long courseId;
        private String courseName;
        private Long createdByUserId;
        private String createdByEmail;
        private int sectionCount;
        private int questionCount;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String name;
        private String description;
        private Long courseId;
        private String courseName;
        private Long createdByUserId;
        private String createdByEmail;
        private List<SectionData> sections;
        private List<QuestionData> questions;
        private List<ImportData> imports;
        private Long version;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PageResponse {
        private List<SummaryResponse> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean last;
    }
}
