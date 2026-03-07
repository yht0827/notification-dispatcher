package com.example.application.port.out.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface NotificationReadStatusRepository {

	void markAsRead(Long notificationId, LocalDateTime readAt);

	boolean existsByNotificationId(Long notificationId);

	Set<Long> findReadNotificationIds(List<Long> notificationIds);
}
