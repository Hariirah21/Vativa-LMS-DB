package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.CourseReportDto;
import com.example.lms.service.CourseReportService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/course-reports")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INSTRUCTOR')")
public class CourseReportController {
    private final CourseReportService courseReportService;

    public CourseReportController(CourseReportService courseReportService) {
        this.courseReportService = courseReportService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseReportDto.ListResponse>>> list(
            @Valid @ModelAttribute CourseReportDto.FilterRequest filters,
            Authentication authentication) {
        List<CourseReportDto.ListResponse> reports =
                courseReportService.list(filters, authentication);
        String message = reports.isEmpty()
                ? "No course report data available."
                : "Course reports fetched successfully.";
        return ResponseEntity.ok(ApiResponse.success(message, reports));
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(
            @Valid @ModelAttribute CourseReportDto.FilterRequest filters,
            Authentication authentication) {
        return download(courseReportService.exportCsv(filters, authentication));
    }

    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportExcel(
            @Valid @ModelAttribute CourseReportDto.FilterRequest filters,
            Authentication authentication) {
        return download(courseReportService.exportExcel(filters, authentication));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseReportDto.OverviewResponse>> overview(
            @PathVariable Long courseId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Course overview fetched successfully.",
                courseReportService.overview(courseId, authentication)));
    }

    private ResponseEntity<byte[]> download(CourseReportService.ExportedReport report) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(report.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(report.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(report.content());
    }
}
