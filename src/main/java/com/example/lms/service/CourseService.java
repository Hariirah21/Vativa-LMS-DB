package com.example.lms.service;

import com.example.lms.dto.CourseDto;
import com.example.lms.entity.CourseCategoryEntity;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseCategoryRepository;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CourseService {
    private static final Set<String> ALLOWED_LEVELS =
            Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED");
    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png");
    private static final long MAX_THUMBNAIL_BYTES = 2 * 1024 * 1024;
    private static final int MAX_NAME_CHARACTERS = 255;
    private static final Path THUMBNAIL_DIRECTORY =
            Path.of("uploads", "course-thumbnails").toAbsolutePath().normalize();

    private final CourseRepository courseRepository;
    private final CourseCategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public CourseService(
            CourseRepository courseRepository,
            CourseCategoryRepository categoryRepository,
            UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CourseDto.CourseResponse createCourse(
            CourseDto.CourseRequest request, MultipartFile thumbnail) {
        String name = validateName(request.getName());
        CourseCategoryEntity category = getActiveCategory(request.getCategoryId());
        User instructor = resolveActiveInstructor(request.getInstructorId());
        String level = validateLevel(request.getLevel());

        CourseEntity course = CourseEntity.builder()
                .name(name)
                .categoryId(category.getId())
                .instructorId(instructor.getId())
                .level(level)
                .description(request.getDescription())
                .thumbnailUrl(storeThumbnail(thumbnail, null))
                .build();
        return toResponse(courseRepository.saveAndFlush(course), category, instructor);
    }

    @Transactional(readOnly = true)
    public List<CourseDto.CourseResponse> getAllCourses() {
        return courseRepository.findAllByOrderByUpdatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseDto.CourseResponse getCourseById(Long courseId) {
        return toResponse(getCourseOrThrow(courseId));
    }

    @Transactional
    public CourseDto.CourseResponse updateCourse(
            Long courseId, CourseDto.CourseRequest request, MultipartFile thumbnail) {
        CourseEntity course = getCourseOrThrow(courseId);
        assertInstructorOwnsCourse(course);
        String name = validateName(request.getName());
        CourseCategoryEntity category = getActiveCategory(request.getCategoryId());
        User instructor = resolveActiveInstructor(request.getInstructorId());
        String level = validateLevel(request.getLevel());

        course.setName(name);
        course.setCategoryId(category.getId());
        course.setInstructorId(instructor.getId());
        course.setLevel(level);
        course.setDescription(request.getDescription());
        course.setThumbnailUrl(storeThumbnail(thumbnail, course.getThumbnailUrl()));
        return toResponse(courseRepository.saveAndFlush(course), category, instructor);
    }

    @Transactional
    public void deleteCourse(Long courseId) {
        CourseEntity course = getCourseOrThrow(courseId);
        assertInstructorOwnsCourse(course);
        courseRepository.delete(course);
    }

    private CourseEntity getCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException(
                        "Course not found with id: " + courseId, HttpStatus.NOT_FOUND));
    }

    private CourseCategoryEntity getActiveCategory(Long categoryId) {
        CourseCategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApiException(
                        "Course category not found with id: " + categoryId,
                        HttpStatus.NOT_FOUND));
        if (!Boolean.TRUE.equals(category.getActive())) {
            throw new ApiException("The selected course category is inactive.", HttpStatus.BAD_REQUEST);
        }
        return category;
    }

    private User resolveActiveInstructor(Long requestedInstructorId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException("Authentication is required.", HttpStatus.UNAUTHORIZED);
        }
        boolean signedInAsInstructor = hasRole(authentication, "ROLE_INSTRUCTOR");
        boolean signedInAsAdmin = hasRole(authentication, "ROLE_ADMIN");
        if (!signedInAsInstructor && !signedInAsAdmin) {
            throw new ApiException("Your role cannot manage courses.", HttpStatus.FORBIDDEN);
        }

        User instructor = signedInAsInstructor
                ? userRepository.findByEmailIgnoreCase(authentication.getName())
                    .orElseThrow(() -> new ApiException(
                            "Logged-in instructor profile not found.",
                            HttpStatus.NOT_FOUND))
                : userRepository.findById(requestedInstructorId)
                .orElseThrow(() -> new ApiException(
                        "Instructor not found with id: " + requestedInstructorId,
                        HttpStatus.NOT_FOUND));
        if (!Boolean.TRUE.equals(instructor.getActive())
                || instructor.getRole() == null
                || !"INSTRUCTOR".equalsIgnoreCase(
                        instructor.getRole().replace("ROLE_", "").trim())) {
            throw new ApiException("The selected user is not an active instructor.", HttpStatus.BAD_REQUEST);
        }
        return instructor;
    }

    private void assertInstructorOwnsCourse(CourseEntity course) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !hasRole(authentication, "ROLE_INSTRUCTOR")) {
            return;
        }
        User current = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ApiException(
                        "Logged-in instructor profile not found.", HttpStatus.NOT_FOUND));
        if (!current.getId().equals(course.getInstructorId())) {
            throw new ApiException(
                    "Instructors can only modify courses assigned to themselves.",
                    HttpStatus.FORBIDDEN);
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private String validateName(String rawName) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new ApiException("Course Name is required.", HttpStatus.BAD_REQUEST);
        }
        if (name.length() > MAX_NAME_CHARACTERS) {
            throw new ApiException(
                    "Course Name must not exceed 255 characters.",
                    HttpStatus.BAD_REQUEST);
        }
        return name;
    }

    private String validateLevel(String rawLevel) {
        String level = rawLevel == null ? "" : rawLevel.trim().toUpperCase();
        if (!ALLOWED_LEVELS.contains(level)) {
            throw new ApiException(
                    "Course Level must be BEGINNER, INTERMEDIATE, or ADVANCED.",
                    HttpStatus.BAD_REQUEST);
        }
        return level;
    }

    private String storeThumbnail(MultipartFile thumbnail, String currentUrl) {
        if (thumbnail == null || thumbnail.isEmpty()) return currentUrl;
        if (thumbnail.getSize() > MAX_THUMBNAIL_BYTES
                || !ALLOWED_IMAGE_TYPES.contains(thumbnail.getContentType())) {
            throw new ApiException("Thumbnail must be a JPG or PNG up to 2 MB.", HttpStatus.BAD_REQUEST);
        }
        String extension = "image/png".equals(thumbnail.getContentType()) ? ".png" : ".jpg";
        String fileName = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(THUMBNAIL_DIRECTORY);
            Files.copy(
                    thumbnail.getInputStream(),
                    THUMBNAIL_DIRECTORY.resolve(fileName),
                    StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/course-thumbnails/" + fileName;
        } catch (IOException exception) {
            throw new ApiException("Course thumbnail could not be saved.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private CourseDto.CourseResponse toResponse(CourseEntity course) {
        return toResponse(
                course,
                categoryRepository.findById(course.getCategoryId()).orElse(null),
                userRepository.findById(course.getInstructorId()).orElse(null));
    }

    private CourseDto.CourseResponse toResponse(
            CourseEntity course, CourseCategoryEntity category, User instructor) {
        String instructorName = instructor == null ? "" :
                (instructor.getFirstName() + " " + instructor.getLastName()).trim();
        return CourseDto.CourseResponse.builder()
                .id(course.getId())
                .name(course.getName())
                .categoryId(course.getCategoryId())
                .categoryName(category == null ? "" : category.getName())
                .categoryDescription(category == null ? null : category.getDescription())
                .categoryActive(category != null && Boolean.TRUE.equals(category.getActive()))
                .instructorId(course.getInstructorId())
                .instructorName(instructorName)
                .instructorEmail(instructor == null ? null : instructor.getEmail())
                .level(course.getLevel())
                .description(course.getDescription())
                .thumbnailUrl(course.getThumbnailUrl())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .build();
    }
}
