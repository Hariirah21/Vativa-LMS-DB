package com.example.lms.repository;

import com.example.lms.entity.SystemFeatureEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemFeatureRepository extends JpaRepository<SystemFeatureEntity, String> {

    List<SystemFeatureEntity> findByActiveTrueOrderByCategoryIdAsc();
}