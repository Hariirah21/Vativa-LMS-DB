package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.CourseLookupDto;
import com.example.lms.repository.UserRepository;
import com.example.lms.service.CourseCategoryCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/course-lookups")
public class CourseLookupController {
    private final CourseCategoryCatalogService categoryCatalogService;
    private final UserRepository userRepository;

    public CourseLookupController(
            CourseCategoryCatalogService categoryCatalogService,
            UserRepository userRepository) {
        this.categoryCatalogService = categoryCatalogService;
        this.userRepository = userRepository;
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<CourseLookupDto>>> categories() {
        List<CourseLookupDto> data = categoryCatalogService.getSelectableCategories()
                .stream()
                .map(item -> new CourseLookupDto(item.getId(), item.getName()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Course categories fetched successfully.", data));
    }

    @GetMapping("/instructors")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<CourseLookupDto>>> instructors(
            Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
        List<com.example.lms.entity.User> instructors = admin
                ? userRepository.findActiveUsersByRole("INSTRUCTOR")
                : userRepository.findByEmailIgnoreCase(authentication.getName())
                        .filter(user -> Boolean.TRUE.equals(user.getActive())
                                && user.getRole() != null
                                && "INSTRUCTOR".equalsIgnoreCase(
                                        user.getRole().replace("ROLE_", "").trim()))
                        .map(List::of)
                        .orElseGet(List::of);
        List<CourseLookupDto> data = instructors
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
