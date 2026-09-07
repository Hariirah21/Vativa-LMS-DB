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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CourseService {
    private static final Set<String> ALLOWED_LEVELS =
            Set.of("BEGINNER", "INTERMEDIATE", "ADVANCED");
    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/jpeg", "image/png");
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS =
            Set.of("jpg", "jpeg", "png");
    private static final long MAX_THUMBNAIL_BYTES = 2 * 1024 * 1024;
    private static final int MAX_NAME_CHARACTERS = 255;
    private static final String DEFAULT_THUMBNAIL_URL =
            "/images/default-course-thumbnail.svg";
    private static final Path THUMBNAIL_DIRECTORY =
            Path.of("uploads", "course-thumbnails").toAbsolutePath().normalize();

    private final CourseRepository courseRepository;
    private final CourseCategoryRepository categoryRepository;
    private final CourseCategoryCatalogService categoryCatalogService;
    private final UserRepository userRepository;

    public CourseService(
            CourseRepository courseRepository,
            CourseCategoryRepository categoryRepository,
            CourseCategoryCatalogService categoryCatalogService,
            UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.categoryRepository = categoryRepository;
        this.categoryCatalogService = categoryCatalogService;
        this.userRepository = userRepository;
    }

    @Transactional
    public CourseDto.CourseResponse createCourse(
            CourseDto.CourseRequest request,
            MultipartFile thumbnail,
            Authentication authentication) {
        String name = validateName(request.getName());
        ensureUniqueName(name, null);
        CourseCategoryEntity category = getActiveCategory(request.getCategoryId());
        User instructor = resolveActiveInstructor(request.getInstructorId(), authentication);
        String level = validateLevel(request.getLevel());

        CourseEntity course = CourseEntity.builder()
                .name(name)
                .categoryId(category.getId())
                .instructorId(instructor.getId())
                .level(level)
                .description(request.getDescription())
                .thumbnailUrl(storeThumbnail(thumbnail, DEFAULT_THUMBNAIL_URL))
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
            Long courseId,
            CourseDto.CourseRequest request,
            MultipartFile thumbnail,
            Authentication authentication) {
        CourseEntity course = getCourseOrThrow(courseId);
        assertInstructorOwnsCourse(course, authentication);
        String name = validateName(request.getName());
        ensureUniqueName(name, courseId);
        CourseCategoryEntity category = getActiveCategory(request.getCategoryId());
        User instructor = resolveActiveInstructor(request.getInstructorId(), authentication);
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
    public void deleteCourse(Long courseId, Authentication authentication) {
        CourseEntity course = getCourseOrThrow(courseId);
        assertInstructorOwnsCourse(course, authentication);
        courseRepository.delete(course);
    }

    private CourseEntity getCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException(
                        "Course not found with id: " + courseId, HttpStatus.NOT_FOUND));
    }

    private CourseCategoryEntity getActiveCategory(Long categoryId) {
        return categoryCatalogService.getSelectableCategory(categoryId);
    }

    private User resolveActiveInstructor(
            Long requestedInstructorId, Authentication authentication) {
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

    private void assertInstructorOwnsCourse(
            CourseEntity course, Authentication authentication) {
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
        int wordCount = name.split(" ").length;
        if (wordCount < 3) {
            throw new ApiException(
                    "Course Name must contain at least 3 words.",
                    HttpStatus.BAD_REQUEST);
        }
        if (wordCount > 10) {
            throw new ApiException(
                    "Course Name must not exceed 10 words.",
                    HttpStatus.BAD_REQUEST);
        }
        return name;
    }

    private void ensureUniqueName(String name, Long currentCourseId) {
        boolean duplicate = currentCourseId == null
                ? courseRepository.existsByNameIgnoreCase(name)
                : courseRepository.existsByNameIgnoreCaseAndIdNot(name, currentCourseId);
        if (duplicate) {
            throw new ApiException("Course Name already exists.", HttpStatus.CONFLICT);
        }
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
        if (thumbnail == null || thumbnail.isEmpty()) {
            return currentUrl == null || currentUrl.isBlank()
                    ? DEFAULT_THUMBNAIL_URL
                    : currentUrl;
        }
        String extension = validateThumbnail(thumbnail);
        String storedExtension = "png".equals(extension) ? ".png" : ".jpg";
        String fileName = UUID.randomUUID() + storedExtension;
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

    private String validateThumbnail(MultipartFile thumbnail) {
        if (thumbnail.getSize() > MAX_THUMBNAIL_BYTES) {
            throw new ApiException(
                    "Thumbnail must not exceed 2 MB.", HttpStatus.BAD_REQUEST);
        }

        String originalName = thumbnail.getOriginalFilename();
        int extensionSeparator = originalName == null ? -1 : originalName.lastIndexOf('.');
        String extension = originalName == null || extensionSeparator < 0
                || extensionSeparator == originalName.length() - 1
                ? ""
                : originalName.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT);
        String contentType = thumbnail.getContentType() == null
                ? ""
                : thumbnail.getContentType().toLowerCase(Locale.ROOT);

        boolean matchingType = switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg".equals(contentType);
            case "png" -> "image/png".equals(contentType);
            default -> false;
        };
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)
                || !ALLOWED_IMAGE_TYPES.contains(contentType)
                || !matchingType) {
            throw new ApiException(
                    "Thumbnail must be a JPG, JPEG, or PNG file.", HttpStatus.BAD_REQUEST);
        }
        return extension;
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
                .thumbnailUrl(course.getThumbnailUrl() == null || course.getThumbnailUrl().isBlank()
                        ? DEFAULT_THUMBNAIL_URL
                        : course.getThumbnailUrl())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .build();
    }
}
