package com.example.lms.service;

import com.example.lms.dto.DashboardDto;
import com.example.lms.entity.DashboardTimePeriod;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseEnrollmentRepository;
import com.example.lms.repository.LoginActivityRepository;
import com.example.lms.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DashboardService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardService.class);

    private final UserRepository userRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final LoginActivityRepository loginActivityRepository;

    public DashboardService(UserRepository userRepository,
                            CourseEnrollmentRepository enrollmentRepository,
                            LoginActivityRepository loginActivityRepository) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.loginActivityRepository = loginActivityRepository;
    }

    @Transactional(readOnly = true)
    public DashboardDto.DashboardResponse getDashboard(DashboardTimePeriod timePeriod) {
        try {
            LocalDateTime now = LocalDateTime.now();
            DashboardTimePeriod.DateRange range = timePeriod.resolve(now);
            List<DashboardDto.CountChartDataResponse> logins = mapCounts(
                    loginCounts(timePeriod.getGrouping(), range), timePeriod.getGrouping());
            List<DashboardDto.CountChartDataResponse> signups = mapCounts(
                    signupCounts(timePeriod.getGrouping(), range), timePeriod.getGrouping());

            DashboardDto.UserDistributionResponse distribution = userDistribution();
            BigDecimal completionRate = completionRate();

            return DashboardDto.DashboardResponse.builder()
                    .timePeriod(timePeriod)
                    .logins(logins)
                    .newSignups(signups)
                    // No payment/order/subscription entity currently exists. Empty/zero is safer than
                    // incorrectly treating catalog package prices as earned revenue.
                    .revenue(List.of())
                    .totalRevenue(BigDecimal.ZERO)
                    .activeLearners(userRepository.countActiveUsersByRole("LEARNER"))
                    // The current model has no persisted watch/session duration to aggregate.
                    .averageLearningTime(BigDecimal.ZERO)
                    .courseCompletionRate(completionRate)
                    .userDistribution(distribution)
                    .onlineUsers(loginActivityRepository.countOnlineUsers(now))
                    .build();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("Unable to load dashboard data.", exception);
            throw new ApiException(
                    "Unable to load dashboard data. Please try again later.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private List<Object[]> loginCounts(DashboardTimePeriod.Grouping grouping,
                                       DashboardTimePeriod.DateRange range) {
        return switch (grouping) {
            case HOUR -> loginActivityRepository.countLoginsByHour(
                    range.startInclusive(), range.endExclusive());
            case DAY -> loginActivityRepository.countLoginsByDay(
                    range.startInclusive(), range.endExclusive());
            case MONTH -> loginActivityRepository.countLoginsByMonth(
                    range.startInclusive(), range.endExclusive());
        };
    }

    private List<Object[]> signupCounts(DashboardTimePeriod.Grouping grouping,
                                        DashboardTimePeriod.DateRange range) {
        return switch (grouping) {
            case HOUR -> userRepository.countSignupsByHour(
                    range.startInclusive(), range.endExclusive());
            case DAY -> userRepository.countSignupsByDay(
                    range.startInclusive(), range.endExclusive());
            case MONTH -> userRepository.countSignupsByMonth(
                    range.startInclusive(), range.endExclusive());
        };
    }

    private List<DashboardDto.CountChartDataResponse> mapCounts(
            List<Object[]> rows, DashboardTimePeriod.Grouping grouping) {
        List<DashboardDto.CountChartDataResponse> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            int year = number(row[0]).intValue();
            int month = number(row[1]).intValue();
            String label;
            long count;
            if (grouping == DashboardTimePeriod.Grouping.MONTH) {
                label = "%04d-%02d".formatted(year, month);
                count = number(row[2]).longValue();
            } else {
                int day = number(row[2]).intValue();
                if (grouping == DashboardTimePeriod.Grouping.HOUR) {
                    label = "%04d-%02d-%02dT%02d:00".formatted(
                            year, month, day, number(row[3]).intValue());
                    count = number(row[4]).longValue();
                } else {
                    label = "%04d-%02d-%02d".formatted(year, month, day);
                    count = number(row[3]).longValue();
                }
            }
            result.add(DashboardDto.CountChartDataResponse.builder()
                    .label(label)
                    .value(count)
                    .build());
        }
        return List.copyOf(result);
    }

    private DashboardDto.UserDistributionResponse userDistribution() {
        long admins = 0;
        long instructors = 0;
        long learners = 0;
        for (Object[] row : userRepository.countUsersByRole()) {
            String role = String.valueOf(row[0]).trim().toUpperCase(Locale.ROOT);
            long count = number(row[1]).longValue();
            if ("ADMIN".equals(role) || "SUPER_ADMIN".equals(role) || "SUPERADMIN".equals(role)) {
                admins += count;
            } else if ("INSTRUCTOR".equals(role)) {
                instructors += count;
            } else if ("LEARNER".equals(role)) {
                learners += count;
            }
        }
        return DashboardDto.UserDistributionResponse.builder()
                .admins(admins)
                .instructors(instructors)
                .learners(learners)
                .build();
    }

    private BigDecimal completionRate() {
        List<Object[]> rows = enrollmentRepository.countTotalAndCompletedEnrollments();
        if (rows.isEmpty()) {
            return BigDecimal.ZERO;
        }
        Object[] counts = rows.getFirst();
        long total = number(counts[0]).longValue();
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        long completed = number(counts[1]).longValue();
        return BigDecimal.valueOf(completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private Number number(Object value) {
        return value instanceof Number number ? number : 0;
    }
}
