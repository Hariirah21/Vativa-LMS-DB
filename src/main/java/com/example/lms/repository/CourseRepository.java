package com.example.lms.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.lms.entity.CourseEntity;

public interface CourseRepository extends JpaRepository<CourseEntity, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @EntityGraph(attributePaths = "enrollments")
    @Query("SELECT c FROM CourseEntity c WHERE c.id = :courseId")
    Optional<CourseEntity> findByIdWithEnrollments(@Param("courseId") Long courseId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CourseEntity c WHERE c.id = :courseId")
    Optional<CourseEntity> findByIdForEnrollment(@Param("courseId") Long courseId);

    @Query("SELECT c FROM CourseEntity c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :searchName, '%'))")
    List<CourseEntity> searchByName(@Param("searchName") String searchName);

    @Query("SELECT c FROM CourseEntity c WHERE c.status = :status")
    List<CourseEntity> findByStatus(@Param("status") String status);

    @Query("SELECT c FROM CourseEntity c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :searchName, '%')) AND c.status = :status")
    List<CourseEntity> searchByNameAndStatus(@Param("searchName") String searchName, @Param("status") String status);
}
