package com.example.lms.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.lms.entity.CourseEnrollmentEntity;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollmentEntity, Long> {

    // Find all course IDs enrolled by a specific user
    @Query("SELECT e.courseId FROM CourseEnrollmentEntity e WHERE e.userId = :userId")
    List<Long> findEnrolledCourseIdsByUserId(@Param("userId") Long userId);

    // Check if a user is enrolled in a specific course
    @Query("SELECT COUNT(e) > 0 FROM CourseEnrollmentEntity e WHERE e.userId = :userId AND e.courseId = :courseId")
    boolean isUserEnrolledInCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);
}
