package com.example.lms.repository;

import com.example.lms.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    boolean existsByNameIgnoreCaseAndCreatedByAdminId(String name, Long createdByAdminId);

    Optional<RoleEntity> findByIdAndCreatedByAdminId(Long id, Long createdByAdminId);

    List<RoleEntity> findAllByCreatedByAdminId(Long createdByAdminId);

    // SRS: "Available Roles dropdown displays all active roles"
    List<RoleEntity> findAllByCreatedByAdminIdAndStatus(Long createdByAdminId, String status);

    boolean existsByIdAndCreatedByAdminId(Long id, Long createdByAdminId);
}