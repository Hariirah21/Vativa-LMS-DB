package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.LearnerReportDto;
import com.example.lms.service.LearnerReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/learner-reports")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'INSTRUCTOR')")
public class LearnerReportController {
    private static final MediaType CSV_MEDIA_TYPE =
            new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final LearnerReportService learnerReportService;

    public LearnerReportController(LearnerReportService learnerReportService) {
        this.learnerReportService = learnerReportService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LearnerReportDto.ListResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String filter,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Learner reports fetched successfully.",
                learnerReportService.list(search, filter, authentication)));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String filter,
            Authentication authentication) {
        LearnerReportService.ExportedReport report =
                learnerReportService.export(search, filter, authentication);
        return ResponseEntity.ok()
                .contentType(CSV_MEDIA_TYPE)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(report.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(report.content());
    }

    @GetMapping("/{learnerId}")
    public ResponseEntity<ApiResponse<LearnerReportDto.PreviewResponse>> preview(
            @PathVariable Long learnerId,
            Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                "Learner report fetched successfully.",
                learnerReportService.preview(learnerId, authentication)));
    }
}
