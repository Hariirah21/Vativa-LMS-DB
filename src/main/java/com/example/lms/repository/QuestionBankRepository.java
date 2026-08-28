package com.example.lms.repository;

import com.example.lms.entity.QuestionBankEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuestionBankRepository extends JpaRepository<QuestionBankEntity, Long> {

    @EntityGraph(attributePaths = {"course", "createdBy"})
    @Query("""
            SELECT qb FROM QuestionBankEntity qb
            WHERE (:search IS NULL OR LOWER(qb.name) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<QuestionBankEntity> searchAll(@Param("search") String search, Pageable pageable);

    @EntityGraph(attributePaths = {"course", "createdBy"})
    @Query("""
            SELECT qb FROM QuestionBankEntity qb
            WHERE qb.createdBy.id = :creatorId
              AND (:search IS NULL OR LOWER(qb.name) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<QuestionBankEntity> searchByCreator(
            @Param("creatorId") Long creatorId,
            @Param("search") String search,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"course", "createdBy"})
    @Query("SELECT qb FROM QuestionBankEntity qb WHERE qb.id = :id")
    Optional<QuestionBankEntity> findWithReferencesById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"course", "createdBy"})
    Optional<QuestionBankEntity> findByCreatedByIdAndIdempotencyKey(
            Long createdById,
            String idempotencyKey
    );
}
