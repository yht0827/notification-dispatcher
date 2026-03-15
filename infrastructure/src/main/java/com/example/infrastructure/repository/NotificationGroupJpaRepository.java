package com.example.infrastructure.repository;

import com.example.domain.notification.GroupType;
import com.example.domain.notification.NotificationGroup;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationGroupJpaRepository extends JpaRepository<NotificationGroup, Long> {

    @Query("""
        select g from NotificationGroup g
        where g.clientId = :clientId
          and g.createdAt >= :from
          and (:cursorId is null or g.id < :cursorId)
          and (:completed is null
               or (true = :completed and g.totalCount = g.sentCount + g.failedCount)
               or (false = :completed and g.totalCount <> g.sentCount + g.failedCount))
        order by g.id desc
        """)
    List<NotificationGroup> findByClientIdWithCursor(
        @Param("clientId") String clientId,
        @Param("from") LocalDateTime from,
        @Param("cursorId") Long cursorId,
        @Param("completed") Boolean completed,
        Pageable pageable);

    @EntityGraph(attributePaths = "notifications")
    @Query("select g from NotificationGroup g where g.id = :id")
    Optional<NotificationGroup> findByIdWithNotifications(@Param("id") Long id);

    Optional<NotificationGroup> findByClientIdAndIdempotencyKey(String clientId, String idempotencyKey);

    List<NotificationGroup> findByGroupType(GroupType groupType);
}
