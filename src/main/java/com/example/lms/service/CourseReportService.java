package com.example.lms.service;

import com.example.lms.dto.CourseReportDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class CourseReportService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CourseReportService.class);
    private static final int MAX_SEARCH_LENGTH = 100;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final List<String> EXPORT_HEADERS = List.of(
            "Course", "Course Code", "Category", "Enrollments", "Completion Rate",
            "Completed Learners", "Learners Not Started", "Total Time Spent",
            "Certificates Issued");

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public CourseReportService(CourseRepository courseRepository,
                               UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CourseReportDto.ListResponse> list(
            CourseReportDto.FilterRequest request,
            Authentication authentication) {
        try {
            ReportCriteria criteria = criteria(request, authentication);
            return loadReports(null, criteria).stream()
                    .map(this::toListResponse)
                    .toList();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("Unable to load course reports.", exception);
            throw unexpectedError();
        }
    }

    @Transactional(readOnly = true)
    public CourseReportDto.OverviewResponse overview(
            Long courseId,
            Authentication authentication) {
        try {
            Long instructorId = resolveInstructorScope(authentication);
            CourseEntity course = requireAvailableCourse(courseId);
            assertInstructorOwnsCourse(course, instructorId);
            ReportCriteria criteria = ReportCriteria.unfiltered(instructorId);
            List<CourseMetrics> reports = loadReports(courseId, criteria);
            if (reports.isEmpty()) {
                throw courseUnavailable();
            }
            return toOverviewResponse(reports.getFirst());
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("Unable to load course overview for course {}.", courseId, exception);
            throw unexpectedError();
        }
    }

    @Transactional(readOnly = true)
    public ExportedReport exportCsv(
            CourseReportDto.FilterRequest request,
            Authentication authentication) {
        try {
            List<CourseMetrics> reports = loadReports(null, criteria(request, authentication));
            StringBuilder csv = new StringBuilder();
            appendCsvRow(csv, EXPORT_HEADERS);
            for (CourseMetrics report : reports) {
                appendCsvRow(csv, exportValues(report).stream()
                        .map(this::text)
                        .toList());
            }
            return new ExportedReport(
                    "course-reports.csv",
                    "text/csv;charset=UTF-8",
                    csv.toString().getBytes(StandardCharsets.UTF_8));
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("Unable to export course reports as CSV.", exception);
            throw exportError();
        }
    }

    @Transactional(readOnly = true)
    public ExportedReport exportExcel(
            CourseReportDto.FilterRequest request,
            Authentication authentication) {
        try {
            List<CourseMetrics> reports = loadReports(null, criteria(request, authentication));
            List<List<Object>> rows = new ArrayList<>();
            rows.add(new ArrayList<>(EXPORT_HEADERS));
            reports.stream().map(this::exportValues).forEach(rows::add);
            return new ExportedReport(
                    "course-reports.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    createXlsx(rows));
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            LOGGER.error("Unable to export course reports as Excel.", exception);
            throw exportError();
        }
    }

    private List<CourseMetrics> loadReports(Long courseId, ReportCriteria criteria) {
        return courseRepository.findCourseReportAggregates(
                        courseId,
                        criteria.instructorId(),
                        criteria.search(),
                        criteria.categoryId(),
                        criteria.category()).stream()
                .map(this::toMetrics)
                .filter(metrics -> matchesNumericFilters(metrics, criteria))
                .toList();
    }

    private CourseMetrics toMetrics(Object[] row) {
        long enrollments = number(row[4]).longValue();
        long completed = number(row[5]).longValue();
        BigDecimal completionRate = enrollments == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(completed)
                        .multiply(ONE_HUNDRED)
                        .divide(BigDecimal.valueOf(enrollments), 2, RoundingMode.HALF_UP);
        BigDecimal averageProgress = row[8] instanceof Number average
                ? BigDecimal.valueOf(average.doubleValue()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        return new CourseMetrics(
                number(row[0]).longValue(),
                Objects.toString(row[1], ""),
                row[2] == null ? null : String.valueOf(row[2]),
                Objects.toString(row[3], ""),
                enrollments,
                completed,
                number(row[6]).longValue(),
                number(row[7]).longValue(),
                completionRate,
                averageProgress);
    }

    private CourseReportDto.ListResponse toListResponse(CourseMetrics metrics) {
        return CourseReportDto.ListResponse.builder()
                .courseId(metrics.courseId())
                .courseTitle(metrics.courseTitle())
                .courseCode(metrics.courseCode())
                .category(metrics.category())
                .enrollments(metrics.enrollments())
                .completionRate(metrics.completionRate())
                .completedLearners(metrics.completedLearners())
                .learnersNotStarted(metrics.learnersNotStarted())
                .build();
    }

    private CourseReportDto.OverviewResponse toOverviewResponse(CourseMetrics metrics) {
        return CourseReportDto.OverviewResponse.builder()
                .courseId(metrics.courseId())
                .courseTitle(metrics.courseTitle())
                .courseCode(metrics.courseCode())
                .category(metrics.category())
                .enrollments(metrics.enrollments())
                .learners(metrics.enrollments())
                .completionRate(metrics.completionRate())
                .completedLearners(metrics.completedLearners())
                .learnersInProgress(metrics.learnersInProgress())
                .learnersNotStarted(metrics.learnersNotStarted())
                .averageUserProgress(metrics.averageUserProgress())
                // No persisted learning-duration or certificate source exists in this project.
                .totalTimeSpent(null)
                .certificatesIssued(null)
                .build();
    }

    private List<Object> exportValues(CourseMetrics metrics) {
        return List.of(
                metrics.courseTitle(),
                nullable(metrics.courseCode()),
                metrics.category(),
                metrics.enrollments(),
                metrics.completionRate(),
                metrics.completedLearners(),
                metrics.learnersNotStarted(),
                "",
                "");
    }

    private Object nullable(Object value) {
        return value == null ? "" : value;
    }

    private boolean matchesNumericFilters(CourseMetrics metrics, ReportCriteria criteria) {
        return matchesLong(
                    metrics.enrollments(),
                    criteria.enrollments(),
                    criteria.minEnrollments(),
                    criteria.maxEnrollments())
                && matchesDecimal(
                    metrics.completionRate(),
                    criteria.completionRate(),
                    criteria.minCompletionRate(),
                    criteria.maxCompletionRate())
                && matchesLong(
                    metrics.completedLearners(),
                    criteria.completedLearners(),
                    criteria.minCompletedLearners(),
                    criteria.maxCompletedLearners())
                && matchesLong(
                    metrics.learnersNotStarted(),
                    criteria.learnersNotStarted(),
                    criteria.minLearnersNotStarted(),
                    criteria.maxLearnersNotStarted());
    }

    private boolean matchesLong(long actual, Long exact, Long minimum, Long maximum) {
        return (exact == null || actual == exact)
                && (minimum == null || actual >= minimum)
                && (maximum == null || actual <= maximum);
    }

    private boolean matchesDecimal(
            BigDecimal actual,
            BigDecimal exact,
            BigDecimal minimum,
            BigDecimal maximum) {
        return (exact == null || actual.compareTo(exact) == 0)
                && (minimum == null || actual.compareTo(minimum) >= 0)
                && (maximum == null || actual.compareTo(maximum) <= 0);
    }

    private ReportCriteria criteria(
            CourseReportDto.FilterRequest request,
            Authentication authentication) {
        CourseReportDto.FilterRequest filters = request == null
                ? new CourseReportDto.FilterRequest()
                : request;
        String search = normalizeText(filters.getSearch());
        if (search != null && search.length() > MAX_SEARCH_LENGTH) {
            throw new ApiException(
                    "Search must not exceed 100 characters.",
                    HttpStatus.BAD_REQUEST);
        }
        String category = normalizeText(filters.getCategory());
        if (category != null && category.length() > 100) {
            throw new ApiException(
                    "Category must not exceed 100 characters.",
                    HttpStatus.BAD_REQUEST);
        }

        validateNonNegative("Enrollments", filters.getEnrollments());
        validateNonNegative("Minimum enrollments", filters.getMinEnrollments());
        validateNonNegative("Maximum enrollments", filters.getMaxEnrollments());
        validatePercentage("Completion rate", filters.getCompletionRate());
        validatePercentage("Minimum completion rate", filters.getMinCompletionRate());
        validatePercentage("Maximum completion rate", filters.getMaxCompletionRate());
        validateNonNegative("Completed learners", filters.getCompletedLearners());
        validateNonNegative("Minimum completed learners", filters.getMinCompletedLearners());
        validateNonNegative("Maximum completed learners", filters.getMaxCompletedLearners());
        validateNonNegative("Learners not started", filters.getLearnersNotStarted());
        validateNonNegative(
                "Minimum learners not started", filters.getMinLearnersNotStarted());
        validateNonNegative(
                "Maximum learners not started", filters.getMaxLearnersNotStarted());
        if (filters.getCategoryId() != null && filters.getCategoryId() <= 0) {
            throw new ApiException("Category ID must be a positive number.", HttpStatus.BAD_REQUEST);
        }
        validateRange("enrollments", filters.getMinEnrollments(), filters.getMaxEnrollments());
        validateRange(
                "completion rate",
                filters.getMinCompletionRate(),
                filters.getMaxCompletionRate());
        validateRange(
                "completed learners",
                filters.getMinCompletedLearners(),
                filters.getMaxCompletedLearners());
        validateRange(
                "learners not started",
                filters.getMinLearnersNotStarted(),
                filters.getMaxLearnersNotStarted());

        return new ReportCriteria(
                escapeLike(search),
                category,
                filters.getCategoryId(),
                filters.getEnrollments(),
                filters.getMinEnrollments(),
                filters.getMaxEnrollments(),
                filters.getCompletionRate(),
                filters.getMinCompletionRate(),
                filters.getMaxCompletionRate(),
                filters.getCompletedLearners(),
                filters.getMinCompletedLearners(),
                filters.getMaxCompletedLearners(),
                filters.getLearnersNotStarted(),
                filters.getMinLearnersNotStarted(),
                filters.getMaxLearnersNotStarted(),
                resolveInstructorScope(authentication));
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
                    "You are not authorized to view course reports.",
                    HttpStatus.FORBIDDEN);
        }
        User instructor = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ApiException(
                        "Logged-in instructor profile not found.",
                        HttpStatus.NOT_FOUND));
        if (!Boolean.TRUE.equals(instructor.getActive())
                || !hasUserRole(instructor, "INSTRUCTOR")) {
            throw new ApiException(
                    "The logged-in instructor profile is not active.",
                    HttpStatus.FORBIDDEN);
        }
        return instructor.getId();
    }

    private CourseEntity requireAvailableCourse(Long courseId) {
        if (courseId == null || courseId <= 0) {
            throw new ApiException("Course ID must be a positive number.", HttpStatus.BAD_REQUEST);
        }
        return courseRepository.findById(courseId).orElseThrow(this::courseUnavailable);
    }

    private void assertInstructorOwnsCourse(CourseEntity course, Long instructorId) {
        if (instructorId != null && !instructorId.equals(course.getInstructorId())) {
            throw new ApiException(
                    "You are not authorized to view this course report.",
                    HttpStatus.FORBIDDEN);
        }
    }

    private ApiException courseUnavailable() {
        return new ApiException(
                "The selected course is no longer available. Please select another course.",
                HttpStatus.NOT_FOUND);
    }

    private ApiException unexpectedError() {
        return new ApiException(
                "Something went wrong. Please try again later.",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ApiException exportError() {
        return new ApiException(
                "Unable to export the Course Report. Please try again.",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private boolean hasUserRole(User user, String role) {
        return user.getRole() != null
                && role.equalsIgnoreCase(user.getRole().replace("ROLE_", "").trim());
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String escapeLike(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private void validateNonNegative(String field, Long value) {
        if (value != null && value < 0) {
            throw new ApiException(field + " must be zero or greater.", HttpStatus.BAD_REQUEST);
        }
    }

    private void validatePercentage(String field, BigDecimal value) {
        if (value != null
                && (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(ONE_HUNDRED) > 0)) {
            throw new ApiException(field + " must be between 0 and 100.", HttpStatus.BAD_REQUEST);
        }
    }

    private <T extends Comparable<T>> void validateRange(String field, T minimum, T maximum) {
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new ApiException(
                    "Minimum " + field + " must not exceed maximum " + field + ".",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private Number number(Object value) {
        return value instanceof Number number ? number : 0;
    }

    private String text(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return value == null ? "" : String.valueOf(value);
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

    private byte[] createXlsx(List<List<Object>> rows) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            addZipEntry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                      <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                    </Types>
                    """);
            addZipEntry(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """);
            addZipEntry(zip, "xl/workbook.xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets><sheet name="Course Report" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>
                    """);
            addZipEntry(zip, "xl/_rels/workbook.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                    </Relationships>
                    """);
            addZipEntry(zip, "xl/worksheets/sheet1.xml", worksheetXml(rows));
            zip.finish();
            return output.toByteArray();
        }
    }

    private void addZipEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String worksheetXml(List<List<Object>> rows) {
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                """);
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            int spreadsheetRow = rowIndex + 1;
            xml.append("<row r=\"").append(spreadsheetRow).append("\">");
            List<Object> cells = rows.get(rowIndex);
            for (int columnIndex = 0; columnIndex < cells.size(); columnIndex++) {
                Object value = cells.get(columnIndex);
                String reference = columnName(columnIndex) + spreadsheetRow;
                if (value instanceof Number number) {
                    xml.append("<c r=\"").append(reference).append("\"><v>")
                            .append(number).append("</v></c>");
                } else {
                    xml.append("<c r=\"").append(reference)
                            .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(xmlEscape(value == null ? "" : String.valueOf(value)))
                            .append("</t></is></c>");
                }
            }
            xml.append("</row>");
        }
        return xml.append("</sheetData></worksheet>").toString();
    }

    private String columnName(int zeroBasedIndex) {
        StringBuilder name = new StringBuilder();
        int value = zeroBasedIndex + 1;
        while (value > 0) {
            int remainder = (value - 1) % 26;
            name.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return name.toString();
    }

    private String xmlEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (codePoint == '\t' || codePoint == '\n' || codePoint == '\r' || codePoint >= 32) {
                switch (codePoint) {
                    case '&' -> escaped.append("&amp;");
                    case '<' -> escaped.append("&lt;");
                    case '>' -> escaped.append("&gt;");
                    case '"' -> escaped.append("&quot;");
                    case '\'' -> escaped.append("&apos;");
                    default -> escaped.appendCodePoint(codePoint);
                }
            }
        });
        return escaped.toString();
    }

    private record CourseMetrics(Long courseId,
                                 String courseTitle,
                                 String courseCode,
                                 String category,
                                 long enrollments,
                                 long completedLearners,
                                 long learnersInProgress,
                                 long learnersNotStarted,
                                 BigDecimal completionRate,
                                 BigDecimal averageUserProgress) {
    }

    private record ReportCriteria(String search,
                                  String category,
                                  Long categoryId,
                                  Long enrollments,
                                  Long minEnrollments,
                                  Long maxEnrollments,
                                  BigDecimal completionRate,
                                  BigDecimal minCompletionRate,
                                  BigDecimal maxCompletionRate,
                                  Long completedLearners,
                                  Long minCompletedLearners,
                                  Long maxCompletedLearners,
                                  Long learnersNotStarted,
                                  Long minLearnersNotStarted,
                                  Long maxLearnersNotStarted,
                                  Long instructorId) {
        private static ReportCriteria unfiltered(Long instructorId) {
            return new ReportCriteria(
                    null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, instructorId);
        }
    }

    public record ExportedReport(String fileName, String contentType, byte[] content) {
    }
}
