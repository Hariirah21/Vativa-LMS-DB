package com.example.lms.service;

import com.example.lms.dto.CourseDto;
import com.example.lms.entity.CourseEntity;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class CourseService {

    // SRS Field List - Course Category: fixed dropdown values, manual entry not allowed
    private static final Set<String> ALLOWED_CATEGORIES =
            Set.of("Technical", "Soft Skills", "Compliance", "Leadership");

    // SRS Field List - Course Level: fixed dropdown values
    private static final Set<String> ALLOWED_LEVELS =
            Set.of("Beginner", "Intermediate", "Advanced");

    private static final int MIN_NAME_WORDS = 3;
    private static final int MAX_NAME_WORDS = 10;

    private final CourseRepository courseRepository;

    public CourseService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Transactional
    public CourseDto.CourseResponse createCourse(CourseDto.CourseRequest request) {
        String name = validateName(request.getName());
        String category = validateCategory(request.getCategory());
        String courseLevel = validateLevel(request.getCourseLevel());

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
                .build();
        return toResponse(courseRepository.save(course));
    }

    @Transactional(readOnly = true)
    public List<CourseDto.CourseResponse> getAllCourses() {
        return courseRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseDto.CourseResponse getCourseById(Long courseId) {
        return toResponse(getCourseOrThrow(courseId));
    }

    @Transactional
    public CourseDto.CourseResponse updateCourse(Long courseId, CourseDto.CourseRequest request) {
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
        return toResponse(courseRepository.save(course));
    }

    @Transactional
    public void deleteCourse(Long courseId) {
        CourseEntity course = getCourseOrThrow(courseId);
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

    private CourseDto.CourseResponse toResponse(CourseEntity c) {
        return CourseDto.CourseResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .category(c.getCategory())
                .instructorId(c.getInstructorId())
                .courseLevel(c.getCourseLevel())
                .description(c.getDescription())
                .thumbnailUrl(c.getThumbnailUrl())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}