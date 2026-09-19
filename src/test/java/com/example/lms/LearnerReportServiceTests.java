package com.example.lms;

import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseEnrollmentRepository;
import com.example.lms.repository.LoginActivityRepository;
import com.example.lms.repository.UserRepository;
import com.example.lms.service.LearnerReportService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LearnerReportServiceTests {

    @Test
    void exportFailureUsesTheFeatureSpecificErrorContract() {
        UserRepository userRepository = mock(UserRepository.class);
        CourseEnrollmentRepository enrollmentRepository = mock(CourseEnrollmentRepository.class);
        LoginActivityRepository loginActivityRepository = mock(LoginActivityRepository.class);
        LearnerReportService service = new LearnerReportService(
                userRepository, enrollmentRepository, loginActivityRepository);
        var authentication = new UsernamePasswordAuthenticationToken(
                "admin@reports.test",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        when(userRepository.findLearnersForReport(null, null, null))
                .thenThrow(new DataAccessResourceFailureException("Database unavailable"));

        assertThatThrownBy(() -> service.export(null, null, authentication))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.getStatus().value()).isEqualTo(500);
                    assertThat(apiException.getMessage()).isEqualTo(
                            "Unable to export the Learner Report. Please try again.");
                });
    }
}
