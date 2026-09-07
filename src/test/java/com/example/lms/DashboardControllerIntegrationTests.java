package com.example.lms;

import com.example.lms.entity.CourseEnrollmentEntity;
import com.example.lms.entity.DashboardTimePeriod;
import com.example.lms.entity.LoginActivityEntity;
import com.example.lms.entity.User;
import com.example.lms.repository.CourseEnrollmentRepository;
import com.example.lms.repository.LoginActivityRepository;
import com.example.lms.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerIntegrationTests {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CourseEnrollmentRepository enrollmentRepository;
    @Autowired private LoginActivityRepository loginActivityRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User learner;

    @BeforeEach
    void setUp() {
        loginActivityRepository.deleteAll();
        enrollmentRepository.deleteAll();
        userRepository.deleteAll();

        saveUser("admin@dashboard.test", "ADMIN", true, "test-password");
        saveUser("instructor@dashboard.test", "ROLE_INSTRUCTOR", true, "test-password");
        learner = saveUser("learner@dashboard.test", "LEARNER", true, "test-password");
        User inactiveLearner = saveUser("inactive@dashboard.test", "LEARNER", false, "test-password");

        enrollmentRepository.save(CourseEnrollmentEntity.builder()
                .courseId(100L)
                .userId(learner.getId())
                .progressPercent(100)
                .completedAt(LocalDateTime.now().minusHours(1))
                .build());
        enrollmentRepository.save(CourseEnrollmentEntity.builder()
                .courseId(101L)
                .userId(inactiveLearner.getId())
                .progressPercent(50)
                .build());

        loginActivityRepository.save(LoginActivityEntity.builder()
                .userId(learner.getId())
                .loggedInAt(LocalDateTime.now().minusMinutes(20))
                .expiresAt(LocalDateTime.now().plusMinutes(40))
                .build());
        loginActivityRepository.save(LoginActivityEntity.builder()
                .userId(learner.getId())
                .loggedInAt(LocalDateTime.now().minusHours(2))
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build());
    }

    @Test
    void dashboardDefaultsToLastWeekAndReturnsAggregateMetrics() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .with(user("admin@dashboard.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.timePeriod").value("LAST_WEEK"))
                .andExpect(jsonPath("$.data.revenue", hasSize(0)))
                .andExpect(jsonPath("$.data.totalRevenue").value(0))
                .andExpect(jsonPath("$.data.averageLearningTime").value(0))
                .andExpect(jsonPath("$.data.activeLearners").value(1))
                .andExpect(jsonPath("$.data.courseCompletionRate").value(50.0))
                .andExpect(jsonPath("$.data.userDistribution.admins").value(1))
                .andExpect(jsonPath("$.data.userDistribution.instructors").value(1))
                .andExpect(jsonPath("$.data.userDistribution.learners").value(2))
                .andExpect(jsonPath("$.data.onlineUsers").value(1));
    }

    @Test
    void todayReturnsChartReadyLoginAndSignupDataForInstructor() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .param("timePeriod", "TODAY")
                        .with(user("instructor@dashboard.test").roles("INSTRUCTOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timePeriod").value("TODAY"))
                .andExpect(jsonPath("$.data.logins", hasSize(2)))
                .andExpect(jsonPath("$.data.logins[0].label").isString())
                .andExpect(jsonPath("$.data.logins[0].value").isNumber())
                .andExpect(jsonPath("$.data.newSignups", hasSize(1)))
                .andExpect(jsonPath("$.data.newSignups[0].value").value(4));
    }

    @ParameterizedTest
    @EnumSource(DashboardTimePeriod.class)
    void everySupportedPeriodLoads(DashboardTimePeriod period) throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .param("timePeriod", period.name())
                        .with(user("admin@dashboard.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timePeriod").value(period.name()));
    }

    @Test
    void invalidPeriodIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/dashboard")
                        .param("timePeriod", "QUARTER")
                        .with(user("admin@dashboard.test").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid dashboard time period: QUARTER."));
    }

    @Test
    void unauthenticatedAndLearnerUsersCannotAccessDashboard() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/dashboard")
                        .with(user("learner@dashboard.test").roles("LEARNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void emptyDatabaseReturnsEmptyChartsAndZeroMetrics() throws Exception {
        loginActivityRepository.deleteAll();
        enrollmentRepository.deleteAll();
        userRepository.deleteAll();

        mockMvc.perform(get("/api/dashboard")
                        .param("timePeriod", "TODAY")
                        .with(user("admin@dashboard.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logins", hasSize(0)))
                .andExpect(jsonPath("$.data.newSignups", hasSize(0)))
                .andExpect(jsonPath("$.data.activeLearners").value(0))
                .andExpect(jsonPath("$.data.courseCompletionRate").value(0))
                .andExpect(jsonPath("$.data.userDistribution.admins").value(0))
                .andExpect(jsonPath("$.data.userDistribution.instructors").value(0))
                .andExpect(jsonPath("$.data.userDistribution.learners").value(0))
                .andExpect(jsonPath("$.data.onlineUsers").value(0));
    }

    @Test
    void successfulLoginPersistsTheSessionUsedByDashboard() throws Exception {
        User loginUser = saveUser(
                "login@dashboard.test",
                "ADMIN",
                true,
                passwordEncoder.encode("Password1!"));

        long before = loginActivityRepository.count();
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "login@dashboard.test",
                                  "password": "Password1!",
                                  "rememberMe": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(loginUser.getId()));

        assertThat(loginActivityRepository.count()).isEqualTo(before + 1);
    }

    private User saveUser(String email, String role, boolean active, String password) {
        return userRepository.save(User.builder()
                .firstName("Dashboard")
                .lastName("User")
                .email(email)
                .countryCode("+91")
                .phoneNumber(String.valueOf(Math.abs(email.hashCode()) % 1_000_000_000L + 1_000_000_000L))
                .password(password)
                .role(role)
                .acceptedTerms(true)
                .active(active)
                .build());
    }
}
