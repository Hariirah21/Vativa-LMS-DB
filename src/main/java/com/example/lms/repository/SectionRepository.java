package com.example.lms.repository;

import com.example.lms.entity.SectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SectionRepository extends JpaRepository<SectionEntity, Long> {

    List<SectionEntity> findByCourseIdOrderBySectionOrderAsc(Long courseId);

    Optional<SectionEntity> findByIdAndCourseId(Long id, Long courseId);

    boolean existsByCourseIdAndNameIgnoreCase(Long courseId, String name);

    boolean existsByCourseIdAndNameIgnoreCaseAndIdNot(Long courseId, String name, Long id);

    long countByCourseId(Long courseId);

    @Query("SELECT COALESCE(MAX(s.sectionOrder), 0) FROM SectionEntity s WHERE s.course.id = :courseId")
    Integer findMaxOrderByCourseId(@Param("courseId") Long courseId);
}
