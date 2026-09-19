package com.example.lms;

import com.example.lms.dto.CourseReportDto;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.UserRepository;
import com.example.lms.service.CourseReportService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseReportServiceTests {
    @Test
    void listFailureUsesTheRequiredUnexpectedErrorContract() {
        CourseRepository courseRepository = failingRepository();
        CourseReportService service = new CourseReportService(
                courseRepository, mock(UserRepository.class));

        assertThatThrownBy(() -> service.list(new CourseReportDto.FilterRequest(), admin()))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertApiException(
                        exception,
                        "Something went wrong. Please try again later."));
    }

    @Test
    void exportFailureUsesTheFeatureSpecificErrorContract() {
        CourseRepository courseRepository = failingRepository();
        CourseReportService service = new CourseReportService(
                courseRepository, mock(UserRepository.class));

        assertThatThrownBy(() -> service.exportExcel(
                        new CourseReportDto.FilterRequest(), admin()))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertApiException(
                        exception,
                        "Unable to export the Course Report. Please try again."));
    }

    private CourseRepository failingRepository() {
        CourseRepository courseRepository = mock(CourseRepository.class);
        when(courseRepository.findCourseReportAggregates(null, null, null, null, null))
                .thenThrow(new DataAccessResourceFailureException("Database unavailable"));
        return courseRepository;
    }

    private UsernamePasswordAuthenticationToken admin() {
        return new UsernamePasswordAuthenticationToken(
                "admin@course-reports.test",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private void assertApiException(Throwable exception, String message) {
        ApiException apiException = (ApiException) exception;
        assertThat(apiException.getStatus().value()).isEqualTo(500);
        assertThat(apiException.getMessage()).isEqualTo(message);
    }
}
