package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.CourseDto;
import com.example.lms.exception.ApiException;
import com.example.lms.exception.CourseCreationException;
import com.example.lms.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseDto.CourseResponse>> createCourse(
            @Valid @RequestPart("course") CourseDto.CourseRequest request,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail,
            Authentication authentication) {
        try {
            CourseDto.CourseResponse created =
                    courseService.createCourse(request, thumbnail, authentication);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.success("Course created successfully.", created));
        } catch (DataIntegrityViolationException | AccessDeniedException exception) {
            throw exception;
        } catch (ApiException exception) {
            if (exception.getStatus().is5xxServerError()) {
                throw new CourseCreationException(exception);
            }
            throw exception;
        } catch (RuntimeException exception) {
            throw new CourseCreationException(exception);
        }
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
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<CourseDto.CourseResponse>> updateCourse(
            @PathVariable Long courseId,
            @Valid @RequestPart("course") CourseDto.CourseRequest request,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail,
            Authentication authentication) {
        CourseDto.CourseResponse updated =
                courseService.updateCourse(courseId, request, thumbnail, authentication);
        return ResponseEntity.ok(ApiResponse.success("Course updated successfully.", updated));
    }

    @DeleteMapping("/{courseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(
            @PathVariable Long courseId, Authentication authentication) {
        courseService.deleteCourse(courseId, authentication);
        return ResponseEntity.ok(ApiResponse.success("Course deleted successfully."));
    }
}
