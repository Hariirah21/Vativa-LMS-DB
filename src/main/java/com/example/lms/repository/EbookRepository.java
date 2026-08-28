package com.example.lms.repository;

import com.example.lms.entity.Ebook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EbookRepository extends JpaRepository<Ebook, Long> {

    List<Ebook> findAllByCourseId(Long courseId);

    Optional<Ebook> findByIdAndCourseId(Long id, Long courseId);
}