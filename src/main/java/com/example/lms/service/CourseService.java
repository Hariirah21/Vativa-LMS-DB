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
    private static final int MIN_NAME_WORDS = 3;
    private static final int MAX_NAME_WORDS = 10;
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
        User instructor = getActiveInstructor(request.getInstructorId());
        String level = validateLevel(request.getLevel());

        CourseEntity course = CourseEntity.builder()
                .name(name)
                .categoryId(category.getId())
                .instructorId(instructor.getId())
                .level(level)
                .description(request.getDescription())
                .thumbnailUrl(storeThumbnail(thumbnail, null))
                .build();
        return toResponse(courseRepository.save(course), category, instructor);
    }

    @Transactional(readOnly = true)
    public List<CourseDto.CourseResponse> getAllCourses() {
        return courseRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CourseDto.CourseResponse getCourseById(Long courseId) {
        return toResponse(getCourseOrThrow(courseId));
    }

    @Transactional
    public CourseDto.CourseResponse updateCourse(
            Long courseId, CourseDto.CourseRequest request, MultipartFile thumbnail) {
        CourseEntity course = getCourseOrThrow(courseId);
        String name = validateName(request.getName());
        CourseCategoryEntity category = getActiveCategory(request.getCategoryId());
        User instructor = getActiveInstructor(request.getInstructorId());
        String level = validateLevel(request.getLevel());

        course.setName(name);
        course.setCategoryId(category.getId());
        course.setInstructorId(instructor.getId());
        course.setLevel(level);
        course.setDescription(request.getDescription());
        course.setThumbnailUrl(storeThumbnail(thumbnail, course.getThumbnailUrl()));
        return toResponse(courseRepository.save(course), category, instructor);
    }

    @Transactional
    public void deleteCourse(Long courseId) {
        courseRepository.delete(getCourseOrThrow(courseId));
    }

    private CourseEntity getCourseOrThrow(Long courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException(
                        "Course not found with id: " + courseId, HttpStatus.NOT_FOUND));
    }

    private CourseCategoryEntity getActiveCategory(Long categoryId) {
        CourseCategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApiException("Invalid course category.", HttpStatus.BAD_REQUEST));
        if (!Boolean.TRUE.equals(category.getActive())) {
            throw new ApiException("The selected course category is inactive.", HttpStatus.BAD_REQUEST);
        }
        return category;
    }

    private User getActiveInstructor(Long instructorId) {
        User instructor = userRepository.findById(instructorId)
                .orElseThrow(() -> new ApiException("Invalid instructor.", HttpStatus.BAD_REQUEST));
        if (!Boolean.TRUE.equals(instructor.getActive())
                || !"INSTRUCTOR".equalsIgnoreCase(instructor.getRole())) {
            throw new ApiException("The selected user is not an active instructor.", HttpStatus.BAD_REQUEST);
        }
        return instructor;
    }

    private String validateName(String rawName) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        int wordCount = name.isEmpty() ? 0 : name.split("\\s+").length;
        if (wordCount < MIN_NAME_WORDS || wordCount > MAX_NAME_WORDS) {
            throw new ApiException(
                    "Course Name must contain between 3 and 10 words.", HttpStatus.BAD_REQUEST);
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
                .instructorId(course.getInstructorId())
                .instructorName(instructorName)
                .level(course.getLevel())
                .description(course.getDescription())
                .thumbnailUrl(course.getThumbnailUrl())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .build();
    }
}
