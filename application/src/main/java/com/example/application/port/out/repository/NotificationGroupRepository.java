package com.example.application.port.out.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.example.domain.notification.GroupType;
import com.example.domain.notification.NotificationGroup;

public interface NotificationGroupRepository {

	NotificationGroup save(NotificationGroup group);

	NotificationGroup saveAndFlush(NotificationGroup group);

	Optional<NotificationGroup> findById(Long id);

	Optional<NotificationGroup> findByIdWithNotifications(Long id);

	Optional<NotificationGroup> findByClientIdAndIdempotencyKey(String clientId, String idempotencyKey);

	List<NotificationGroup> findByClientIdWithCursor(String clientId, LocalDateTime from, Long cursorId, Boolean completed, int limit);

	List<NotificationGroup> findByGroupType(GroupType groupType);

	void bulkApplyDispatchCounts(List<NotificationGroupCountUpdate> updates);

}
