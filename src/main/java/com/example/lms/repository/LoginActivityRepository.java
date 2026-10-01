package com.example.lms.repository;

import com.example.lms.entity.LoginActivityEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface LoginActivityRepository extends JpaRepository<LoginActivityEntity, Long> {

    @Query("""
            select year(activity.loggedInAt), month(activity.loggedInAt), day(activity.loggedInAt),
                   hour(activity.loggedInAt), count(activity)
            from LoginActivityEntity activity
            where activity.loggedInAt >= :start and activity.loggedInAt < :end
            group by year(activity.loggedInAt), month(activity.loggedInAt), day(activity.loggedInAt),
                     hour(activity.loggedInAt)
            order by year(activity.loggedInAt), month(activity.loggedInAt), day(activity.loggedInAt),
                     hour(activity.loggedInAt)
            """)
    List<Object[]> countLoginsByHour(@Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);

    @Query("""
            select year(activity.loggedInAt), month(activity.loggedInAt), day(activity.loggedInAt), count(activity)
            from LoginActivityEntity activity
            where activity.loggedInAt >= :start and activity.loggedInAt < :end
            group by year(activity.loggedInAt), month(activity.loggedInAt), day(activity.loggedInAt)
            order by year(activity.loggedInAt), month(activity.loggedInAt), day(activity.loggedInAt)
            """)
    List<Object[]> countLoginsByDay(@Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end);

    @Query("""
            select year(activity.loggedInAt), month(activity.loggedInAt), count(activity)
            from LoginActivityEntity activity
            where activity.loggedInAt >= :start and activity.loggedInAt < :end
            group by year(activity.loggedInAt), month(activity.loggedInAt)
            order by year(activity.loggedInAt), month(activity.loggedInAt)
            """)
    List<Object[]> countLoginsByMonth(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    @Query("""
            select count(distinct activity.userId)
            from LoginActivityEntity activity, User user
            where user.id = activity.userId
              and user.active = true
              and activity.expiresAt > :now
            """)
    long countOnlineUsers(@Param("now") LocalDateTime now);

    @Query("""
            select activity.userId, max(activity.loggedInAt)
            from LoginActivityEntity activity
            where activity.userId in :userIds
            group by activity.userId
            """)
    List<Object[]> findLastLoginsByUserIds(@Param("userIds") Collection<Long> userIds);
}
