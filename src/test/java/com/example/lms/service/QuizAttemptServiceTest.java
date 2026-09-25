package com.example.lms.service;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.QuestionBankDto;
import com.example.lms.dto.QuizAttemptDto;
import com.example.lms.entity.CourseEnrollmentEntity;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.QuestionBankEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseEnrollmentRepository;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.QuestionBankRepository;
import com.example.lms.repository.QuizAttemptAnswerRepository;
import com.example.lms.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class QuizAttemptServiceTest {

    @Autowired
    private QuizAttemptService service;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseEnrollmentRepository enrollmentRepository;
    @Autowired
    private QuestionBankRepository questionBankRepository;
    @Autowired
    private QuizAttemptAnswerRepository answerRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private User learner;
    private CourseEntity course;
    private QuestionBankEntity quiz;
    private AuthPrincipal principal;

    @BeforeEach
    void setUp() throws Exception {
        User instructor = userRepository.save(user(
                "instructor-attempt@example.com", "INSTRUCTOR", "1111111111"));
        learner = userRepository.save(user(
                "learner-attempt@example.com", "LEARNER", "2222222222"));
        principal = new AuthPrincipal(learner.getId(), learner.getEmail(), learner.getRole());
        course = courseRepository.save(CourseEntity.builder()
                .name("Attempt Quiz Course")
                .status("Active")
                .build());
        enrollmentRepository.save(CourseEnrollmentEntity.builder()
                .courseId(course.getId())
                .userId(learner.getId())
                .status("In Progress")
                .build());

        QuestionBankDto.Content content = QuestionBankDto.Content.builder()
                .questions(new ArrayList<>(List.of(
                        singleQuestion(),
                        multipleQuestion(),
                        textQuestion()
                )))
                .build();
        quiz = new QuestionBankEntity();
        quiz.setName("Course Assessment");
        quiz.setCourse(course);
        quiz.setCreatedBy(instructor);
        quiz.setPassPercentage(new BigDecimal("70.00"));
        quiz.setContentJson(objectMapper.writeValueAsString(content));
        quiz = questionBankRepository.saveAndFlush(quiz);
    }

    @Test
    void startsQuizAndResumesSameActiveAttempt() {
        QuizAttemptDto.AttemptResponse first = start();
        QuizAttemptDto.AttemptResponse resumed = start();

        assertEquals(first.getAttemptId(), resumed.getAttemptId());
        assertEquals(1, first.getAttemptNumber());
        assertEquals(3, first.getTotalQuestions());
        assertEquals("q1", first.getCurrentQuestion().getQuestionId());
        assertEquals(0, resumed.getAnsweredQuestions());
    }

    @Test
    void validatesRoleQuizCourseAndEnrollment() {
        ApiException roleError = assertThrows(ApiException.class, () ->
                service.start(quiz.getId(), startRequest(course.getId()),
                        new AuthPrincipal(99L, "admin@example.com", "ADMIN")));
        assertEquals(HttpStatus.FORBIDDEN, roleError.getStatus());

        ApiException quizError = assertThrows(ApiException.class, () ->
                service.start(999999L, startRequest(course.getId()), principal));
        assertEquals(HttpStatus.NOT_FOUND, quizError.getStatus());

        ApiException courseError = assertThrows(ApiException.class, () ->
                service.start(quiz.getId(), startRequest(course.getId() + 1), principal));
        assertEquals(HttpStatus.BAD_REQUEST, courseError.getStatus());

        User other = userRepository.save(user(
                "unenrolled@example.com", "LEARNER", "3333333333"));
        ApiException enrollmentError = assertThrows(ApiException.class, () ->
                service.start(quiz.getId(), startRequest(course.getId()),
                        new AuthPrincipal(other.getId(), other.getEmail(), other.getRole())));
        assertEquals(HttpStatus.FORBIDDEN, enrollmentError.getStatus());
    }

    @Test
    void savesAndUpdatesSingleAnswerWithoutDuplicate() {
        Long attemptId = start().getAttemptId();
        service.saveAnswer(attemptId, answer("q1", List.of("q1-a"), null), principal);
        QuizAttemptDto.SavedAnswerResponse updated = service.saveAnswer(
                attemptId, answer("q1", List.of("q1-b"), null), principal);

        assertEquals(List.of("q1-b"), updated.getSelectedOptionIds());
        assertEquals(1, answerRepository.findAllByAttemptIdOrderByIdAsc(attemptId).size());
    }

    @Test
    void validatesSingleMultipleTextAndOptionOwnership() {
        Long attemptId = start().getAttemptId();
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.saveAnswer(
                attemptId, answer("q1", List.of("q1-a", "q1-b"), null), principal));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.saveAnswer(
                attemptId, answer("q2", List.of(), null), principal));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.saveAnswer(
                attemptId, answer("q1", List.of("q2-a"), null), principal));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.saveAnswer(
                attemptId, answer("q3", List.of(), "   "), principal));
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.saveAnswer(
                attemptId, answer("q3", List.of(), "x".repeat(501)), principal));

        service.saveAnswer(attemptId,
                answer("q2", List.of("q2-a", "q2-b"), null), principal);
        service.saveAnswer(attemptId, answer("q3", List.of(), "Spring"), principal);
        assertEquals(2, answerRepository.findAllByAttemptIdOrderByIdAsc(attemptId).size());
    }

    @Test
    void restoresSavedAnswerAndProvidesPreviousAndNextNavigation() {
        Long attemptId = start().getAttemptId();
        service.saveAnswer(attemptId, answer("q1", List.of("q1-a"), null), principal);

        QuizAttemptDto.AttemptResponse restored = service.getActive(quiz.getId(), principal);
        assertEquals(1, restored.getAnsweredQuestions());
        assertEquals("q2", restored.getCurrentQuestion().getQuestionId());

        QuizAttemptDto.QuestionResponse first =
                service.getQuestion(attemptId, "q1", principal);
        QuizAttemptDto.QuestionResponse second =
                service.getQuestion(attemptId, "q2", principal);
        assertEquals(List.of("q1-a"), first.getSavedAnswer().getSelectedOptionIds());
        assertEquals("q2", first.getNextQuestionId());
        assertEquals("q1", second.getPreviousQuestionId());
    }

    @Test
    void requiresEveryAnswerBeforeFinish() {
        Long attemptId = start().getAttemptId();
        service.saveAnswer(attemptId, answer("q1", List.of("q1-a"), null), principal);
        assertStatus(HttpStatus.UNPROCESSABLE_ENTITY,
                () -> service.finish(attemptId, principal));
    }

    @Test
    void calculatesScorePassAndMakesFinishIdempotent() {
        Long attemptId = start().getAttemptId();
        answerAllCorrect(attemptId);

        QuizAttemptDto.ResultResponse first = service.finish(attemptId, principal);
        QuizAttemptDto.ResultResponse duplicate = service.finish(attemptId, principal);

        assertEquals(3, first.getCorrectAnswers());
        assertEquals(new BigDecimal("100.00"), first.getScore());
        assertTrue(first.getPassed());
        assertTrue(first.isNextUnitAllowed());
        assertNotNull(first.getSubmittedAt());
        assertNotNull(first.getTimeSpentSeconds());
        assertEquals(first.getSubmittedAt(), duplicate.getSubmittedAt());
        assertEquals(first.getScore(), service.getResult(attemptId, principal).getScore());
    }

    @Test
    void returnsCorrectAnswersOnlyInCompletedReview() {
        Long attemptId = start().getAttemptId();
        service.saveAnswer(attemptId, answer("q1", List.of("q1-b"), null), principal);
        service.saveAnswer(attemptId, answer("q2", List.of("q2-a"), null), principal);
        service.saveAnswer(attemptId, answer("q3", List.of(), "Wrong"), principal);

        QuizAttemptDto.ResultResponse result = service.finish(attemptId, principal);
        assertFalse(result.getPassed());
        assertEquals(new BigDecimal("0.00"), result.getScore());
        assertTrue(result.getQuestions().stream()
                .allMatch(review -> review.getCorrectAnswer() != null));
    }

    @Test
    void rejectsAnotherLearnersAttemptAccessAndModification() {
        Long attemptId = start().getAttemptId();
        User other = userRepository.save(user(
                "other-learner@example.com", "LEARNER", "4444444444"));
        AuthPrincipal otherPrincipal =
                new AuthPrincipal(other.getId(), other.getEmail(), other.getRole());

        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.getAttempt(attemptId, otherPrincipal));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.saveAnswer(
                attemptId, answer("q1", List.of("q1-a"), null), otherPrincipal));
    }

    @Test
    void retestIncrementsAttemptAndPreservesCompletedResult() {
        Long firstAttemptId = start().getAttemptId();
        answerAllCorrect(firstAttemptId);
        QuizAttemptDto.ResultResponse firstResult = service.finish(firstAttemptId, principal);

        QuizAttemptDto.AttemptResponse retest = service.retest(
                quiz.getId(), startRequest(course.getId()), principal);
        assertEquals(2, retest.getAttemptNumber());
        assertNotEquals(firstAttemptId, retest.getAttemptId());
        assertEquals(0, retest.getAnsweredQuestions());
        assertEquals(firstResult.getSubmittedAt(),
                service.getResult(firstAttemptId, principal).getSubmittedAt());
    }

    @Test
    void enforcesConfiguredAttemptLimit() {
        quiz.setMaxAttempts(1);
        questionBankRepository.saveAndFlush(quiz);
        Long attemptId = start().getAttemptId();
        answerAllCorrect(attemptId);
        service.finish(attemptId, principal);

        assertStatus(HttpStatus.CONFLICT, () -> service.retest(
                quiz.getId(), startRequest(course.getId()), principal));
    }

    private QuizAttemptDto.AttemptResponse start() {
        return service.start(quiz.getId(), startRequest(course.getId()), principal);
    }

    private QuizAttemptDto.StartRequest startRequest(Long courseId) {
        return QuizAttemptDto.StartRequest.builder().courseId(courseId).build();
    }

    private QuizAttemptDto.SaveAnswerRequest answer(
            String questionId,
            List<String> optionIds,
            String text
    ) {
        return QuizAttemptDto.SaveAnswerRequest.builder()
                .questionId(questionId)
                .selectedOptionIds(optionIds)
                .textAnswer(text)
                .build();
    }

    private void answerAllCorrect(Long attemptId) {
        service.saveAnswer(attemptId, answer("q1", List.of("q1-a"), null), principal);
        service.saveAnswer(attemptId,
                answer("q2", List.of("q2-a", "q2-b"), null), principal);
        service.saveAnswer(attemptId, answer("q3", List.of(), "spring"), principal);
    }

    private void assertStatus(HttpStatus expected, Runnable action) {
        ApiException exception = assertThrows(ApiException.class, action::run);
        assertEquals(expected, exception.getStatus());
    }

    private QuestionBankDto.QuestionData singleQuestion() {
        return QuestionBankDto.QuestionData.builder()
                .id("q1")
                .questionType(QuestionBankDto.QuestionType.SINGLE_ANSWER)
                .questionText("Choose A")
                .score(new BigDecimal("30"))
                .position(1)
                .options(new ArrayList<>(List.of(
                        option("q1-a", "A", true, 1),
                        option("q1-b", "B", false, 2))))
                .build();
    }

    private QuestionBankDto.QuestionData multipleQuestion() {
        return QuestionBankDto.QuestionData.builder()
                .id("q2")
                .questionType(QuestionBankDto.QuestionType.MULTIPLE_ANSWER)
                .questionText("Choose A and B")
                .score(new BigDecimal("30"))
                .position(2)
                .options(new ArrayList<>(List.of(
                        option("q2-a", "A", true, 1),
                        option("q2-b", "B", true, 2),
                        option("q2-c", "C", false, 3))))
                .build();
    }

    private QuestionBankDto.QuestionData textQuestion() {
        return QuestionBankDto.QuestionData.builder()
                .id("q3")
                .questionType(QuestionBankDto.QuestionType.SHORT_TEXT)
                .questionText("Framework name")
                .textAnswer("Spring")
                .score(new BigDecimal("40"))
                .position(3)
                .build();
    }

    private QuestionBankDto.OptionData option(
            String id,
            String text,
            boolean correct,
            int position
    ) {
        return QuestionBankDto.OptionData.builder()
                .id(id)
                .optionText(text)
                .correct(correct)
                .position(position)
                .build();
    }

    private User user(String email, String role, String phone) {
        return User.builder()
                .firstName("Quiz")
                .lastName("User")
                .email(email)
                .countryCode("+1")
                .phoneNumber(phone)
                .password("encoded-password")
                .role(role)
                .acceptedTerms(true)
                .active(true)
                .build();
    }
}
