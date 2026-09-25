package com.example.lms.repository;

import com.example.lms.entity.CourseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseRepository extends JpaRepository<CourseEntity, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("SELECT c FROM CourseEntity c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :searchName, '%'))")
    List<CourseEntity> searchByName(@Param("searchName") String searchName);
}
