package com.example.lms.repository;

import com.example.lms.entity.CourseCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseCategoryRepository extends JpaRepository<CourseCategoryEntity, Long> {
    List<CourseCategoryEntity> findByActiveTrueOrderByNameAsc();
}
