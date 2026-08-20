package com.example.lms.repository;

import com.example.lms.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleRepository extends JpaRepository<RoleEntity, Long> {

    // Roles are unique system-wide, not per-admin (SRS: "Role names must be
    // unique in the system").
    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    // SRS: "Available Roles dropdown displays all active roles"
    List<RoleEntity> findAllByStatus(String status);
}