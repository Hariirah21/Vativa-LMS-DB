package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.CourseEnrollmentDto;
import com.example.lms.service.CourseEnrollmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses/{courseId}/enrollments")
@PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
public class CourseEnrollmentController {
    private final CourseEnrollmentService service;

    public CourseEnrollmentController(CourseEnrollmentService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseEnrollmentDto.EnrolledUserResponse>>> list(@PathVariable Long courseId) {
        return ResponseEntity.ok(ApiResponse.success("Enrolled users fetched successfully.", service.getEnrolledUsers(courseId)));
    }

    @GetMapping("/eligible-users")
    public ResponseEntity<ApiResponse<List<CourseEnrollmentDto.EligibleUserResponse>>> eligible(@PathVariable Long courseId) {
        return ResponseEntity.ok(ApiResponse.success("Eligible users fetched successfully.", service.getEligibleUsers(courseId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CourseEnrollmentDto.BulkOperationResponse>> enroll(
            @PathVariable Long courseId, @Valid @RequestBody CourseEnrollmentDto.BulkUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Selected users enrolled successfully.", service.enroll(courseId, request.getUserIds())));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<CourseEnrollmentDto.BulkOperationResponse>> unenroll(
            @PathVariable Long courseId, @Valid @RequestBody CourseEnrollmentDto.BulkUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Selected users unenrolled successfully.", service.unenroll(courseId, request.getUserIds())));
    }
}
