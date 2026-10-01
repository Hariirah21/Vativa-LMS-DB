package com.example.lms.repository;

import com.example.lms.entity.EbookMediaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EbookMediaRepository extends JpaRepository<EbookMediaEntity, UUID> {
}
