package com.example.lms.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.CourseDto;
import com.example.lms.service.CourseService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CourseDto.CourseResponse>> createCourse(
            @Valid @RequestBody CourseDto.CourseRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        CourseDto.CourseResponse created = courseService.createCourse(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Course created successfully.", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseDto.CourseResponse>>> getAllCourses(
            @RequestParam(name = "search", required = false) String searchName,
            @RequestParam(name = "status", required = false) String status) {
        List<CourseDto.CourseResponse> courses = courseService.getAllCourses(searchName, status);
        return ResponseEntity.ok(ApiResponse.success("Courses fetched successfully.", courses));
    }

    @GetMapping("/instructors")
    public ResponseEntity<ApiResponse<List<CourseDto.InstructorOption>>> getActiveInstructors() {
        List<CourseDto.InstructorOption> instructors = courseService.getActiveInstructors();
        return ResponseEntity.ok(
                ApiResponse.success("Active instructors fetched successfully.", instructors));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseDto.CourseResponse>> getCourseById(
            @PathVariable Long courseId) {
        CourseDto.CourseResponse course = courseService.getCourseById(courseId);
        return ResponseEntity.ok(ApiResponse.success("Course fetched successfully.", course));
    }

    @GetMapping("/{courseId}/enrollments")
    public ResponseEntity<ApiResponse<List<CourseDto.EnrolledCourseResponse>>> getEnrolledCourseList(
            @PathVariable Long courseId) {
        List<CourseDto.EnrolledCourseResponse> enrollments =
                courseService.getEnrolledCourseList(courseId);
        return ResponseEntity.ok(
                ApiResponse.success("Enrolled Course List fetched successfully.", enrollments));
    }

    @PostMapping("/enrollments")
    public ResponseEntity<ApiResponse<List<CourseDto.EnrolledCourseResponse>>> enrollUsers(
            @Valid @RequestBody CourseDto.EnrollUsersRequest request) {
        List<CourseDto.EnrolledCourseResponse> enrollments =
                courseService.enrollUsers(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Selected users enrolled successfully.", enrollments));
    }

    @DeleteMapping("/enrollments")
    public ResponseEntity<ApiResponse<Void>> unenrollUsers(
            @Valid @RequestBody CourseDto.UnenrollUsersRequest request) {
        courseService.unenrollUsers(request);
        return ResponseEntity.ok(
                ApiResponse.success("Selected users unenrolled successfully."));
    }

    @PutMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseDto.CourseResponse>> updateCourse(
            @PathVariable Long courseId,
            @Valid @RequestBody CourseDto.CourseRequest request,
            @AuthenticationPrincipal AuthPrincipal principal) {
        CourseDto.CourseResponse updated = courseService.updateCourse(courseId, request, principal);
        return ResponseEntity.ok(ApiResponse.success("Course updated successfully.", updated));
    }

    @DeleteMapping("/{courseId}")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(
            @PathVariable Long courseId,
            @AuthenticationPrincipal AuthPrincipal principal) {
        courseService.deleteCourse(courseId, principal);
        return ResponseEntity.ok(ApiResponse.success("Course deleted successfully."));
    }
}
