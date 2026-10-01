package com.example.lms.repository;

import com.example.lms.entity.MultimediaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MultimediaRepository extends JpaRepository<MultimediaEntity, UUID> {

    List<MultimediaEntity> findByCourseIdOrderByCreatedAtDesc(Long courseId);

    List<MultimediaEntity> findByCourseIdAndPublishedTrueOrderByCreatedAtDesc(Long courseId);

    Optional<MultimediaEntity> findByClientRequestId(UUID clientRequestId);
}
