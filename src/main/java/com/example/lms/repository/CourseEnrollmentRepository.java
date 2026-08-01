package com.example.lms.repository;

import com.example.lms.entity.CourseEnrollmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollmentEntity, Long> {
    List<CourseEnrollmentEntity> findByCourseIdOrderByEnrolledAtDesc(Long courseId);
    List<CourseEnrollmentEntity> findByCourseIdAndUserIdIn(Long courseId, Collection<Long> userIds);
    boolean existsByCourseIdAndUserId(Long courseId, Long userId);
}
