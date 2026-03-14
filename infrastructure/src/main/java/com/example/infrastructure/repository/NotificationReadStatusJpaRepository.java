package com.example.infrastructure.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.domain.notification.NotificationReadStatus;

public interface NotificationReadStatusJpaRepository extends JpaRepository<NotificationReadStatus, Long> {

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(
		value = "INSERT IGNORE INTO notification_read_status (notification_id, read_at) VALUES (:notificationId, :readAt)",
		nativeQuery = true
	)
	int insertIgnore(@Param("notificationId") Long notificationId, @Param("readAt") java.time.LocalDateTime readAt);

	List<NotificationReadStatus> findAllByNotificationIdIn(List<Long> notificationIds);

	Optional<NotificationReadStatus> findByNotificationId(Long notificationId);
}
