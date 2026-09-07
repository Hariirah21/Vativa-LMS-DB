package com.example.lms.repository;

import com.example.lms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select user
            from User user
            where lower(trim(replace(user.role, 'ROLE_', ''))) = lower(:role)
              and user.active = true
            order by user.firstName asc, user.lastName asc
            """)
    List<User> findActiveUsersByRole(@Param("role") String role);

    List<User> findAllByActiveTrueOrderByFirstNameAscLastNameAsc();

    @Query("""
            select year(user.createdAt), month(user.createdAt), day(user.createdAt),
                   hour(user.createdAt), count(user)
            from User user
            where user.createdAt >= :start and user.createdAt < :end
            group by year(user.createdAt), month(user.createdAt), day(user.createdAt), hour(user.createdAt)
            order by year(user.createdAt), month(user.createdAt), day(user.createdAt), hour(user.createdAt)
            """)
    List<Object[]> countSignupsByHour(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    @Query("""
            select year(user.createdAt), month(user.createdAt), day(user.createdAt), count(user)
            from User user
            where user.createdAt >= :start and user.createdAt < :end
            group by year(user.createdAt), month(user.createdAt), day(user.createdAt)
            order by year(user.createdAt), month(user.createdAt), day(user.createdAt)
            """)
    List<Object[]> countSignupsByDay(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    @Query("""
            select year(user.createdAt), month(user.createdAt), count(user)
            from User user
            where user.createdAt >= :start and user.createdAt < :end
            group by year(user.createdAt), month(user.createdAt)
            order by year(user.createdAt), month(user.createdAt)
            """)
    List<Object[]> countSignupsByMonth(@Param("start") LocalDateTime start,
                                       @Param("end") LocalDateTime end);

    @Query("""
            select upper(trim(replace(user.role, 'ROLE_', ''))), count(user)
            from User user
            group by upper(trim(replace(user.role, 'ROLE_', '')))
            """)
    List<Object[]> countUsersByRole();

    @Query("""
            select count(user)
            from User user
            where user.active = true
              and lower(trim(replace(user.role, 'ROLE_', ''))) = lower(:role)
            """)
    long countActiveUsersByRole(@Param("role") String role);
}
