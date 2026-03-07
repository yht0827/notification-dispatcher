package com.example.application.port.out.repository;

import com.example.domain.notification.GroupType;
import com.example.domain.notification.NotificationGroup;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationGroupRepository {

    NotificationGroup save(NotificationGroup group);

    Optional<NotificationGroup> findById(Long id);

    Optional<NotificationGroup> findByIdWithNotifications(Long id);

    Optional<NotificationGroup> findByClientIdAndIdempotencyKey(String clientId, String idempotencyKey);

    List<NotificationGroup> findByClientIdWithCursor(String clientId, LocalDateTime from, Long cursorId, int limit);

    List<NotificationGroup> findRecentByCursor(Long cursorId, int limit);

    List<NotificationGroup> findByGroupType(GroupType groupType);

    void delete(NotificationGroup group);
}
