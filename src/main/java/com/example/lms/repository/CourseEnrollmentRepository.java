package com.example.lms.repository;

import com.example.lms.entity.CourseEnrollmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollmentEntity, Long> {
    List<CourseEnrollmentEntity> findByCourseIdOrderByEnrolledAtDesc(Long courseId);
    List<CourseEnrollmentEntity> findByCourseIdAndUserIdIn(Long courseId, Collection<Long> userIds);
    boolean existsByCourseIdAndUserId(Long courseId, Long userId);

    @Query("""
            SELECT CASE WHEN COUNT(enrollment) > 0 THEN true ELSE false END
            FROM CourseEnrollmentEntity enrollment
            WHERE enrollment.courseId = :courseId
              AND enrollment.userId = :userId
              AND (enrollment.expiresAt IS NULL OR enrollment.expiresAt > :now)
            """)
    boolean existsActiveAssignment(
            @Param("courseId") Long courseId,
            @Param("userId") Long userId,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select count(enrollment),
                   coalesce(sum(case when enrollment.progressPercent >= 100 then 1 else 0 end), 0)
            from CourseEnrollmentEntity enrollment
            """)
    List<Object[]> countTotalAndCompletedEnrollments();
}
