package com.example.lms.controller;

import com.example.lms.dto.ApiResponse;
import com.example.lms.dto.CourseCategoryDto;
import com.example.lms.dto.InstructorDto;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseCategoryRepository;
import com.example.lms.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CourseReferenceController {
    private final CourseCategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public CourseReferenceController(
            CourseCategoryRepository categoryRepository,
            UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/api/course-categories/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<CourseCategoryDto>>> activeCategories() {
        List<CourseCategoryDto> categories = categoryRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .map(category -> CourseCategoryDto.builder()
                        .id(category.getId())
                        .name(category.getName())
                        .description(category.getDescription())
                        .active(Boolean.TRUE.equals(category.getActive()))
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.success(
                "Active course categories fetched successfully.", categories));
    }

    @GetMapping("/api/instructors/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'INSTRUCTOR')")
    public ResponseEntity<ApiResponse<List<InstructorDto>>> activeInstructors(
            Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        List<User> instructors;
        if (admin) {
            instructors = userRepository
                    .findByRoleIgnoreCaseAndActiveTrueOrderByFirstNameAscLastNameAsc("INSTRUCTOR");
        } else {
            User current = userRepository.findByEmailIgnoreCase(authentication.getName())
                    .orElseThrow(() -> new ApiException(
                            "Logged-in instructor profile not found.", HttpStatus.NOT_FOUND));
            if (!isActiveInstructor(current)) {
                throw new ApiException("Logged-in instructor profile not found.", HttpStatus.NOT_FOUND);
            }
            instructors = List.of(current);
        }
        return ResponseEntity.ok(ApiResponse.success(
                "Active instructors fetched successfully.",
                instructors.stream().map(this::toInstructorDto).toList()));
    }

    private InstructorDto toInstructorDto(User user) {
        return InstructorDto.builder()
                .id(user.getId())
                .name((user.getFirstName() + " " + user.getLastName()).trim())
                .email(user.getEmail())
                .active(Boolean.TRUE.equals(user.getActive()))
                .build();
    }

    private boolean isActiveInstructor(User user) {
        return Boolean.TRUE.equals(user.getActive())
                && user.getRole() != null
                && "INSTRUCTOR".equalsIgnoreCase(user.getRole().replace("ROLE_", "").trim());
    }
}
