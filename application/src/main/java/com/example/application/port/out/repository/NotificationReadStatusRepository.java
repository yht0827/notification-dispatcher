package com.example.application.port.out.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface NotificationReadStatusRepository {

	void markAsRead(Long notificationId, LocalDateTime readAt);

	int markAllAsRead(List<Long> notificationIds, LocalDateTime readAt);

	boolean existsByNotificationId(Long notificationId);

	LocalDateTime findReadAtByNotificationId(Long notificationId);

	Set<Long> findReadNotificationIds(List<Long> notificationIds);

	Map<Long, LocalDateTime> findReadAtByNotificationIds(List<Long> notificationIds);
}
