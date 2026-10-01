package com.example.lms.repository;

import com.example.lms.entity.CourseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CourseRepository extends JpaRepository<CourseEntity, Long> {
    List<CourseEntity> findAllByOrderByUpdatedAtDesc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("""
            select course.id,
                   course.name,
                   course.courseCode,
                   category.name,
                   count(enrollment.id),
                   coalesce(sum(case
                       when enrollment.id is not null
                        and coalesce(enrollment.progressPercent, 0) >= 100 then 1
                       else 0
                   end), 0),
                   coalesce(sum(case
                       when enrollment.id is not null
                        and coalesce(enrollment.progressPercent, 0) > 0
                        and coalesce(enrollment.progressPercent, 0) < 100 then 1
                       else 0
                   end), 0),
                   coalesce(sum(case
                       when enrollment.id is not null
                        and coalesce(enrollment.progressPercent, 0) <= 0 then 1
                       else 0
                   end), 0),
                   avg(case
                       when enrollment.id is null then null
                       when coalesce(enrollment.progressPercent, 0) < 0 then 0
                       when coalesce(enrollment.progressPercent, 0) > 100 then 100
                       else coalesce(enrollment.progressPercent, 0)
                   end)
            from CourseEntity course
            join CourseCategoryEntity category on category.id = course.categoryId
            left join CourseEnrollmentEntity enrollment on enrollment.courseId = course.id
            where (:courseId is null or course.id = :courseId)
              and (:instructorId is null or course.instructorId = :instructorId)
              and (
                    :search is null
                    or lower(course.name) like concat('%', lower(:search), '%') escape '\\'
                    or lower(coalesce(course.courseCode, ''))
                        like concat('%', lower(:search), '%') escape '\\'
                    or lower(category.name) like concat('%', lower(:search), '%') escape '\\'
              )
              and (:categoryId is null or category.id = :categoryId)
              and (:category is null or lower(category.name) = lower(:category))
            group by course.id, course.name, course.courseCode, category.name
            order by lower(course.name), course.id
            """)
    List<Object[]> findCourseReportAggregates(@Param("courseId") Long courseId,
                                              @Param("instructorId") Long instructorId,
                                              @Param("search") String search,
                                              @Param("categoryId") Long categoryId,
                                              @Param("category") String category);
}
