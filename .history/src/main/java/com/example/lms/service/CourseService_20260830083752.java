package com.example.lms.service;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.lms.config.AuthPrincipal;
import com.example.lms.dto.CourseDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseRepository;

@Service
public class CourseService {

    // SRS Field List - Course Category: fixed dropdown values, manual entry not allowed
    private static final Set<String> ALLOWED_CATEGORIES =
            Set.of("Technical", "Soft Skills", "Compliance", "Leadership");

    // SRS Field List - Course Level: fixed dropdown values
    private static final Set<String> ALLOWED_LEVELS =
            Set.of("Beginner", "Intermediate", "Advanced");

    // SRS Field List - Course Status: fixed dropdown values (Read-only, auto-managed)
    private static final Set<String> ALLOWED_STATUS =
            Set.of("Draft", "Upcoming", "Published");
    private static final String DEFAULT_STATUS = "Draft";

    private static final int MIN_NAME_WORDS = 3;
    private static final int MAX_NAME_WORDS = 10;
    private static final int MIN_SEARCH_LENGTH = 1;
    private static final int MAX_SEARCH_LENGTH = 100;

    private final CourseRepository courseRepository;

    public CourseService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Transactional
    public CourseDto.CourseResponse createCourse(CourseDto.CourseRequest request, AuthPrincipal principal) {
        validateCreateUpdatePermission(principal);
        
        String name = validateName(request.getName());
        String category = validateCategory(request.getCategory());
        String courseLevel = validateLevel(request.getCourseLevel());
        String status = DEFAULT_STATUS;  // Status is read-only, defaults to "Draft"

        if (courseRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException("A course with the same name already exists.", HttpStatus.CONFLICT);
        }

        CourseEntity course = CourseEntity.builder()
                .name(name)
                .category(category)
                .instructorId(request.getInstructorId())
                .courseLevel(courseLevel)
                .description(request.getDescription())
                .thumbnailUrl(request.getThumbnailUrl())
                .status(status)
                .build();
        return toResponse(courseRepository.save(course));
    }

