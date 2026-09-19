package com.example.lms.service;

import com.example.lms.dto.LearnerReportDto;
import com.example.lms.entity.LearnerReportFilter;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseEnrollmentRepository;
import com.example.lms.repository.LoginActivityRepository;
import com.example.lms.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Service
public class LearnerReportService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LearnerReportService.class);
    private static final int MAX_SEARCH_LENGTH = 100;
    private static final DateTimeFormatter EXPORT_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final UserRepository userRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final LoginActivityRepository loginActivityRepository;

    public LearnerReportService(UserRepository userRepository,
                                CourseEnrollmentRepository enrollmentRepository,
                                LoginActivityRepository loginActivityRepository) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.loginActivityRepository = loginActivityRepository;
    }

    @Transactional(readOnly = true)
    public List<LearnerReportDto.ListResponse> list(String search,
                                                    String filter,
                                                    Authentication authentication) {
        try {
            ReportCriteria criteria = criteria(search, filter, authentication);
            return findLearners(criteria).stream()
                    .map(this::toListResponse)
                    .toList();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("Unable to load learner report list.", exception);
            throw new ApiException(
                    "Unable to load the Learner Report. Please try again later.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional(readOnly = true)
    public LearnerReportDto.PreviewResponse preview(Long learnerId,
                                                    Authentication authentication) {
        try {
            if (learnerId == null || learnerId <= 0) {
                throw new ApiException("Learner ID must be a positive number.", HttpStatus.BAD_REQUEST);
            }
            Long instructorId = resolveInstructorScope(authentication);
            User learner = requireAvailableLearner(learnerId);
            assertInstructorAccess(learnerId, instructorId);

            Map<Long, LearnerMetrics> metrics = loadMetrics(List.of(learnerId), instructorId);
            Map<Long, LocalDateTime> lastLogins = loadLastLogins(List.of(learnerId));
            return toPreviewResponse(
                    learner,
                    metrics.getOrDefault(learnerId, LearnerMetrics.EMPTY),
                    lastLogins.get(learnerId));
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("Unable to load learner report preview for learner {}.", learnerId, exception);
            throw new ApiException(
                    "Unable to load the Learner Report. Please try again later.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional(readOnly = true)
    public ExportedReport export(String search,
                                 String filter,
                                 Authentication authentication) {
        try {
            ReportCriteria criteria = criteria(search, filter, authentication);
            List<User> learners = findLearners(criteria);
            List<Long> learnerIds = learners.stream().map(User::getId).toList();
            Map<Long, LearnerMetrics> metrics = loadMetrics(learnerIds, criteria.instructorId());
            Map<Long, LocalDateTime> lastLogins = loadLastLogins(learnerIds);

            StringBuilder csv = new StringBuilder();
            appendCsvRow(csv, List.of(
                    "Learner ID", "Learner Name", "Role", "Department", "Registration Date",
                    "Last Activity", "Last Login", "Completed Courses", "Incomplete Courses",
                    "Enrolled Courses", "Assigned Courses", "Average Score", "Study Time",
                    "Total Time on Platform"));
            for (User learner : learners) {
                LearnerReportDto.PreviewResponse report = toPreviewResponse(
                        learner,
                        metrics.getOrDefault(learner.getId(), LearnerMetrics.EMPTY),
                        lastLogins.get(learner.getId()));
                appendCsvRow(csv, List.of(
                        text(report.getLearnerId()),
                        text(report.getLearnerName()),
                        text(report.getRole()),
                        text(report.getDepartment()),
                        dateTime(report.getRegistrationDate()),
                        dateTime(report.getLastActivity()),
                        dateTime(report.getLastLogin()),
                        text(report.getCompletedCourses()),
                        text(report.getIncompleteCourses()),
                        text(report.getEnrolledCourses()),
                        text(report.getAssignedCourses()),
                        text(report.getAverageScore()),
                        text(report.getStudyTime()),
                        text(report.getTotalTimeOnPlatform())));
            }
            return new ExportedReport(
                    "learner-reports.csv",
                    csv.toString().getBytes(StandardCharsets.UTF_8));
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("Unable to export learner reports.", exception);
            throw new ApiException(
                    "Unable to export the Learner Report. Please try again.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private ReportCriteria criteria(String search,
                                    String filter,
                                    Authentication authentication) {
        String normalizedSearch = normalizeSearch(search);
        LearnerReportFilter selectedFilter = LearnerReportFilter.from(filter);
        Long instructorId = resolveInstructorScope(authentication);
        return new ReportCriteria(normalizedSearch, selectedFilter, instructorId);
    }

    private List<User> findLearners(ReportCriteria criteria) {
        return userRepository.findLearnersForReport(
                criteria.search(),
                criteria.filter() == null ? null : criteria.filter().name(),
                criteria.instructorId());
    }

    private String normalizeSearch(String search) {
        if (search == null) {
            return null;
        }
        String normalized = search.trim();
        if (normalized.length() > MAX_SEARCH_LENGTH) {
            throw new ApiException(
                    "Search must not exceed 100 characters.",
                    HttpStatus.BAD_REQUEST);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private Long resolveInstructorScope(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException("Authentication is required.", HttpStatus.UNAUTHORIZED);
        }
        if (hasRole(authentication, "ROLE_ADMIN")
                || hasRole(authentication, "ROLE_SUPER_ADMIN")) {
            return null;
        }
        if (!hasRole(authentication, "ROLE_INSTRUCTOR")) {
            throw new ApiException(
                    "You are not authorized to view learner reports.",
                    HttpStatus.FORBIDDEN);
        }
        User instructor = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ApiException(
                        "Logged-in instructor profile not found.",
                        HttpStatus.NOT_FOUND));
        if (!Boolean.TRUE.equals(instructor.getActive()) || !hasUserRole(instructor, "INSTRUCTOR")) {
            throw new ApiException(
                    "The logged-in instructor profile is not active.",
                    HttpStatus.FORBIDDEN);
        }
        return instructor.getId();
    }

    private User requireAvailableLearner(Long learnerId) {
        User learner = userRepository.findById(learnerId)
                .orElseThrow(this::learnerUnavailable);
        if (!Boolean.TRUE.equals(learner.getActive()) || !hasUserRole(learner, "LEARNER")) {
            throw learnerUnavailable();
        }
        return learner;
    }

    private void assertInstructorAccess(Long learnerId, Long instructorId) {
        if (instructorId != null
                && !enrollmentRepository.isLearnerAccessibleToInstructor(learnerId, instructorId)) {
            throw new ApiException(
                    "You are not authorized to view this learner report.",
                    HttpStatus.FORBIDDEN);
        }
    }

    private ApiException learnerUnavailable() {
        return new ApiException(
                "The selected learner is no longer available. Please select another learner.",
                HttpStatus.NOT_FOUND);
    }

    private Map<Long, LearnerMetrics> loadMetrics(Collection<Long> learnerIds, Long instructorId) {
        if (learnerIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, LearnerMetrics> result = new HashMap<>();
        for (Object[] row : enrollmentRepository.findLearnerReportMetrics(learnerIds, instructorId)) {
            Long learnerId = number(row[0]).longValue();
            BigDecimal averageScore = row[4] instanceof Number average
                    ? BigDecimal.valueOf(average.doubleValue()).setScale(2, RoundingMode.HALF_UP)
                    : null;
            result.put(learnerId, new LearnerMetrics(
                    number(row[1]).longValue(),
                    number(row[2]).longValue(),
                    number(row[3]).longValue(),
                    averageScore,
                    row[5] instanceof LocalDateTime completedAt ? completedAt : null));
        }
        return result;
    }

    private Map<Long, LocalDateTime> loadLastLogins(Collection<Long> learnerIds) {
        if (learnerIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, LocalDateTime> result = new HashMap<>();
        for (Object[] row : loginActivityRepository.findLastLoginsByUserIds(learnerIds)) {
            if (row[0] instanceof Number learnerId && row[1] instanceof LocalDateTime lastLogin) {
                result.put(learnerId.longValue(), lastLogin);
            }
        }
        return result;
    }

    private LearnerReportDto.ListResponse toListResponse(User learner) {
        return LearnerReportDto.ListResponse.builder()
                .learnerId(learner.getId())
                .learnerName(displayName(learner))
                .role(learner.getRole())
                // The existing User model has no department association.
                .department(null)
                .build();
    }

    private LearnerReportDto.PreviewResponse toPreviewResponse(User learner,
                                                               LearnerMetrics metrics,
                                                               LocalDateTime lastLogin) {
        LocalDateTime lastActivity = latest(lastLogin, metrics.latestCompletion());
        return LearnerReportDto.PreviewResponse.builder()
                .learnerId(learner.getId())
                .learnerName(displayName(learner))
                .role(learner.getRole())
                // These values remain null until the application persists their source data.
                .department(null)
                .registrationDate(learner.getCreatedAt())
                .lastActivity(lastActivity)
                .lastLogin(lastLogin)
                .completedCourses(metrics.completed())
                .incompleteCourses(metrics.incomplete())
                .enrolledCourses(metrics.enrolled())
                // CourseEnrollmentEntity is the application's persisted course-assignment record.
                .assignedCourses(metrics.enrolled())
                .averageScore(metrics.averageScore())
                .studyTime(null)
                .totalTimeOnPlatform(null)
                .build();
    }

    private LocalDateTime latest(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private boolean hasUserRole(User user, String role) {
        return user.getRole() != null
                && role.equalsIgnoreCase(user.getRole().replace("ROLE_", "").trim());
    }

    private String displayName(User learner) {
        String displayName = (learner.getFirstName() + " " + learner.getLastName()).trim();
        return displayName.isBlank() ? learner.getEmail() : displayName;
    }

    private void appendCsvRow(StringBuilder csv, List<String> cells) {
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append(csvCell(cells.get(index)));
        }
        csv.append("\r\n");
    }

    private String csvCell(String value) {
        String safeValue = value == null ? "" : value;
        if (!safeValue.isEmpty() && "=+-@".indexOf(safeValue.charAt(0)) >= 0) {
            safeValue = "'" + safeValue;
        }
        return '"' + safeValue.replace("\"", "\"\"") + '"';
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String dateTime(LocalDateTime value) {
        return value == null ? "" : EXPORT_DATE_TIME.format(value);
    }

    private Number number(Object value) {
        return value instanceof Number number ? number : 0;
    }

    private record ReportCriteria(String search,
                                  LearnerReportFilter filter,
                                  Long instructorId) {
    }

    private record LearnerMetrics(long enrolled,
                                  long completed,
                                  long incomplete,
                                  BigDecimal averageScore,
                                  LocalDateTime latestCompletion) {
        private static final LearnerMetrics EMPTY = new LearnerMetrics(0, 0, 0, null, null);
    }

    public record ExportedReport(String fileName, byte[] content) {
    }
}
