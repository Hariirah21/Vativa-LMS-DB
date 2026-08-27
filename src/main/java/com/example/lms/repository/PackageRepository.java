package com.example.lms.repository;

import com.example.lms.entity.PackageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PackageRepository extends JpaRepository<PackageEntity, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<PackageEntity> findByNameIgnoreCase(String name);

    /**
     * Doc06 Edge Case #6: "prevent duplicate package names when creating OR
     * updating a package". A single indexed query is cheaper and race-safer
     * than fetch-then-filter-in-memory.
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    /**
     * Backs Doc06 Field #1 (Search by Package Name) and Field #2 (Filter by
     * Status: All / Active / Inactive). `status` is nullable to represent
     * the "All Packages" option. Paginated for performance as the catalog
     * grows (production-grade requirement).
     */
    @Query("""
            SELECT p FROM PackageEntity p
            WHERE (:status IS NULL OR p.status = :status)
              AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<PackageEntity> search(@Param("status") String status,
                                @Param("search") String search,
                                Pageable pageable);
}