    @Transactional(readOnly = true)
    public List<CourseDto.CourseResponse> getAllCourses(String searchName, String status) {
        List<CourseEntity> courses;
        
        // Validate search input if provided
        if (searchName != null && !searchName.trim().isEmpty()) {
            searchName = validateSearchInput(searchName);
        }
        
        // Validate status filter if provided
        if (status != null && !status.trim().isEmpty()) {
            status = validateStatusFilter(status);
        }
        
        boolean hasSearch = searchName != null && !searchName.trim().isEmpty();
        boolean hasStatus = status != null && !status.trim().isEmpty();

        if (hasSearch && hasStatus) {
            courses = courseRepository.searchByNameAndStatus(searchName.trim(), status.trim());
        } else if (hasSearch) {
            courses = courseRepository.searchByName(searchName.trim());
        } else if (hasStatus) {
            courses = courseRepository.findByStatus(status.trim());
        } else {
            courses = courseRepository.findAll();
        }
        return courses.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseDto.CourseResponse getCourseById(Long courseId) {
        return toResponse(getCourseOrThrow(courseId));
    }

    @Transactional
    public CourseDto.CourseResponse updateCourse(Long courseId, CourseDto.CourseRequest request, AuthPrincipal principal) {
        validateCreateUpdatePermission(principal);
        CourseEntity course = getCourseOrThrow(courseId);

        String name = validateName(request.getName());
        String category = validateCategory(request.getCategory());
        String courseLevel = validateLevel(request.getCourseLevel());

        if (courseRepository.existsByNameIgnoreCaseAndIdNot(name, courseId)) {
            throw new ApiException("A course with the same name already exists.", HttpStatus.CONFLICT);
        }

        course.setName(name);
        course.setCategory(category);
        course.setInstructorId(request.getInstructorId());
        course.setCourseLevel(courseLevel);
        course.setDescription(request.getDescription());
        course.setThumbnailUrl(request.getThumbnailUrl());
        // Status is read-only and NOT updated by users
        return toResponse(courseRepository.save(course));
    }

    @Transactional
    public void deleteCourse(Long courseId, AuthPrincipal principal) {
        CourseEntity course = getCourseOrThrow(courseId);
        validateDeletePermission(course, principal);
        courseRepository.delete(course);
    }

    // ---------- helpers ----------

    private CourseEntity getCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException(
                        "Course not found with id: " + courseId, HttpStatus.NOT_FOUND));
    }

    private String validateName(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw new ApiException("Course Name is required.", HttpStatus.BAD_REQUEST);
        }
        int wordCount = name.split("\\s+").length;
        if (wordCount < MIN_NAME_WORDS || wordCount > MAX_NAME_WORDS) {
            throw new ApiException(
                    "Course Name must contain between " + MIN_NAME_WORDS + " and " + MAX_NAME_WORDS + " words.",
                    HttpStatus.BAD_REQUEST);
        }
        return name;
    }

    private String validateCategory(String rawCategory) {
        String category = rawCategory == null ? "" : rawCategory.trim();
        if (category.isEmpty()) {
            throw new ApiException("Course Category is required", HttpStatus.BAD_REQUEST);
        }
        boolean matched = ALLOWED_CATEGORIES.stream().anyMatch(c -> c.equalsIgnoreCase(category));
        if (!matched) {
            throw new ApiException(
                    "Course Category must be one of: " + String.join(", ", ALLOWED_CATEGORIES),
                    HttpStatus.BAD_REQUEST);
        }
        return category;
    }

    private String validateLevel(String rawLevel) {
        String level = rawLevel == null ? "" : rawLevel.trim();
        if (level.isEmpty()) {
            throw new ApiException("Course Level is required", HttpStatus.BAD_REQUEST);
        }
        boolean matched = ALLOWED_LEVELS.stream().anyMatch(l -> l.equalsIgnoreCase(level));
        if (!matched) {
            throw new ApiException(
                    "Course Level must be one of: " + String.join(", ", ALLOWED_LEVELS),
                    HttpStatus.BAD_REQUEST);
        }
        return level;
    }

    private String validateSearchInput(String rawSearch) {
        String search = rawSearch == null ? "" : rawSearch.trim();
        
        // Check if empty
        if (search.isEmpty()) {
            return search; // Empty search is allowed (shows all courses)
        }
        
        // Check length
        if (search.length() < MIN_SEARCH_LENGTH || search.length() > MAX_SEARCH_LENGTH) {
            throw new ApiException(
                    "Search input must be between " + MIN_SEARCH_LENGTH + " and " + MAX_SEARCH_LENGTH + " characters.",
                    HttpStatus.BAD_REQUEST);
        }
        
        // Validate alphanumeric format (allow alphanumeric, spaces, hyphens, underscores)
        if (!search.matches("^[a-zA-Z0-9\\s\\-_]+$")) {
            throw new ApiException(
                    "Search input must contain only alphanumeric characters, spaces, hyphens, and underscores.",
                    HttpStatus.BAD_REQUEST);
        }
        
        return search;
    }

    private String validateStatusFilter(String rawStatus) {
        String status = rawStatus == null ? "" : rawStatus.trim();
        if (status.isEmpty()) {
            return status; // Empty status is allowed (shows all courses)
        }
        boolean matched = ALLOWED_STATUS.stream().anyMatch(s -> s.equalsIgnoreCase(status));
        if (!matched) {
            throw new ApiException(
                    "Invalid Course Status. Allowed values are: " + String.join(", ", ALLOWED_STATUS),
                    HttpStatus.BAD_REQUEST);
        }
        return status;
    }

    private void validateDeletePermission(CourseEntity course, AuthPrincipal principal) {
        if (principal == null) {
            throw new ApiException("Authentication is required to delete a course.", HttpStatus.UNAUTHORIZED);
        }

        String role = principal.getRole();
        boolean isAdmin = role != null && (role.equalsIgnoreCase("ADMIN") || role.equalsIgnoreCase("SUPER_ADMIN"));
        boolean isInstructor = role != null && role.equalsIgnoreCase("INSTRUCTOR");

        // ADMIN/SUPER_ADMIN can delete any course
        if (isAdmin) {
            return;
        }

        // INSTRUCTOR can only delete courses they created/own
        if (isInstructor) {
            if (!course.getInstructorId().equals(principal.getId())) {
                throw new ApiException(
                        "You do not have permission to delete this course. Only the course instructor or an admin can delete it.",
                        HttpStatus.FORBIDDEN);
            }
            return;
        }

        // All other roles are denied
        throw new ApiException(
                "You do not have permission to perform this action. Only instructors and admins can delete courses.",
                HttpStatus.FORBIDDEN);
    }

    private void validateCreateUpdatePermission(AuthPrincipal principal) {
        if (principal == null) {
            throw new ApiException("Authentication is required to create or update a course.", HttpStatus.UNAUTHORIZED);
        }

        String role = principal.getRole();
        boolean isAdmin = role != null && (role.equalsIgnoreCase("ADMIN") || role.equalsIgnoreCase("SUPER_ADMIN"));
        boolean isInstructor = role != null && role.equalsIgnoreCase("INSTRUCTOR");

        // Only ADMIN, SUPER_ADMIN, and INSTRUCTOR can create/update courses
        if (isAdmin || isInstructor) {
            return;
        }

        throw new ApiException(
                "You do not have permission to perform this action. Only instructors and admins can create or update courses.",
                HttpStatus.FORBIDDEN);
    }

    private CourseDto.CourseResponse toResponse(CourseEntity c) {
        return CourseDto.CourseResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .category(c.getCategory())
                .instructorId(c.getInstructorId())
                .courseLevel(c.getCourseLevel())
                .description(c.getDescription())
                .thumbnailUrl(c.getThumbnailUrl())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}