package com.example.lms.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.lms.entity.CourseEnrollmentEntity;
import com.example.lms.entity.CourseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@Transactional
class CourseEnrollmentRepositoryTest {

    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Test
    void persistsAllEnrolledCourseListFieldsPerUserAndCourse() {
        CourseEntity course = courseRepository.saveAndFlush(CourseEntity.builder()
                .name("Enrollment Persistence Test")
                .status("Draft")
                .build());

        CourseEnrollmentEntity enrollment = CourseEnrollmentEntity.builder()
                .courseId(course.getId())
                .userId(202L)
                .enrollmentDate(LocalDate.of(2026, 8, 1))
                .completionDate(LocalDate.of(2026, 8, 20))
                .expirationDate(LocalDate.of(2027, 8, 1))
                .progressPercentage(new BigDecimal("75.50"))
                .status("In Progress")
                .scorePercentage(new BigDecimal("82.25"))
                .completion(false)
                .build();

        courseEnrollmentRepository.saveAndFlush(enrollment);

        List<CourseEnrollmentEntity> results =
                courseEnrollmentRepository.findByCourseId(course.getId());
        CourseEnrollmentEntity persisted = results.get(0);

        assertEquals(202L, persisted.getUserId());
        assertEquals(LocalDate.of(2026, 8, 1), persisted.getEnrollmentDate());
        assertEquals(LocalDate.of(2026, 8, 20), persisted.getCompletionDate());
        assertEquals(LocalDate.of(2027, 8, 1), persisted.getExpirationDate());
        assertEquals(new BigDecimal("75.50"), persisted.getProgressPercentage());
        assertEquals("In Progress", persisted.getStatus());
        assertEquals(new BigDecimal("82.25"), persisted.getScorePercentage());
        assertFalse(persisted.getCompletion());
    }
}
