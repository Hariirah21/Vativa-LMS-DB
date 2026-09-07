package com.example.lms.repository;

import com.example.lms.entity.EbookEntity;
import com.example.lms.entity.EbookStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EbookRepository extends JpaRepository<EbookEntity, UUID> {
    Optional<EbookEntity> findByClientRequestId(UUID clientRequestId);
    List<EbookEntity> findByCourseIdOrderByUpdatedAtDesc(Long courseId);
    List<EbookEntity> findByCourseIdAndStatusOrderByUpdatedAtDesc(Long courseId, EbookStatus status);
}
