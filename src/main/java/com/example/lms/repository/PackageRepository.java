package com.example.lms.repository;

import com.example.lms.entity.PackageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PackageRepository extends JpaRepository<PackageEntity, Long> {
    boolean existsByNameIgnoreCase(String name);
    Optional<PackageEntity> findByNameIgnoreCase(String name);
}