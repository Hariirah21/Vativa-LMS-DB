package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.CourseDto;
import com.example.lms.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CourseDto.CourseResponse>> createCourse(
            @Valid @RequestBody CourseDto.CourseRequest request) {
        CourseDto.CourseResponse created = courseService.createCourse(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Course created successfully.", created));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseDto.CourseResponse>>> getAllCourses() {
        List<CourseDto.CourseResponse> courses = courseService.getAllCourses();
        return ResponseEntity.ok(ApiResponse.success("Courses fetched successfully.", courses));
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseDto.CourseResponse>> getCourseById(
            @PathVariable Long courseId) {
        CourseDto.CourseResponse course = courseService.getCourseById(courseId);
        return ResponseEntity.ok(ApiResponse.success("Course fetched successfully.", course));
    }

    @PutMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseDto.CourseResponse>> updateCourse(
            @PathVariable Long courseId,
            @Valid @RequestBody CourseDto.CourseRequest request) {
        CourseDto.CourseResponse updated = courseService.updateCourse(courseId, request);
        return ResponseEntity.ok(ApiResponse.success("Course updated successfully.", updated));
    }

    @DeleteMapping("/{courseId}")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(@PathVariable Long courseId) {
        courseService.deleteCourse(courseId);
        return ResponseEntity.ok(ApiResponse.success("Course deleted successfully."));
    }
}