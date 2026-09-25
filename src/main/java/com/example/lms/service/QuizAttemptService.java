package com.example.lms.service;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.QuestionBankDto;
import com.example.lms.dto.QuizAttemptDto;
import com.example.lms.entity.QuestionBankEntity;
import com.example.lms.entity.QuizAttemptAnswerEntity;
import com.example.lms.entity.QuizAttemptEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseEnrollmentRepository;
import com.example.lms.repository.QuestionBankRepository;
import com.example.lms.repository.QuizAttemptAnswerRepository;
import com.example.lms.repository.QuizAttemptRepository;
import com.example.lms.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizAttemptService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final QuizAttemptRepository attemptRepository;
    private final QuizAttemptAnswerRepository answerRepository;
    private final QuestionBankRepository questionBankRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public QuizAttemptDto.AttemptResponse start(
            Long quizId,
            QuizAttemptDto.StartRequest request,
            AuthPrincipal principal
    ) {
        return createOrResume(quizId, request.getCourseId(), principal, false);
    }

    @Transactional
    public QuizAttemptDto.AttemptResponse retest(
            Long quizId,
            QuizAttemptDto.StartRequest request,
            AuthPrincipal principal
    ) {
        return createOrResume(quizId, request.getCourseId(), principal, true);
    }

    @Transactional(readOnly = true)
    public QuizAttemptDto.AttemptResponse getActive(Long quizId, AuthPrincipal principal) {
        requireLearner(principal);
        QuestionBankEntity quiz = getAccessibleQuiz(quizId, null, principal.getId());
        QuizAttemptEntity attempt = attemptRepository
                .findFirstByQuestionBankIdAndLearnerIdAndStatusOrderByStartedAtDesc(
                        quiz.getId(), principal.getId(), QuizAttemptEntity.Status.IN_PROGRESS)
                .orElseThrow(() -> api("No active quiz attempt was found.", HttpStatus.NOT_FOUND));
        return toAttemptResponse(attempt, loadAnswers(attempt.getId()));
    }

    @Transactional(readOnly = true)
    public QuizAttemptDto.AttemptResponse getAttempt(Long attemptId, AuthPrincipal principal) {
        QuizAttemptEntity attempt = ownedAttempt(attemptId, principal, false);
        return toAttemptResponse(attempt, loadAnswers(attemptId));
    }

    @Transactional(readOnly = true)
    public QuizAttemptDto.QuestionResponse getQuestion(
            Long attemptId,
            String questionId,
            AuthPrincipal principal
    ) {
        QuizAttemptEntity attempt = ownedAttempt(attemptId, principal, false);
        List<QuestionBankDto.QuestionData> questions = readSnapshot(attempt);
        Map<String, QuizAttemptAnswerEntity> answers = loadAnswers(attemptId);
        int index = indexOfQuestion(questions, questionId);
        return toQuestionResponse(attempt, questions, index, answers.get(questionId));
    }

    @Transactional
    public QuizAttemptDto.SavedAnswerResponse saveAnswer(
            Long attemptId,
            QuizAttemptDto.SaveAnswerRequest request,
            AuthPrincipal principal
    ) {
        QuizAttemptEntity attempt = ownedAttempt(attemptId, principal, true);
        requireInProgress(attempt);
        List<QuestionBankDto.QuestionData> questions = readSnapshot(attempt);
        QuestionBankDto.QuestionData question = questions.get(
                indexOfQuestion(questions, request.getQuestionId()));
        ValidatedAnswer validated = validateAnswer(question, request);

        QuizAttemptAnswerEntity answer = answerRepository
                .findByAttemptIdAndQuestionId(attemptId, question.getId())
                .orElseGet(QuizAttemptAnswerEntity::new);
        if (answer.getId() == null) {
            answer.setAttempt(attempt);
            answer.setQuestionId(question.getId());
        }
        answer.setSelectedOptionIdsJson(writeOptionIds(validated.selectedOptionIds()));
        answer.setTextAnswer(validated.textAnswer());
        answer.setCorrect(null);
        answer.setAwardedPoints(null);
        return toSavedAnswer(answerRepository.saveAndFlush(answer));
    }

    @Transactional
    public QuizAttemptDto.ResultResponse finish(Long attemptId, AuthPrincipal principal) {
        QuizAttemptEntity attempt = ownedAttempt(attemptId, principal, true);
        if (attempt.getStatus() == QuizAttemptEntity.Status.COMPLETED) {
            return toResultResponse(attempt, loadAnswers(attemptId));
        }
        requireInProgress(attempt);

        List<QuestionBankDto.QuestionData> questions = readSnapshot(attempt);
        Map<String, QuizAttemptAnswerEntity> answers = loadAnswers(attemptId);
        List<String> unanswered = questions.stream()
                .map(QuestionBankDto.QuestionData::getId)
                .filter(id -> !answers.containsKey(id))
                .toList();
        if (!unanswered.isEmpty()) {
            throw api("All quiz questions must be answered before finishing.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        int correctAnswers = 0;
        BigDecimal configuredPossible = ZERO;
        BigDecimal configuredEarned = ZERO;
        for (QuestionBankDto.QuestionData question : questions) {
            QuizAttemptAnswerEntity answer = answers.get(question.getId());
            boolean correct = isCorrect(question, answer);
            BigDecimal questionPoints = safePoints(question.getScore());
            configuredPossible = configuredPossible.add(questionPoints);
            if (correct) {
                correctAnswers++;
                configuredEarned = configuredEarned.add(questionPoints);
            }
            answer.setCorrect(correct);
            answer.setAwardedPoints(correct ? questionPoints : ZERO);
        }

        BigDecimal possiblePoints;
        BigDecimal earnedPoints;
        if (configuredPossible.compareTo(ZERO) > 0) {
            possiblePoints = configuredPossible;
            earnedPoints = configuredEarned;
        } else {
            possiblePoints = BigDecimal.valueOf(questions.size());
            earnedPoints = BigDecimal.valueOf(correctAnswers);
            for (QuizAttemptAnswerEntity answer : answers.values()) {
                answer.setAwardedPoints(Boolean.TRUE.equals(answer.getCorrect())
                        ? BigDecimal.ONE : ZERO);
            }
        }
        BigDecimal score = percentage(earnedPoints, possiblePoints);
        LocalDateTime submittedAt = LocalDateTime.now();
        attempt.setCorrectAnswers(correctAnswers);
        attempt.setEarnedPoints(earnedPoints);
        attempt.setPossiblePoints(possiblePoints);
        attempt.setScore(score);
        attempt.setPassed(attempt.getPassPercentage() == null
                ? null
                : score.compareTo(attempt.getPassPercentage()) >= 0);
        attempt.setSubmittedAt(submittedAt);
        attempt.setTimeSpentSeconds(Math.max(0L,
                Duration.between(attempt.getStartedAt(), submittedAt).getSeconds()));
        attempt.setStatus(QuizAttemptEntity.Status.COMPLETED);
        answerRepository.saveAll(answers.values());
        attemptRepository.saveAndFlush(attempt);
        return toResultResponse(attempt, answers);
    }

    @Transactional(readOnly = true)
    public QuizAttemptDto.ResultResponse getResult(Long attemptId, AuthPrincipal principal) {
        QuizAttemptEntity attempt = ownedAttempt(attemptId, principal, false);
        if (attempt.getStatus() != QuizAttemptEntity.Status.COMPLETED) {
            throw api("Quiz result is available only after the attempt is completed.",
                    HttpStatus.CONFLICT);
        }
        return toResultResponse(attempt, loadAnswers(attemptId));
    }

    private QuizAttemptDto.AttemptResponse createOrResume(
            Long quizId,
            Long courseId,
            AuthPrincipal principal,
            boolean retest
    ) {
        requireLearner(principal);
        User learner = userRepository.findByIdForUpdate(principal.getId())
                .orElseThrow(() -> api("Authenticated user no longer exists.",
                        HttpStatus.UNAUTHORIZED));
        if (!Boolean.TRUE.equals(learner.getActive())) {
            throw api("Learner account is inactive.", HttpStatus.FORBIDDEN);
        }
        QuestionBankEntity quiz = getAccessibleQuiz(quizId, courseId, learner.getId());
        QuizAttemptEntity active = attemptRepository
                .findFirstByQuestionBankIdAndLearnerIdAndStatusOrderByStartedAtDesc(
                        quizId, learner.getId(), QuizAttemptEntity.Status.IN_PROGRESS)
                .orElse(null);
        if (active != null) {
            return toAttemptResponse(active, loadAnswers(active.getId()));
        }

        QuizAttemptEntity previous = attemptRepository
                .findTopByQuestionBankIdAndLearnerIdOrderByAttemptNumberDesc(
                        quizId, learner.getId())
                .orElse(null);
        if (retest && (previous == null
                || previous.getStatus() != QuizAttemptEntity.Status.COMPLETED)) {
            throw api("Retest is available only after a completed attempt.",
                    HttpStatus.CONFLICT);
        }
        long priorAttempts = attemptRepository.countByQuestionBankIdAndLearnerId(
                quizId, learner.getId());
        if (quiz.getMaxAttempts() != null && priorAttempts >= quiz.getMaxAttempts()) {
            throw api("Maximum quiz attempt limit has been reached.", HttpStatus.CONFLICT);
        }

        List<QuestionBankDto.QuestionData> questions = readVisibleQuizQuestions(quiz);
        QuizAttemptEntity attempt = new QuizAttemptEntity();
        attempt.setLearner(learner);
        attempt.setQuestionBank(quiz);
        attempt.setCourse(quiz.getCourse());
        attempt.setAttemptNumber(previous == null ? 1 : previous.getAttemptNumber() + 1);
        attempt.setStatus(QuizAttemptEntity.Status.IN_PROGRESS);
        attempt.setStartedAt(LocalDateTime.now());
        attempt.setTotalQuestions(questions.size());
        attempt.setPassPercentage(quiz.getPassPercentage());
        attempt.setQuestionSnapshotJson(writeSnapshot(questions));
        attempt = attemptRepository.saveAndFlush(attempt);
        return toAttemptResponse(attempt, Map.of());
    }

    private QuestionBankEntity getAccessibleQuiz(
            Long quizId,
            Long expectedCourseId,
            Long learnerId
    ) {
        QuestionBankEntity quiz = questionBankRepository.findWithReferencesById(quizId)
                .orElseThrow(() -> api("Quiz not found.", HttpStatus.NOT_FOUND));
        if (quiz.getCourse() == null) {
            throw api("Quiz is not assigned to a course.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (expectedCourseId != null
                && !Objects.equals(quiz.getCourse().getId(), expectedCourseId)) {
            throw api("Quiz does not belong to the selected course.",
                    HttpStatus.BAD_REQUEST);
        }
        if (!enrollmentRepository.isUserEnrolledInCourse(
                learnerId, quiz.getCourse().getId())) {
            throw api("Learner is not enrolled in this course.", HttpStatus.FORBIDDEN);
        }
        return quiz;
    }

    private QuizAttemptEntity ownedAttempt(
            Long attemptId,
            AuthPrincipal principal,
            boolean forUpdate
    ) {
        requireLearner(principal);
        QuizAttemptEntity attempt = (forUpdate
                ? attemptRepository.findByIdForUpdate(attemptId)
                : attemptRepository.findWithReferencesById(attemptId))
                .orElseThrow(() -> api("Quiz attempt not found.", HttpStatus.NOT_FOUND));
        if (!Objects.equals(attempt.getLearner().getId(), principal.getId())) {
            throw api("You cannot access another learner's quiz attempt.",
                    HttpStatus.FORBIDDEN);
        }
        return attempt;
    }

    private void requireLearner(AuthPrincipal principal) {
        if (principal == null || principal.getId() == null) {
            throw api("Authentication is required.", HttpStatus.UNAUTHORIZED);
        }
        if (principal.getRole() == null
                || !"LEARNER".equalsIgnoreCase(principal.getRole())) {
            throw api("Learner access is required.", HttpStatus.FORBIDDEN);
        }
    }

    private void requireInProgress(QuizAttemptEntity attempt) {
        if (attempt.getStatus() != QuizAttemptEntity.Status.IN_PROGRESS) {
            throw api("Quiz attempt is already completed.", HttpStatus.CONFLICT);
        }
    }

    private List<QuestionBankDto.QuestionData> readVisibleQuizQuestions(
            QuestionBankEntity quiz
    ) {
        try {
            QuestionBankDto.Content content = objectMapper.readValue(
                    quiz.getContentJson(), QuestionBankDto.Content.class);
            List<QuestionBankDto.QuestionData> source = content.getQuestions() == null
                    ? List.of() : content.getQuestions();
            List<QuestionBankDto.QuestionData> questions = source.stream()
                    .filter(question -> !question.isHidden())
                    .filter(question -> question.getQuestionType() != null)
                    .toList();
            if (questions.isEmpty()) {
                throw api("Quiz has no available questions.",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            Set<String> questionIds = new HashSet<>();
            for (QuestionBankDto.QuestionData question : questions) {
                if (question.getId() == null || question.getId().isBlank()
                        || !questionIds.add(question.getId())) {
                    throw api("Quiz contains invalid or duplicate question IDs.",
                            HttpStatus.UNPROCESSABLE_ENTITY);
                }
            }
            return new ArrayList<>(questions);
        } catch (JsonProcessingException ex) {
            throw api("Quiz question data could not be loaded.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private List<QuestionBankDto.QuestionData> readSnapshot(QuizAttemptEntity attempt) {
        try {
            return objectMapper.readValue(
                    attempt.getQuestionSnapshotJson(),
                    new TypeReference<List<QuestionBankDto.QuestionData>>() { });
        } catch (JsonProcessingException ex) {
            throw api("Quiz attempt question data could not be loaded.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String writeSnapshot(List<QuestionBankDto.QuestionData> questions) {
        try {
            return objectMapper.writeValueAsString(questions);
        } catch (JsonProcessingException ex) {
            throw api("Quiz attempt could not be started.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, QuizAttemptAnswerEntity> loadAnswers(Long attemptId) {
        return answerRepository.findAllByAttemptIdOrderByIdAsc(attemptId).stream()
                .collect(Collectors.toMap(
                        QuizAttemptAnswerEntity::getQuestionId,
                        Function.identity(),
                        (first, ignored) -> first,
                        HashMap::new
                ));
    }

    private int indexOfQuestion(
            List<QuestionBankDto.QuestionData> questions,
            String questionId
    ) {
        for (int index = 0; index < questions.size(); index++) {
            if (Objects.equals(questions.get(index).getId(), questionId)) {
                return index;
            }
        }
        throw api("Question does not belong to this quiz attempt.",
                HttpStatus.NOT_FOUND);
    }

    private ValidatedAnswer validateAnswer(
            QuestionBankDto.QuestionData question,
            QuizAttemptDto.SaveAnswerRequest request
    ) {
        List<String> rawIds = request.getSelectedOptionIds() == null
                ? List.of() : request.getSelectedOptionIds();
        if (rawIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw api("Selected option IDs must not be blank.", HttpStatus.BAD_REQUEST);
        }
        LinkedHashSet<String> selected = new LinkedHashSet<>(rawIds);
        if (selected.size() != rawIds.size()) {
            throw api("Selected option IDs must not contain duplicates.",
                    HttpStatus.BAD_REQUEST);
        }

        if (question.getQuestionType() == QuestionBankDto.QuestionType.SHORT_TEXT) {
            if (!selected.isEmpty()) {
                throw api("Text questions do not accept selected options.",
                        HttpStatus.BAD_REQUEST);
            }
            String text = request.getTextAnswer();
            if (text == null || text.isBlank()) {
                throw api("Text answer is required.", HttpStatus.BAD_REQUEST);
            }
            if (text.length() > 500) {
                throw api("Text answer must not exceed 500 characters.",
                        HttpStatus.BAD_REQUEST);
            }
            return new ValidatedAnswer(List.of(), text);
        }

        if (request.getTextAnswer() != null && !request.getTextAnswer().isBlank()) {
            throw api("Choice questions do not accept a text answer.",
                    HttpStatus.BAD_REQUEST);
        }
        if (question.getQuestionType() == QuestionBankDto.QuestionType.SINGLE_ANSWER
                && selected.size() != 1) {
            throw api("Single-choice questions require exactly one selected option.",
                    HttpStatus.BAD_REQUEST);
        }
        if (question.getQuestionType() == QuestionBankDto.QuestionType.MULTIPLE_ANSWER
                && selected.isEmpty()) {
            throw api("Multiple-choice questions require at least one selected option.",
                    HttpStatus.BAD_REQUEST);
        }
        Set<String> validOptionIds = safeOptions(question).stream()
                .map(QuestionBankDto.OptionData::getId)
                .collect(Collectors.toSet());
        if (!validOptionIds.containsAll(selected)) {
            throw api("One or more selected options do not belong to this question.",
                    HttpStatus.BAD_REQUEST);
        }
        return new ValidatedAnswer(new ArrayList<>(selected), null);
    }

    private boolean isCorrect(
            QuestionBankDto.QuestionData question,
            QuizAttemptAnswerEntity answer
    ) {
        if (question.getQuestionType() == QuestionBankDto.QuestionType.SHORT_TEXT) {
            String expected = question.getTextAnswer() == null
                    ? "" : question.getTextAnswer().trim();
            String actual = answer.getTextAnswer() == null
                    ? "" : answer.getTextAnswer().trim();
            return !expected.isBlank() && expected.equalsIgnoreCase(actual);
        }
        Set<String> expected = safeOptions(question).stream()
                .filter(QuestionBankDto.OptionData::isCorrect)
                .map(QuestionBankDto.OptionData::getId)
                .collect(Collectors.toSet());
        return expected.equals(new HashSet<>(readOptionIds(answer)));
    }

    private List<QuestionBankDto.OptionData> safeOptions(
            QuestionBankDto.QuestionData question
    ) {
        return question.getOptions() == null ? List.of() : question.getOptions();
    }

    private BigDecimal safePoints(BigDecimal points) {
        return points == null || points.compareTo(ZERO) < 0 ? ZERO : points;
    }

    private BigDecimal percentage(BigDecimal earned, BigDecimal possible) {
        if (possible.compareTo(ZERO) == 0) {
            return ZERO.setScale(2);
        }
        return earned.multiply(ONE_HUNDRED)
                .divide(possible, 2, RoundingMode.HALF_UP);
    }

    private String writeOptionIds(List<String> optionIds) {
        try {
            return objectMapper.writeValueAsString(optionIds);
        } catch (JsonProcessingException ex) {
            throw api("Quiz answer could not be saved.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private List<String> readOptionIds(QuizAttemptAnswerEntity answer) {
        try {
            return objectMapper.readValue(
                    answer.getSelectedOptionIdsJson(),
                    new TypeReference<List<String>>() { });
        } catch (JsonProcessingException ex) {
            throw api("Saved quiz answer could not be loaded.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private QuizAttemptDto.SavedAnswerResponse toSavedAnswer(
            QuizAttemptAnswerEntity answer
    ) {
        return QuizAttemptDto.SavedAnswerResponse.builder()
                .questionId(answer.getQuestionId())
                .selectedOptionIds(readOptionIds(answer))
                .textAnswer(answer.getTextAnswer())
                .savedAt(answer.getUpdatedAt())
                .build();
    }

    private QuizAttemptDto.QuestionResponse toQuestionResponse(
            QuizAttemptEntity attempt,
            List<QuestionBankDto.QuestionData> questions,
            int index,
            QuizAttemptAnswerEntity answer
    ) {
        QuestionBankDto.QuestionData question = questions.get(index);
        List<QuizAttemptDto.OptionResponse> options = safeOptions(question).stream()
                .map(option -> QuizAttemptDto.OptionResponse.builder()
                        .id(option.getId())
                        .text(option.getOptionText())
                        .position(option.getPosition())
                        .build())
                .toList();
        return QuizAttemptDto.QuestionResponse.builder()
                .attemptId(attempt.getId())
                .quizId(attempt.getQuestionBank().getId())
                .questionId(question.getId())
                .questionNumber(index + 1)
                .totalQuestions(questions.size())
                .questionText(question.getQuestionText())
                .questionType(question.getQuestionType())
                .options(options)
                .savedAnswer(answer == null ? null : toSavedAnswer(answer))
                .attemptStatus(attempt.getStatus())
                .previousQuestionId(index == 0 ? null : questions.get(index - 1).getId())
                .nextQuestionId(index + 1 >= questions.size()
                        ? null : questions.get(index + 1).getId())
                .build();
    }

    private QuizAttemptDto.AttemptResponse toAttemptResponse(
            QuizAttemptEntity attempt,
            Map<String, QuizAttemptAnswerEntity> answers
    ) {
        List<QuestionBankDto.QuestionData> questions = readSnapshot(attempt);
        List<QuizAttemptDto.SavedAnswerResponse> savedAnswers = questions.stream()
                .map(question -> answers.get(question.getId()))
                .filter(Objects::nonNull)
                .map(this::toSavedAnswer)
                .toList();
        int currentIndex = 0;
        for (int index = 0; index < questions.size(); index++) {
            if (!answers.containsKey(questions.get(index).getId())) {
                currentIndex = index;
                break;
            }
            currentIndex = index;
        }
        return QuizAttemptDto.AttemptResponse.builder()
                .attemptId(attempt.getId())
                .quizId(attempt.getQuestionBank().getId())
                .quizName(attempt.getQuestionBank().getName())
                .courseId(attempt.getCourse().getId())
                .attemptNumber(attempt.getAttemptNumber())
                .status(attempt.getStatus())
                .startedAt(attempt.getStartedAt())
                .submittedAt(attempt.getSubmittedAt())
                .totalQuestions(questions.size())
                .answeredQuestions(savedAnswers.size())
                .savedAnswers(savedAnswers)
                .currentQuestion(toQuestionResponse(
                        attempt, questions, currentIndex,
                        answers.get(questions.get(currentIndex).getId())))
                .build();
    }

    private QuizAttemptDto.ResultResponse toResultResponse(
            QuizAttemptEntity attempt,
            Map<String, QuizAttemptAnswerEntity> answers
    ) {
        List<QuestionBankDto.QuestionData> questions = readSnapshot(attempt);
        List<QuizAttemptDto.QuestionReviewResponse> reviews = questions.stream()
                .map(question -> toReview(question, answers.get(question.getId())))
                .toList();
        return QuizAttemptDto.ResultResponse.builder()
                .attemptId(attempt.getId())
                .quizId(attempt.getQuestionBank().getId())
                .quizName(attempt.getQuestionBank().getName())
                .courseId(attempt.getCourse().getId())
                .attemptNumber(attempt.getAttemptNumber())
                .status(attempt.getStatus())
                .correctAnswers(attempt.getCorrectAnswers())
                .totalQuestions(attempt.getTotalQuestions())
                .earnedPoints(attempt.getEarnedPoints())
                .possiblePoints(attempt.getPossiblePoints())
                .score(attempt.getScore())
                .passPercentage(attempt.getPassPercentage())
                .passed(attempt.getPassed())
                .timeSpentSeconds(attempt.getTimeSpentSeconds())
                .startedAt(attempt.getStartedAt())
                .submittedAt(attempt.getSubmittedAt())
                .nextUnitAllowed(Boolean.TRUE.equals(attempt.getPassed()))
                .questions(reviews)
                .build();
    }

    private QuizAttemptDto.QuestionReviewResponse toReview(
            QuestionBankDto.QuestionData question,
            QuizAttemptAnswerEntity answer
    ) {
        QuizAttemptDto.CorrectAnswerResponse correctAnswer = null;
        if (answer != null && Boolean.FALSE.equals(answer.getCorrect())) {
            if (question.getQuestionType() == QuestionBankDto.QuestionType.SHORT_TEXT) {
                correctAnswer = QuizAttemptDto.CorrectAnswerResponse.builder()
                        .textAnswer(question.getTextAnswer())
                        .selectedOptionIds(List.of())
                        .build();
            } else {
                correctAnswer = QuizAttemptDto.CorrectAnswerResponse.builder()
                        .selectedOptionIds(safeOptions(question).stream()
                                .filter(QuestionBankDto.OptionData::isCorrect)
                                .map(QuestionBankDto.OptionData::getId)
                                .toList())
                        .build();
            }
        }
        return QuizAttemptDto.QuestionReviewResponse.builder()
                .questionId(question.getId())
                .questionText(question.getQuestionText())
                .questionType(question.getQuestionType())
                .options(safeOptions(question).stream()
                        .map(option -> QuizAttemptDto.OptionResponse.builder()
                                .id(option.getId())
                                .text(option.getOptionText())
                                .position(option.getPosition())
                                .build())
                        .toList())
                .learnerAnswer(answer == null ? null : toSavedAnswer(answer))
                .correct(answer == null ? null : answer.getCorrect())
                .awardedPoints(answer == null ? null : answer.getAwardedPoints())
                .correctAnswer(correctAnswer)
                .build();
    }

    private ApiException api(String message, HttpStatus status) {
        return new ApiException(message, status);
    }

    private record ValidatedAnswer(List<String> selectedOptionIds, String textAnswer) {
    }
}
