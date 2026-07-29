package com.example.lms.repository;

import com.example.lms.entity.MultimediaResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MultimediaResourceRepository extends JpaRepository<MultimediaResource, UUID> {

    List<MultimediaResource> findByCourseIdOrderByCreatedAtDesc(Long courseId);

    List<MultimediaResource> findByCourseIdAndPublishedTrueOrderByCreatedAtDesc(Long courseId);

    Optional<MultimediaResource> findByClientRequestId(UUID clientRequestId);
}
