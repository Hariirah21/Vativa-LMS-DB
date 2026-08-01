package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.CourseLookupDto;
import com.example.lms.repository.CourseCategoryRepository;
import com.example.lms.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/course-lookups")
public class CourseLookupController {
    private final CourseCategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public CourseLookupController(
            CourseCategoryRepository categoryRepository,
            UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<CourseLookupDto>>> categories() {
        List<CourseLookupDto> data = categoryRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .map(item -> new CourseLookupDto(item.getId(), item.getName()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Course categories fetched successfully.", data));
    }

    @GetMapping("/instructors")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CourseLookupDto>>> instructors() {
        List<CourseLookupDto> data = userRepository
                .findByRoleIgnoreCaseAndActiveTrueOrderByFirstNameAscLastNameAsc("INSTRUCTOR")
                .stream()
                .map(user -> new CourseLookupDto(
                        user.getId(),
                        (user.getFirstName() + " " + user.getLastName()).trim()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Instructors fetched successfully.", data));
    }

    @GetMapping("/levels")
    public ResponseEntity<ApiResponse<List<String>>> levels() {
        return ResponseEntity.ok(ApiResponse.success(
                "Course levels fetched successfully.",
                List.of("BEGINNER", "INTERMEDIATE", "ADVANCED")));
    }
}
