package com.example.lms.service;

import com.example.lms.dto.CourseEnrollmentDto;
import com.example.lms.entity.CourseEnrollmentEntity;
import com.example.lms.entity.CourseEntity;
import com.example.lms.entity.User;
import com.example.lms.exception.ApiException;
import com.example.lms.repository.CourseEnrollmentRepository;
import com.example.lms.repository.CourseRepository;
import com.example.lms.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CourseEnrollmentService {
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    public CourseEnrollmentService(CourseEnrollmentRepository enrollmentRepository,
                                   CourseRepository courseRepository,
                                   UserRepository userRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CourseEnrollmentDto.EnrolledUserResponse> getEnrolledUsers(Long courseId) {
        CourseEntity course = getManagedCourse(courseId);
        List<CourseEnrollmentEntity> enrollments = enrollmentRepository.findByCourseIdOrderByEnrolledAtDesc(course.getId());
        Map<Long, User> users = userRepository.findAllById(enrollments.stream().map(CourseEnrollmentEntity::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return enrollments.stream().filter(e -> users.containsKey(e.getUserId()))
                .map(e -> toResponse(e, users.get(e.getUserId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<CourseEnrollmentDto.EligibleUserResponse> getEligibleUsers(Long courseId) {
        getManagedCourse(courseId);
        Set<Long> enrolledIds = enrollmentRepository.findByCourseIdOrderByEnrolledAtDesc(courseId).stream()
                .map(CourseEnrollmentEntity::getUserId).collect(Collectors.toSet());
        return userRepository.findAllByActiveTrueOrderByFirstNameAscLastNameAsc().stream()
                .filter(user -> isLearner(user) && !enrolledIds.contains(user.getId()))
                .map(user -> CourseEnrollmentDto.EligibleUserResponse.builder()
                        .userId(user.getId()).user(fullName(user)).email(user.getEmail()).build())
                .toList();
    }

    @Transactional
    public CourseEnrollmentDto.BulkOperationResponse enroll(Long courseId, List<Long> requestedUserIds) {
        getManagedCourse(courseId);
        List<Long> userIds = normalizeIds(requestedUserIds);
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<Long> invalid = userIds.stream().filter(id -> {
            User user = users.get(id);
            return user == null || !Boolean.TRUE.equals(user.getActive()) || !isLearner(user);
        }).toList();
        if (!invalid.isEmpty()) throw new ApiException("Only active learners can be enrolled. Invalid user IDs: " + invalid, HttpStatus.BAD_REQUEST);
        List<Long> duplicates = userIds.stream().filter(id -> enrollmentRepository.existsByCourseIdAndUserId(courseId, id)).toList();
        if (!duplicates.isEmpty()) throw new ApiException("Users are already enrolled in this course: " + duplicates, HttpStatus.CONFLICT);
        List<CourseEnrollmentEntity> records = userIds.stream().map(id -> CourseEnrollmentEntity.builder()
                .courseId(courseId).userId(id).build()).toList();
        enrollmentRepository.saveAllAndFlush(records);
        return result(courseId, userIds);
    }

    @Transactional
    public CourseEnrollmentDto.BulkOperationResponse unenroll(Long courseId, List<Long> requestedUserIds) {
        getManagedCourse(courseId);
        List<Long> userIds = normalizeIds(requestedUserIds);
        List<CourseEnrollmentEntity> records = enrollmentRepository.findByCourseIdAndUserIdIn(courseId, userIds);
        Set<Long> found = records.stream().map(CourseEnrollmentEntity::getUserId).collect(Collectors.toSet());
        List<Long> missing = userIds.stream().filter(id -> !found.contains(id)).toList();
        if (!missing.isEmpty()) throw new ApiException("Users are not enrolled in this course: " + missing, HttpStatus.NOT_FOUND);
        enrollmentRepository.deleteAllInBatch(records);
        return result(courseId, userIds);
    }

    private CourseEntity getManagedCourse(Long courseId) {
        CourseEntity course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ApiException("Course not found with id: " + courseId, HttpStatus.NOT_FOUND));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new ApiException("Authentication is required.", HttpStatus.UNAUTHORIZED);
        boolean instructor = auth.getAuthorities().stream().anyMatch(a -> "ROLE_INSTRUCTOR".equals(a.getAuthority()));
        if (instructor) {
            User current = userRepository.findByEmailIgnoreCase(auth.getName())
                    .orElseThrow(() -> new ApiException("Logged-in instructor profile not found.", HttpStatus.NOT_FOUND));
            if (!current.getId().equals(course.getInstructorId()))
                throw new ApiException("Instructors can only manage enrollments for courses assigned to themselves.", HttpStatus.FORBIDDEN);
        }
        return course;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) throw new ApiException("Please select at least one user", HttpStatus.BAD_REQUEST);
        if (ids.stream().anyMatch(Objects::isNull)) throw new ApiException("User ID is required.", HttpStatus.BAD_REQUEST);
        return ids.stream().distinct().toList();
    }

    private boolean isLearner(User user) {
        return user.getRole() != null && "LEARNER".equalsIgnoreCase(user.getRole().replace("ROLE_", "").trim());
    }

    private CourseEnrollmentDto.EnrolledUserResponse toResponse(CourseEnrollmentEntity e, User user) {
        int progress = Math.max(0, Math.min(100, Optional.ofNullable(e.getProgressPercent()).orElse(0)));
        String status = progress == 0 ? "Not Started" : progress >= 100 ? "Completed" : "In Progress";
        return CourseEnrollmentDto.EnrolledUserResponse.builder().enrollmentId(e.getId()).userId(user.getId())
                .user(fullName(user)).email(user.getEmail()).enrollmentDate(e.getEnrolledAt())
                .completionDate(e.getCompletedAt()).expirationDate(e.getExpiresAt()).progressPercent(progress)
                .status(status).scorePercent(e.getScorePercent())
                .completion(e.getCompletedAt() == null ? "Not completed" : "Completed").build();
    }

    private String fullName(User user) { return (user.getFirstName() + " " + user.getLastName()).trim(); }
    private CourseEnrollmentDto.BulkOperationResponse result(Long courseId, List<Long> userIds) {
        return CourseEnrollmentDto.BulkOperationResponse.builder().courseId(courseId).userIds(userIds)
                .affectedCount(userIds.size()).build();
    }
}
