package com.example.lms.repository;

import com.example.lms.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


public interface UserRepository extends JpaRepository<User, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForQuestionBankCreation(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByIdAndRoleIgnoreCaseAndActiveTrue(Long id, String role);

    List<User> findByRoleIgnoreCaseAndActiveTrueOrderByFirstNameAscLastNameAsc(String role);

    boolean existsByEmailIgnoreCase(String email);
}
