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
            select user
            from User user
            where user.active = true
              and lower(trim(replace(user.role, 'ROLE_', ''))) = 'learner'
              and (
                    :search is null
                    or lower(concat(concat(user.firstName, ' '), user.lastName))
                        like concat('%', lower(:search), '%') escape '\\'
                    or lower(user.email) like concat('%', lower(:search), '%') escape '\\'
              )
              and (
                    :instructorId is null
                    or exists (
                        select enrollment.id
                        from CourseEnrollmentEntity enrollment, CourseEntity course
                        where enrollment.userId = user.id
                          and course.id = enrollment.courseId
                          and course.instructorId = :instructorId
                    )
              )
              and (
                    :filter is null
                    or (
                        :filter = 'COMPLETED'
                        and exists (
                            select completed.id
                            from CourseEnrollmentEntity completed
                            where completed.userId = user.id
                              and completed.progressPercent >= 100
                              and (
                                    :instructorId is null
                                    or exists (
                                        select completedCourse.id
                                        from CourseEntity completedCourse
                                        where completedCourse.id = completed.courseId
                                          and completedCourse.instructorId = :instructorId
                                    )
                              )
                        )
                    )
                    or (
                        :filter = 'INCOMPLETED'
                        and exists (
                            select incomplete.id
                            from CourseEnrollmentEntity incomplete
                            where incomplete.userId = user.id
                              and incomplete.progressPercent < 100
                              and (
                                    :instructorId is null
                                    or exists (
                                        select incompleteCourse.id
                                        from CourseEntity incompleteCourse
                                        where incompleteCourse.id = incomplete.courseId
                                          and incompleteCourse.instructorId = :instructorId
                                    )
                              )
                        )
                    )
                    or (
                        :filter = 'ENROLLED'
                        and exists (
                            select enrolled.id
                            from CourseEnrollmentEntity enrolled
                            where enrolled.userId = user.id
                              and (
                                    :instructorId is null
                                    or exists (
                                        select enrolledCourse.id
                                        from CourseEntity enrolledCourse
                                        where enrolledCourse.id = enrolled.courseId
                                          and enrolledCourse.instructorId = :instructorId
                                    )
                              )
                        )
                    )
              )
            order by lower(user.firstName), lower(user.lastName), user.id
            """)
    List<User> findLearnersForReport(@Param("search") String search,
                                     @Param("filter") String filter,
                                     @Param("instructorId") Long instructorId);

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
